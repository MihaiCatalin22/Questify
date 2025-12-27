package com.questify.gdpr.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.questify.gdpr.model.UserExportJob;
import com.questify.gdpr.model.UserExportJobPart;
import com.questify.gdpr.repository.UserExportJobPartRepository;
import com.questify.gdpr.repository.UserExportJobRepository;
import com.questify.gdpr.storage.ExportStorageService;
import com.questify.kafka.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserExportService {

    private final UserExportJobRepository jobRepo;
    private final UserExportJobPartRepository partRepo;
    private final ExportStorageService storage;
    private final ObjectMapper mapper;
    private final EventPublisher events;

    @Value("${app.kafka.topics.users}")
    private String usersTopic;

    @Value("${app.kafka.topics.audit:}")
    private String auditTopic;

    @Value("${app.keycloak.gdpr.export.zipTtlHours:24}")
    private long zipTtlHours;

    @Value("${app.keycloak.gdpr.export.presignTtlSeconds:900}")
    private long presignTtlSeconds;

    private static final Set<String> EXPECTED = Set.of(
            "user-service",
            "quest-service",
            "submission-service",
            "proof-service"
    );

    @Transactional
    public UserExportJob createJob(String userId) {
        Instant now = Instant.now();

        UserExportJob job = UserExportJob.builder()
                .userId(userId)
                .status(UserExportJob.Status.RUNNING)
                .createdAt(now)
                .expiresAt(now.plus(Duration.ofHours(zipTtlHours)))
                .build();
        jobRepo.save(job);

        for (String svc : EXPECTED) {
            partRepo.save(UserExportJobPart.builder()
                    .job(job)
                    .service(svc)
                    .received(false)
                    .build());
        }

        Map<String, Object> payload = Map.of(
                "jobId", job.getId(),
                "userId", userId
        );

        events.publish(
                usersTopic,
                userId,
                "UserExportRequested",
                1,
                "user-service",
                payload
        );

        log.info("Export job {} created for user {} (event queued via outbox).", job.getId(), userId);
        return job;
    }

    @Transactional
    public void receivePart(String jobId, String service, Map<String, Object> payload) throws Exception {
        if (!EXPECTED.contains(service)) {
            log.warn("Ignoring export part for unknown service '{}', jobId={}", service, jobId);
            return;
        }

        UserExportJob job = jobRepo.findById(jobId).orElseThrow();

        var part = partRepo.findByJob_IdAndService(jobId, service)
                .orElseThrow(() -> new IllegalStateException("Missing part row for " + service));

        if (part.isReceived()) {
            log.info("Export part already received: jobId={} service={}", jobId, service);
            return;
        }

        byte[] json = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(payload);
        String key = "exports/" + job.getUserId() + "/" + job.getId() + "/parts/" + service + ".json";
        storage.putBytes(key, json, "application/json");

        part.setReceived(true);
        part.setReceivedAt(Instant.now());
        partRepo.save(part);

        assembleIfComplete(jobId);
    }

    private void assembleIfComplete(String jobId) throws Exception {
        UserExportJob job = jobRepo.findById(jobId).orElseThrow();
        if (job.getStatus() == UserExportJob.Status.COMPLETED || job.getStatus() == UserExportJob.Status.EXPIRED) return;

        List<UserExportJobPart> parts = partRepo.findByJob_Id(jobId);

        Set<String> received = new HashSet<>();
        for (UserExportJobPart p : parts) {
            if (p.isReceived()) received.add(p.getService());
        }

        if (!received.containsAll(EXPECTED)) {
            log.info("Export job {} waiting. Received: {}", jobId, received);
            return;
        }

        assembleZip(job, parts);
    }

    private void assembleZip(UserExportJob job, List<UserExportJobPart> parts) throws Exception {
        if (job.getStatus() == UserExportJob.Status.COMPLETED) return;

        String base = "exports/" + job.getUserId() + "/" + job.getId() + "/parts/";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (UserExportJobPart part : parts) {
                String svc = part.getService();
                String key = base + svc + ".json";
                byte[] bytes = storage.getBytes(key);

                zip.putNextEntry(new ZipEntry(svc + ".json"));
                zip.write(bytes);
                zip.closeEntry();
            }

            Map<String, Object> manifest = Map.of(
                    "jobId", job.getId(),
                    "userId", job.getUserId(),
                    "createdAt", job.getCreatedAt(),
                    "generatedAt", Instant.now(),
                    "parts", parts.stream().map(p -> Map.of(
                            "service", p.getService(),
                            "received", p.isReceived(),
                            "receivedAt", p.getReceivedAt()
                    )).toList()
            );

            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
            zip.closeEntry();
        }

        String zipKey = "exports/" + job.getUserId() + "/" + job.getId() + "/questify-export.zip";
        storage.putBytes(zipKey, out.toByteArray(), "application/zip");

        job.setZipObjectKey(zipKey);
        job.setStatus(UserExportJob.Status.COMPLETED);
        jobRepo.save(job);

        if (auditTopic != null && !auditTopic.isBlank()) {
            events.publish(
                    auditTopic,
                    job.getUserId(),
                    "UserExportCompleted",
                    1,
                    "user-service",
                    Map.of("jobId", job.getId(), "userId", job.getUserId())
            );
        }

        log.info("Export job {} COMPLETED. zipKey={}", job.getId(), zipKey);
    }

    public String presignedDownloadUrl(UserExportJob job) {
        return storage.presignGet(job.getZipObjectKey(), presignTtlSeconds);
    }

    @Scheduled(fixedDelayString = "${app.keycloak.gdpr.export.cleanupMs:3600000}")
    public void cleanupExpired() {
        Instant now = Instant.now();
        for (UserExportJob job : jobRepo.findAll()) {
            if (job.getExpiresAt() != null && job.getExpiresAt().isBefore(now)
                    && job.getStatus() != UserExportJob.Status.EXPIRED) {
                try {
                    if (job.getZipObjectKey() != null) storage.deleteObject(job.getZipObjectKey());
                } catch (Exception ignored) {}
                job.setStatus(UserExportJob.Status.EXPIRED);
                jobRepo.save(job);
            }
        }
    }
}

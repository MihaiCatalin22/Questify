package com.questify.gdpr.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.questify.gdpr.model.UserExportJob;
import com.questify.gdpr.model.UserExportJobPart;
import com.questify.gdpr.storage.ExportStorageService;
import com.questify.kafka.EventPublisher;
import com.questify.gdpr.repository.UserExportJobPartRepository;
import com.questify.gdpr.repository.UserExportJobRepository;
import com.questify.service.UserProfileService;
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

    private static final List<String> EXPECTED_PARTS =
            List.of("user-service", "quest-service", "submission-service", "proof-service");

    private final UserExportJobRepository jobRepo;
    private final UserExportJobPartRepository partRepo;
    private final ExportStorageService storage;
    private final ObjectMapper mapper;
    private final EventPublisher events;
    private final UserProfileService profiles;

    @Value("${app.kafka.topics.users}")
    private String usersTopic;

    @Value("${app.kafka.topics.audit}")
    private String auditTopic;

    @Value("${app.gdpr.export.zipTtlHours:24}")
    private long zipTtlHours;

    @Value("${app.gdpr.export.presignTtlSeconds:900}")
    private long presignTtlSeconds;

    @Transactional
    public UserExportJob createJob(String userId) {
        var now = Instant.now();
        var job = UserExportJob.builder()
                .userId(userId)
                .status(UserExportJob.Status.RUNNING)
                .createdAt(now)
                .expiresAt(now.plus(Duration.ofHours(zipTtlHours)))
                .build();

        jobRepo.save(job);

        for (String svc : EXPECTED_PARTS) {
            partRepo.save(UserExportJobPart.builder()
                    .jobId(job.getId())
                    .service(svc)
                    .received(false)
                    .build());
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("schemaVersion", "2.0");
            payload.put("generatedAt", now.toString());
            payload.put("profile", profiles.exportMe(userId));
            receivePart(job.getId(), "user-service", payload);
        } catch (Exception e) {
            log.warn("Failed to write user-service export part. jobId={} err={}", job.getId(), e.toString());
        }

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("jobId", job.getId());
        req.put("userId", userId);

        events.publish(usersTopic, userId, "UserExportRequested", 1, "user-service", req);
        events.publish(auditTopic, userId, "UserExportJobCreated", 1, "user-service", Map.of("jobId", job.getId()));

        return job;
    }

    @Transactional
    public void receivePart(String jobId, String service, Map<String, Object> payload) throws Exception {
        UserExportJob job = jobRepo.findById(jobId).orElseThrow();

        byte[] json = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(payload);

        String key = partObjectKey(job.getUserId(), job.getId(), service);
        storage.putBytes(key, json, "application/json");

        UserExportJobPart part = partRepo.findByJobIdAndService(jobId, service)
                .orElseThrow(() -> new IllegalStateException("Unknown part service=" + service + " for jobId=" + jobId));
        part.setReceived(true);
        part.setReceivedAt(Instant.now());
        partRepo.save(part);

        tryAssemble(job);
    }

    @Transactional
    public void tryAssemble(UserExportJob job) throws Exception {
        long receivedCount = partRepo.countByJobIdAndReceivedTrue(job.getId());
        if (receivedCount < EXPECTED_PARTS.size()) return;

        assembleZip(job);
    }

    private void assembleZip(UserExportJob job) throws Exception {
        if (job.getStatus() == UserExportJob.Status.COMPLETED) return;

        List<UserExportJobPart> parts = partRepo.findByJobId(job.getId());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (UserExportJobPart part : parts) {
                if (!part.isReceived()) continue;

                String partKey = partObjectKey(job.getUserId(), job.getId(), part.getService());
                byte[] data = storage.getBytes(partKey);

                ZipEntry entry = new ZipEntry(part.getService() + ".json");
                zip.putNextEntry(entry);
                zip.write(data);
                zip.closeEntry();
            }
        }

        String zipKey = zipObjectKey(job.getUserId(), job.getId());
        storage.putBytes(zipKey, out.toByteArray(), "application/zip");

        job.setZipObjectKey(zipKey);
        job.setStatus(UserExportJob.Status.COMPLETED);
        jobRepo.save(job);

        events.publish(auditTopic, job.getUserId(), "UserExportCompleted", 1, "user-service",
                Map.of("jobId", job.getId()));
    }

    public String presignedDownloadUrl(UserExportJob job) {
        if (job.getZipObjectKey() == null || job.getZipObjectKey().isBlank()) {
            throw new IllegalStateException("Export ZIP not ready");
        }
        return storage.presignGet(job.getZipObjectKey(), presignTtlSeconds);
    }

    @Scheduled(fixedDelayString = "${app.gdpr.export.cleanupMs:3600000}")
    @Transactional
    public void cleanupExpired() {
        Instant now = Instant.now();
        List<UserExportJob> expired = jobRepo.findByStatusInAndExpiresAtBefore(
                List.of(UserExportJob.Status.COMPLETED, UserExportJob.Status.RUNNING, UserExportJob.Status.PENDING),
                now
        );

        for (UserExportJob job : expired) {
            try {
                if (job.getZipObjectKey() != null) {
                    storage.deleteObject(job.getZipObjectKey());
                }
            } catch (Exception e) {
                log.warn("Failed to delete export zip. jobId={} key={} err={}",
                        job.getId(), job.getZipObjectKey(), e.toString());
            }
            job.setStatus(UserExportJob.Status.EXPIRED);
            jobRepo.save(job);
        }
    }

    private static String partObjectKey(String userId, String jobId, String service) {
        return "exports/" + userId + "/" + jobId + "/parts/" + service + ".json";
    }

    private static String zipObjectKey(String userId, String jobId) {
        return "exports/" + userId + "/" + jobId + "/questify-export.zip";
    }
}

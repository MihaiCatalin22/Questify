package com.questify.gdpr.controller;

import com.questify.gdpr.model.UserExportJob;
import com.questify.gdpr.repository.UserExportJobPartRepository;
import com.questify.gdpr.repository.UserExportJobRepository;
import com.questify.gdpr.service.UserExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/users/me/export-jobs")
@RequiredArgsConstructor
public class UserExportJobsController {

    private final UserExportService exports;
    private final UserExportJobRepository jobs;
    private final UserExportJobPartRepository parts;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> create(Authentication auth) {
        var job = exports.createJob(auth.getName());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobId", job.getId());
        body.put("status", job.getStatus().name());
        body.put("expiresAt", job.getExpiresAt());

        return ResponseEntity.accepted().body(body);
    }

    @GetMapping("/{jobId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> status(@PathVariable String jobId, Authentication auth) {
        var job = jobs.findById(jobId).orElse(null);
        if (job == null) return ResponseEntity.notFound().build();
        if (!job.getUserId().equals(auth.getName())) return ResponseEntity.status(403).build();

        Instant now = Instant.now();
        if (job.getExpiresAt() != null && job.getExpiresAt().isBefore(now)
                && job.getStatus() != UserExportJob.Status.EXPIRED) {

            if (job.getStatus() != UserExportJob.Status.FAILED) {
                job.setStatus(UserExportJob.Status.EXPIRED);
                jobs.save(job);
            }
        }

        var jobParts = parts.findByJob_Id(jobId);
        var missingParts = jobParts.stream()
                .filter(p -> !p.isReceived())
                .map(p -> p.getService())
                .sorted()
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobId", job.getId());
        body.put("status", job.getStatus().name());
        body.put("createdAt", job.getCreatedAt());
        body.put("expiresAt", job.getExpiresAt());
        body.put("lastProgressAt", job.getLastProgressAt());
        body.put("failureReason", job.getFailureReason());
        body.put("missingParts", missingParts);

        return ResponseEntity.ok(body);
    }

    @GetMapping("/{jobId}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> download(@PathVariable String jobId, Authentication auth) {
        var job = jobs.findById(jobId).orElse(null);
        if (job == null) return ResponseEntity.notFound().build();
        if (!job.getUserId().equals(auth.getName())) return ResponseEntity.status(403).build();

        Instant now = Instant.now();
        if (job.getExpiresAt() != null && job.getExpiresAt().isBefore(now)) {
            return ResponseEntity.status(410).body(Map.of("error", "Export expired"));
        }

        if (job.getStatus() == UserExportJob.Status.FAILED) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "Export failed");
            body.put("reason", job.getFailureReason());
            return ResponseEntity.status(409).body(body);
        }

        if (job.getZipObjectKey() == null) {
            return ResponseEntity.status(409).body(Map.of("error", "Export not ready"));
        }

        String url = exports.presignedDownloadUrl(job);
        return ResponseEntity.ok(Map.of("url", url));
    }
}

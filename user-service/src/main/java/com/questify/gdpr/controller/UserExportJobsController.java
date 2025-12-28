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
        return ResponseEntity.accepted().body(Map.of(
                "jobId", job.getId(),
                "status", job.getStatus().name(),
                "expiresAt", job.getExpiresAt()
        ));
    }

    @GetMapping("/{jobId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> status(@PathVariable String jobId, Authentication auth) {
        var job = jobs.findById(jobId).orElse(null);
        if (job == null) return ResponseEntity.notFound().build();
        if (!job.getUserId().equals(auth.getName())) return ResponseEntity.status(403).build();

        var jobParts = parts.findByJob_Id(jobId);
        var missingParts = jobParts.stream()
                .filter(p -> !p.isReceived())
                .map(p -> p.getService())
                .sorted()
                .toList();

        return ResponseEntity.ok(Map.of(
                "jobId", job.getId(),
                "status", job.getStatus().name(),
                "createdAt", job.getCreatedAt(),
                "expiresAt", job.getExpiresAt(),
                "lastProgressAt", job.getLastProgressAt(),
                "failureReason", job.getFailureReason(),
                "missingParts", missingParts
        ));
    }

    @GetMapping("/{jobId}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> download(@PathVariable String jobId, Authentication auth) {
        var job = jobs.findById(jobId).orElse(null);
        if (job == null) return ResponseEntity.notFound().build();
        if (!job.getUserId().equals(auth.getName())) return ResponseEntity.status(403).build();

        if (job.getStatus() == UserExportJob.Status.EXPIRED) {
            return ResponseEntity.status(410).body(Map.of("error", "Export expired"));
        }

        if (job.getStatus() == UserExportJob.Status.FAILED) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", "Export failed",
                    "reason", job.getFailureReason()
            ));
        }

        if (job.getZipObjectKey() == null) {
            return ResponseEntity.status(409).body(Map.of("error", "Export not ready"));
        }

        String url = exports.presignedDownloadUrl(job);
        return ResponseEntity.ok(Map.of("url", url));
    }
}

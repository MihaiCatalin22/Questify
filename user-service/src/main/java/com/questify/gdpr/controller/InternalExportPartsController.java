package com.questify.gdpr.controller;

import com.questify.gdpr.service.UserExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/internal/export-jobs")
@RequiredArgsConstructor
public class InternalExportPartsController {

    private final UserExportService exports;

    @PostMapping(
            path = "/{jobId}/parts/{service}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> uploadPart(
            @PathVariable String jobId,
            @PathVariable String service,
            @RequestBody Map<String, Object> payload
    ) throws Exception {
        exports.receivePart(jobId, service, payload);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}

package com.questify.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import com.questify.config.security.RateLimitService;

@RestController
@RequestMapping("/uploads")
@RequiredArgsConstructor
public class UploadController {
    private final S3Presigner presigner;
    private final String questifyBucket;
    private final String publicBaseUrl;
    private final RateLimitService rateLimit;

    public record PresignRequest(String questId, String fileName, String contentType) {}
    public record PresignResponse(String url, String objectKey, Map<String, String> headers, String publicUrl) {}

    @PostMapping(value = "/presign", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public PresignResponse presign(@RequestBody PresignRequest body, HttpServletRequest req) {
        if (body.questId() == null || body.questId().isBlank()) throw new IllegalArgumentException("questId required");
        if (body.contentType() == null || !body.contentType().matches("^(image|video)/.+"))
            throw new IllegalArgumentException("Only image/* or video/* allowed");

        String ip = req.getHeader("x-forwarded-for");
        if (ip == null || ip.isBlank()) ip = req.getRemoteAddr();
        rateLimit.checkOrThrow("rl:presign:" + ip, 20, java.time.Duration.ofSeconds(60));

        String ext = (body.fileName() != null && body.fileName().contains("."))
                ? body.fileName().substring(body.fileName().lastIndexOf('.') + 1) : null;

        String key = "quests/%s/%d-%s%s".formatted(
                body.questId(), Instant.now().toEpochMilli(), UUID.randomUUID(), ext != null ? "." + ext : "");

        var putReq = PutObjectRequest.builder()
                .bucket(questifyBucket)
                .key(key)
                .contentType(body.contentType())
                .acl(ObjectCannedACL.PUBLIC_READ) // DEV ONLY
                .build();

        var presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .putObjectRequest(putReq)
                .signatureDuration(Duration.ofMinutes(10))
                .build());

        Map<String, String> flatHeaders = presigned.signedHeaders().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> String.join(", ", e.getValue())
                ));

        String publicUrl = (publicBaseUrl.endsWith("/") ? publicBaseUrl : publicBaseUrl + "/") + key;

        return new PresignResponse(presigned.url().toString(), key, flatHeaders, publicUrl);
    }

    @GetMapping("/sign-get")
    public Map<String, String> signGet(
            @RequestParam String objectKey,
            @RequestParam(defaultValue = "300") long expiresSeconds
    ) {
        var getReq = GetObjectRequest.builder()
                .bucket(questifyBucket)
                .key(objectKey)
                .build();

        var presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(java.time.Duration.ofSeconds(expiresSeconds))
                .getObjectRequest(getReq)
                .build());

        return Map.of("url", presigned.url().toString());
    }
}

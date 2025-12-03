package com.questify.controller;

import com.questify.config.StorageProperties;
import com.questify.service.ProofStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/presign")
@RequiredArgsConstructor
public class InternalPresignController {

    private final ProofStorageService storage;
    private final StorageProperties props;

    @GetMapping(value = "/get", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> signGet(
            @RequestParam("key") String key,
            @RequestParam(value = "expires", required = false) Long expires
    ) {
        long ttl = (expires != null ? expires : props.getGetExpirySeconds());
        String url = storage.presignGet(key, ttl);
        return Map.of("url", url, "expiresInSeconds", ttl);
    }

    @PostMapping(value = "/put",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> signPut(@RequestBody Map<String, Object> body) {
        String contentType = String.valueOf(body.getOrDefault("contentType", "application/octet-stream"));
        long ttl = ((Number) body.getOrDefault("expires", props.getPutExpirySeconds())).longValue();

        String key = "proofs/internal/%s".formatted(UUID.randomUUID());

        String url = storage.presignPut(key, contentType, ttl);
        return Map.of("url", url, "key", key, "expiresInSeconds", ttl);
    }
}

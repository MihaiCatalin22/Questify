package com.questify.controller;

import com.questify.service.ProofStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/internal/objects")
@RequiredArgsConstructor
public class InternalObjectController {

    private final ProofStorageService storage;
    @Value("${internal.token}") private String internalToken;

    @GetMapping("/content")
    public ResponseEntity<byte[]> content(@RequestHeader("X-Internal-Token") String token,
                                          @RequestParam String key) {
        if (!internalToken.equals(token)) return ResponseEntity.status(403).build();
        byte[] body = storage.getBytes(key);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, inferContentType(key))
                .body(body);
    }

    @DeleteMapping
    public ResponseEntity<?> delete(@RequestHeader("X-Internal-Token") String token,
                                    @RequestParam String key) throws Exception {
        if (!internalToken.equals(token)) return ResponseEntity.status(403).build();
        storage.delete(key);
        return ResponseEntity.ok(Map.of("deleted", key));
    }

    private static String inferContentType(String key) {
        String lower = Optional.ofNullable(key).orElse("").toLowerCase();
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG_VALUE;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG_VALUE;
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF_VALUE;
        if (lower.endsWith(".bmp")) return "image/bmp";
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}

package com.questify.controller;

import com.questify.config.StorageProperties;
import com.questify.service.ProofStorageService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.FORBIDDEN;

@RestController
@RequestMapping("/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final ProofStorageService storage;
    private final StorageProperties props;

    @PostMapping
    public ResponseEntity<Map<String, String>> signPut(@RequestParam @NotBlank String contentType,
                                                       @RequestParam(required = false) Long expires,
                                                       Authentication auth) {
        var userId = auth != null ? auth.getName() : "anonymous";
        var key = "proofs/%s/%s".formatted(userId, UUID.randomUUID());
        var ttl = expires != null ? expires : props.getPutExpirySeconds();
        var url = storage.presignPut(key, contentType, ttl);
        return ResponseEntity.ok(Map.of(
                "key", key,
                "putUrl", url
        ));
    }

    @PostMapping(path = "/direct", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadDirect(@RequestPart("file") MultipartFile file,
                                                            Authentication auth) {
        var userId = auth != null ? auth.getName() : "anonymous";
        var key = "proofs/%s/%s".formatted(userId, UUID.randomUUID());
        try {
            storage.put(key, file.getInputStream(), file.getSize(), file.getContentType());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Direct upload failed: " + e.getMessage()));
        }

        String publicBase = props.getPublicBaseUrl();
        String url = null;
        if (publicBase != null && !publicBase.isBlank()) {
            String base = publicBase.endsWith("/") ? publicBase.substring(0, publicBase.length() - 1) : publicBase;
            String enc = URLEncoder.encode(key, StandardCharsets.UTF_8).replace("+", "%20");
            url = base + "/" + enc;
        }
        return ResponseEntity.ok(url != null ? Map.of("key", key, "url", url)
                : Map.of("key", key));
    }

    @GetMapping("/sign-get")
    public ResponseEntity<Map<String, String>> signGet(@RequestParam @NotBlank String key,
                                                       @RequestParam(required = false) Long expires,
                                                       Authentication auth) {
        enforceOwnership(key, auth);

        var ttl = expires != null ? expires : props.getGetExpirySeconds();
        var url = storage.presignGet(key, ttl);
        return ResponseEntity.ok(Map.of("getUrl", url));
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@RequestParam @NotBlank String key, Authentication auth) {
        enforceOwnership(key, auth);

        storage.delete(key);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/presign")
    public ResponseEntity<Map<String, String>> presignCompat(
            @RequestParam @NotBlank String contentType,
            @RequestParam(required = false) Long expires,
            Authentication auth) {
        return signPut(contentType, expires, auth);
    }

    private void enforceOwnership(String key, Authentication auth) {
        if (auth == null) {
            throw new ResponseStatusException(FORBIDDEN, "Not authenticated");
        }

        boolean elevated = auth.getAuthorities().stream().anyMatch(a ->
                "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_REVIEWER".equals(a.getAuthority())
        );
        if (elevated) return;

        String userId = auth.getName();
        if (key == null || !key.startsWith("proofs/" + userId + "/")) {
            throw new ResponseStatusException(FORBIDDEN, "You do not own this proof key");
        }
    }
}

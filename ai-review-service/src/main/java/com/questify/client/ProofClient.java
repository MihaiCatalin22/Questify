package com.questify.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class ProofClient {
    private final WebClient proofHttp;
    private final WebClient rawHttp;
    private final String internalToken;

    public ProofClient(@Value("${proof.service.base:http://proof-service}") String proofBase,
                       @Value("${internal.token:${INTERNAL_TOKEN:dev-internal-token}}") String internalToken) {
        this.proofHttp = WebClient.builder().baseUrl(proofBase).build();
        this.rawHttp = WebClient.builder().build();
        this.internalToken = internalToken;
    }

    public List<ProofObject> getProofs(Long submissionId) {
        return List.of();
    }

    public List<ProofObject> getProofsFromKeys(List<String> proofKeys) {
        List<ProofObject> out = new ArrayList<>();
        for (String key : proofKeys) {
            if (key == null || key.isBlank()) continue;
            out.add(fetchProof(key));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private ProofObject fetchProof(String key) {
        Map<String, Object> res = proofHttp.get()
                .uri(uri -> uri.path("/internal/presign/get").queryParam("key", key).queryParam("expires", 300).build())
                .header("X-Internal-Token", internalToken)
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(5));
        String url = res == null ? null : String.valueOf(res.get("url"));
        if (url == null || url.isBlank()) return new ProofObject(key, "application/octet-stream", null);

        byte[] bytes = rawHttp.get()
                .uri(url)
                .accept(MediaType.ALL)
                .retrieve()
                .bodyToMono(byte[].class)
                .block(Duration.ofSeconds(15));
        String contentType = inferContentType(key, bytes);
        String base64 = bytes == null ? null : Base64.getEncoder().encodeToString(bytes);
        return new ProofObject(key, contentType, base64);
    }

    private static String inferContentType(String key, byte[] bytes) {
        String lower = key == null ? "" : key.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (bytes != null && bytes.length > 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8) return "image/jpeg";
        if (bytes != null && bytes.length > 4 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50) return "image/png";
        return "application/octet-stream";
    }

    public record ProofObject(String key, String contentType, String base64) {}
}

package com.questify.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
public class ProofClient {
    private final WebClient proofHttp;
    private final WebClient rawHttp;
    private final String internalToken;

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    public ProofClient(@Value("${proof.service.base:http://proof-service}") String proofBase,
                       @Value("${internal.token:}") String internalDotToken,
                       @Value("${SECURITY_INTERNAL_TOKEN:}") String securityInternalToken,
                       @Value("${INTERNAL_TOKEN:dev-internal-token}") String internalToken,
                       @Value("${ai.review.proof.max-in-memory-bytes:10485760}") int maxInMemoryBytes) {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(maxInMemoryBytes))
                .build();
        this.proofHttp = WebClient.builder().baseUrl(proofBase).exchangeStrategies(strategies).build();
        this.rawHttp = WebClient.builder().exchangeStrategies(strategies).build();
        this.internalToken = firstNonBlank(internalDotToken, securityInternalToken, internalToken);
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
        try {
            return fetchInternalContent(key);
        } catch (NotFound notFound) {
            log.warn("Proof content endpoint missing for key={} (status={}), falling back to presigned download",
                    key, notFound.getStatusCode().value());
            return fetchViaPresignedUrl(key);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().is4xxClientError() || e.getStatusCode().is5xxServerError()) {
                throw new IllegalStateException("proof-service /internal/objects/content failed for key=" + key
                        + " status=" + e.getStatusCode().value()
                        + " body=" + truncate(e.getResponseBodyAsString(), 300), e);
            }
            throw e;
        }
    }

    private ProofObject fetchInternalContent(String key) {
        return proofHttp.get()
                .uri(uri -> uri.path("/internal/objects/content").queryParam("key", key).build())
                .header("X-Internal-Token", internalToken)
                .header("X-Security-Internal-Token", internalToken)
                .accept(MediaType.ALL)
                .retrieve()
                .toEntity(byte[].class)
                .map(entity -> {
                    byte[] bytes = entity.getBody();
                    String headerType = entity.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
                    String contentType = inferContentType(key, bytes, headerType);
                    String base64 = bytes == null ? null : Base64.getEncoder().encodeToString(bytes);
                    log.info("Fetched proof content via internal endpoint key={} bytes={} contentType={}",
                            key, bytes == null ? 0 : bytes.length, contentType);
                    return new ProofObject(key, contentType, base64);
                })
                .block(Duration.ofSeconds(20));
    }

    @SuppressWarnings("unchecked")
    private ProofObject fetchViaPresignedUrl(String key) {
        Map<String, Object> res = proofHttp.get()
                .uri(uri -> uri.path("/internal/presign/get").queryParam("key", key).queryParam("expires", 300).build())
                .header("X-Internal-Token", internalToken)
                .header("X-Security-Internal-Token", internalToken)
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(5));
        String url = res == null ? null : String.valueOf(res.get("url"));
        if (url == null || url.isBlank()) {
            log.warn("Proof presign returned empty URL for key={}", key);
            return new ProofObject(key, "application/octet-stream", null);
        }

        byte[] bytes = rawHttp.get()
                .uri(url)
                .accept(MediaType.ALL)
                .retrieve()
                .bodyToMono(byte[].class)
                .block(Duration.ofSeconds(15));
        String contentType = inferContentType(key, bytes, null);
        String base64 = bytes == null ? null : Base64.getEncoder().encodeToString(bytes);
        log.info("Fetched proof content via presigned URL key={} bytes={} contentType={}",
                key, bytes == null ? 0 : bytes.length, contentType);
        return new ProofObject(key, contentType, base64);
    }

    private static String inferContentType(String key, byte[] bytes, String headerValue) {
        String header = headerValue == null ? "" : headerValue.toLowerCase(Locale.ROOT).trim();
        if (!header.isBlank() && !"application/octet-stream".equals(header)) return header;

        String lower = key == null ? "" : key.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (bytes != null && bytes.length > 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8) return "image/jpeg";
        if (bytes != null && bytes.length > 4 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50) return "image/png";
        return "application/octet-stream";
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }

    public record ProofObject(String key, String contentType, String base64) {}
}

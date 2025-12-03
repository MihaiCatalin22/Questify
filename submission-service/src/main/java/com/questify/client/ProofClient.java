package com.questify.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
public class ProofClient {

    private final WebClient proofApi;
    private final WebClient http;
    private final String publicBase;
    private final String internalToken;
    private final String publicS3Base;

    public record UploadRes(String key, String putUrl) {}

    public ProofClient(
            @Value("${PROOF_SERVICE_BASE:http://proof-service:8080/api}") String base,
            @Value("${PROOF_PUBLIC_BASE_URL:https://questify.tail03c40b.ts.net/s3/questify-proofs}") String publicBase,
            @Value("${SECURITY_INTERNAL_TOKEN:dev-internal-token}") String internalToken,
            @Value("${PUBLIC_S3_BASE:https://questify.tail03c40b.ts.net/s3}") String publicS3Base
    ) {
        HttpClient hc = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(90))
                .compress(true);

        this.proofApi = WebClient.builder()
                .baseUrl(base)
                .clientConnector(new ReactorClientHttpConnector(hc))
                .build();

        this.http = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(hc))
                .build();

        this.publicBase = trimTrailingSlash(publicBase);
        this.internalToken = internalToken;
        this.publicS3Base = trimTrailingSlash(publicS3Base);
    }

    public String signGet(String key) {
        try {
            Map<?, ?> res = proofApi.get()
                    .uri(uri -> uri.path("/internal/presign/get").queryParam("key", key).build())
                    .header("X-Internal-Token", internalToken)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(10));

            if (res == null || res.get("url") == null) {
                throw new ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_GATEWAY,
                        "sign-get returned no url"
                );
            }

            String raw = String.valueOf(res.get("url"));
            return toPublicS3Url(raw);

        } catch (WebClientResponseException e) {
            throw new ResponseStatusException(
                    e.getStatusCode(),
                    "proof-service sign-get failed: " + e.getResponseBodyAsString(),
                    e
            );
        } catch (Exception e) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "proof-service sign-get unreachable",
                    e
            );
        }
    }

    public UploadRes upload(MultipartFile file, String bearer) {
        final String ct = safeContentType(file);

        UploadRes presigned;
        try {
            presigned = proofApi.post()
                    .uri("/uploads") // external presign (JWT)
                    .headers(h -> {
                        if (bearer != null && !bearer.isBlank()) {
                            h.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
                        }
                    })
                    .body(BodyInserters.fromValue(Map.of("contentType", ct)))
                    .retrieve()
                    .bodyToMono(UploadRes.class)
                    .switchIfEmpty(Mono.error(new IllegalStateException("Empty presign response")))
                    .block(Duration.ofSeconds(20));
        } catch (WebClientResponseException e) {
            log.warn("presign failed: {} {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            return directFallback(file, bearer);
        } catch (Exception e) {
            log.warn("presign unreachable: {}", e.toString());
            return directFallback(file, bearer);
        }

        if (presigned == null || presigned.putUrl() == null || presigned.key() == null) {
            log.warn("invalid presign response, falling back to direct upload");
            return directFallback(file, bearer);
        }

        try {
            var in = new InputStreamResource(file.getInputStream());
            http.put()
                    .uri(presigned.putUrl())
                    .header(HttpHeaders.CONTENT_TYPE, ct)
                    .body(BodyInserters.fromResource(in))
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(120));
            return presigned;
        } catch (WebClientResponseException e) {
            log.warn("presigned PUT failed: {} {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            return directFallback(file, bearer);
        } catch (Exception e) {
            log.warn("presigned PUT unreachable: {}", e.toString());
            return directFallback(file, bearer);
        }
    }

    private UploadRes directFallback(MultipartFile file, String bearer) {
        try {
            var mb = new org.springframework.http.client.MultipartBodyBuilder();
            mb.part("file", file.getResource())
                    .filename(file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload")
                    .contentType(MediaType.parseMediaType(safeContentType(file)));

            Map<?, ?> res = proofApi.post()
                    .uri("/uploads/direct")
                    .headers(h -> {
                        if (bearer != null && !bearer.isBlank()) {
                            h.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
                        }
                    })
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(mb.build()))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(180));

            if (res == null || res.get("key") == null) {
                throw new ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_GATEWAY,
                        "Direct upload response missing key"
                );
            }
            String key = String.valueOf(res.get("key"));
            return new UploadRes(key, null);
        } catch (WebClientResponseException e) {
            throw new ResponseStatusException(
                    e.getStatusCode(),
                    "Direct upload failed: " + e.getResponseBodyAsString(),
                    e
            );
        } catch (Exception e) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "Direct upload unreachable",
                    e
            );
        }
    }

    public String publicUrl(String key) {
        String base = trimTrailingSlash(publicBase);
        String enc = URLEncoder.encode(key, StandardCharsets.UTF_8).replace("+", "%20");
        return base + "/" + enc;
    }


    private String toPublicS3Url(String raw) {
        try {
            URI u = URI.create(raw);
            String path = u.getRawPath();
            String q = u.getRawQuery();
            String rewritten = publicS3Base + (path != null ? path : "");
            if (q != null && !q.isBlank()) rewritten += "?" + q;
            return rewritten;
        } catch (Exception e) {
            log.warn("Could not rewrite presigned url '{}': {}", raw, e.toString());
            return raw;
        }
    }

    private static String safeContentType(MultipartFile file) {
        String t = file.getContentType();
        return (t == null || t.isBlank()) ? MediaType.APPLICATION_OCTET_STREAM_VALUE : t;
    }

    private static String trimTrailingSlash(String s) {
        if (s == null || s.isBlank()) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}

package com.questify.gdpr;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
public class UserExportJobsClient {

    private final WebClient http;
    private final String internalToken;

    public UserExportJobsClient(
            WebClient.Builder builder,
            @Value("${user.service.base:${USER_SERVICE_BASE:http://user-service}}") String baseUrl,
            @Value("${SECURITY_INTERNAL_TOKEN:${INTERNAL_TOKEN:dev-internal-token}}") String token
    ) {
        this.http = builder.baseUrl(baseUrl).build();
        this.internalToken = token;
    }

    public void uploadPart(String jobId, String service, Map<String, Object> payload) {
        http.post()
                .uri("/internal/export-jobs/{jobId}/parts/{service}", jobId, service)
                .header("X-Internal-Token", internalToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(15))
                .block();
    }
}

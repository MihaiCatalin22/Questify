package com.questify.client;

import com.questify.dto.CoachDtos.UserCoachSettingsRes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Objects;

@Component
public class UserCoachSettingsClient {

    private final WebClient http;
    private final String internalToken;

    public UserCoachSettingsClient(
            WebClient.Builder builder,
            @Value("${user.service.base:http://user-service}") String baseUrl,
            @Value("${SECURITY_INTERNAL_TOKEN:${INTERNAL_TOKEN:dev-internal-token}}") String internalToken
    ) {
        this.http = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.internalToken = internalToken;
    }

    public UserCoachSettingsRes getCoachSettings(String userId) {
        return Objects.requireNonNull(http.get()
                .uri("/internal/users/{userId}/coach-settings", userId)
                .header("X-Internal-Token", internalToken)
                .retrieve()
                .bodyToMono(UserCoachSettingsRes.class)
                .block(), "Missing coach settings response");
    }
}

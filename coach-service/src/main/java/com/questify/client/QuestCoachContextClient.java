package com.questify.client;

import com.questify.dto.CoachDtos.QuestCoachContextRes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Objects;

@Component
public class QuestCoachContextClient {

    private final WebClient http;
    private final String internalToken;

    public QuestCoachContextClient(
            WebClient.Builder builder,
            @Value("${quest.service.base:http://quest-service}") String baseUrl,
            @Value("${SECURITY_INTERNAL_TOKEN:${INTERNAL_TOKEN:dev-internal-token}}") String internalToken
    ) {
        this.http = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.internalToken = internalToken;
    }

    public QuestCoachContextRes getCoachContext(String userId, boolean includeRecentHistory) {
        return Objects.requireNonNull(http.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/users/{userId}/coach-context")
                        .queryParam("includeRecentHistory", includeRecentHistory)
                        .build(userId))
                .header("X-Internal-Token", internalToken)
                .retrieve()
                .bodyToMono(QuestCoachContextRes.class)
                .block(), "Missing coach context response");
    }
}

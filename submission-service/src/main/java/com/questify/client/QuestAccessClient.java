package com.questify.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@Component
public class QuestAccessClient {
    private final WebClient webClient;
    private final String token;

    public QuestAccessClient(
            // accept either env var style or old dotted key, default to cluster service DNS
            @Value("${QUEST_SERVICE_BASE:${quest.service.base:http://quest-service:8080}}") String base,
            // unify on INTERNAL_TOKEN; also accept SECURITY_INTERNAL_TOKEN or old internal.token if present
            @Value("${SECURITY_INTERNAL_TOKEN:${INTERNAL_TOKEN:${internal.token:dev-internal-token}}}") String token,
            WebClient.Builder builder
    ) {
        this.webClient = builder.baseUrl(base).build();
        this.token = token;
    }

    public boolean allowed(String userId, Long questId) {
        Mono<Boolean> call = webClient.get()
                .uri("/internal/quests/{id}/participants/{userId}/allowed", questId, userId)
                .header("X-Internal-Token", token)
                .retrieve()
                .bodyToMono(Boolean.class)
                .onErrorResume(e ->
                        webClient.get()
                                .uri("/internal/quests/{id}/participants/{userId}/allowed", questId, userId)
                                .header("X-Internal-Token", token)
                                .retrieve()
                                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                                .map(m -> Boolean.TRUE.equals(m.get("allowed")))
                )
                .onErrorReturn(false);

        return call.block(Duration.ofSeconds(5));
    }
}

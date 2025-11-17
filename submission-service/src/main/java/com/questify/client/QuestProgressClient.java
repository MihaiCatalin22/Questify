package com.questify.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
public class QuestProgressClient {

    private final WebClient http;
    private final String internalToken;

    public QuestProgressClient(
            @Value("${QUEST_SERVICE_BASE:http://quest-service:8080}") String questBase,
            @Value("${INTERNAL_TOKEN:dev-internal-token}") String internalToken
    ) {
        this.http = WebClient.builder()
                .baseUrl(questBase)
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()))
                .build();
        this.internalToken = internalToken;
    }

    public void markCompleted(Long questId, String userId, Long submissionId) {
        try {
            http.post()
                    .uri("/internal/quests/{id}/completion", questId)
                    .header("X-Internal-Token", internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "userId", userId,
                            "submissionId", submissionId
                    ))
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(5));
        } catch (Exception e) {
            // Don’t fail the approval if quest-service is temporarily down.
            log.warn("Failed to notify quest completion: questId={}, userId={}, submissionId={}, err={}",
                    questId, userId, submissionId, e.toString());
        }
    }
}

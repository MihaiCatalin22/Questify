package com.questify.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Component
public class QuestClient {
    private final WebClient http;
    private final String internalToken;

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    public QuestClient(@Value("${quest.service.base:http://quest-service}") String baseUrl,
                       @Value("${internal.token:}") String internalDotToken,
                       @Value("${SECURITY_INTERNAL_TOKEN:}") String securityInternalToken,
                       @Value("${INTERNAL_TOKEN:dev-internal-token}") String internalToken) {
        this.http = WebClient.builder().baseUrl(baseUrl).build();
        this.internalToken = firstNonBlank(internalDotToken, securityInternalToken, internalToken);
    }

    public QuestContext getQuest(Long questId) {
        var res = http.get()
                .uri("/internal/quests/{id}/ai-review-context", questId)
                .header("X-Internal-Token", internalToken)
                .header("X-Security-Internal-Token", internalToken)
                .retrieve()
                .bodyToMono(QuestResponse.class)
                .block(Duration.ofSeconds(5));
        if (res == null) return new QuestContext("Quest " + questId, "");
        return new QuestContext(nullToBlank(res.title()), nullToBlank(res.description()));
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    public record QuestContext(String title, String description) {}
    private record QuestResponse(String title, String description) {}
}

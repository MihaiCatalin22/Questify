package com.questify.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

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
        if (res == null) return new QuestContext("Quest " + questId, "", List.of(), List.of(), List.of(), 0.7, null);
        return new QuestContext(
                nullToBlank(res.title()),
                nullToBlank(res.description()),
                nullSafeList(res.requiredEvidence()),
                nullSafeList(res.optionalEvidence()),
                nullSafeList(res.disqualifiers()),
                res.minSupportScore() == null ? 0.7 : Math.max(0.0, Math.min(1.0, res.minSupportScore())),
                res.taskType()
        );
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private static List<String> nullSafeList(List<String> values) {
        if (values == null) return List.of();
        return values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .toList();
    }

    public record QuestContext(
            String title,
            String description,
            List<String> requiredEvidence,
            List<String> optionalEvidence,
            List<String> disqualifiers,
            double minSupportScore,
            String taskType
    ) {}

    private record QuestResponse(
            String title,
            String description,
            List<String> requiredEvidence,
            List<String> optionalEvidence,
            List<String> disqualifiers,
            Double minSupportScore,
            String taskType
    ) {}
}

package com.questify.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Component
public class QuestClient {
    private final WebClient http;

    public QuestClient(@Value("${quest.service.base:http://quest-service}") String baseUrl) {
        this.http = WebClient.builder().baseUrl(baseUrl).build();
    }

    public QuestContext getQuest(Long questId) {
        var res = http.get()
                .uri("/quests/{id}", questId)
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

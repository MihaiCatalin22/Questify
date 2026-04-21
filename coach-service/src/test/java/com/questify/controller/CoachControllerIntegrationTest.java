package com.questify.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "OIDC_JWKS=https://issuer.test/protocol/openid-connect/certs",
        "OIDC_ISSUER=https://issuer.test/realms/questify",
        "SECURITY_INTERNAL_TOKEN=test-internal-token"
})
@AutoConfigureMockMvc
class CoachControllerIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final Instant FIXED_INSTANT = Instant.parse("2026-03-19T14:30:00Z");

    private static final MockWebServer userServer = new MockWebServer();
    private static final MockWebServer questServer = new MockWebServer();
    private static final MockWebServer runtimeServer = new MockWebServer();
    private static boolean serversStarted;

    @Autowired MockMvc mvc;

    @BeforeAll
    static void startServers() throws IOException {
        ensureServersStarted();
    }

    @AfterAll
    static void stopServers() throws IOException {
        if (serversStarted) {
            userServer.shutdown();
            questServer.shutdown();
            runtimeServer.shutdown();
            serversStarted = false;
        }
    }

    @AfterEach
    void drainRecordedRequests() throws InterruptedException {
        drainServer(userServer);
        drainServer(questServer);
        drainServer(runtimeServer);
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        ensureServersStarted();
        registry.add("user.service.base", () -> userServer.url("/").toString());
        registry.add("quest.service.base", () -> questServer.url("/").toString());
        registry.add("coach.runtime-base-url", () -> runtimeServer.url("/").toString());
        registry.add("coach.timeout-ms", () -> "200");
        registry.add("coach.model", () -> "smollm2:1.7b");
        registry.add("coach.retry-enabled", () -> "true");
        registry.add("coach.max-retries", () -> "1");
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        }
    }

    @Test
    void suggestions_happy_path_returns_success_payload() throws Exception {
        enqueueCoachSettings(true, "Walk daily");
        enqueueQuestContext();
        enqueueRuntimePayload(validAiPayload());

        mvc.perform(post("/coach/suggestions")
                        .with(jwt().jwt(token -> token.subject("u1").claim("preferred_username", "alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"DEFAULT\",\"includeRecentHistory\":true}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.source").value("AI"))
                .andExpect(jsonPath("$.model").value("smollm2:1.7b"))
                .andExpect(jsonPath("$.suggestions.length()").value(3))
                .andExpect(jsonPath("$.suggestions[0].description").isNotEmpty())
                .andExpect(jsonPath("$.suggestions[0].category").value("FITNESS"));

        assertInternalRequest(userServer.takeRequest(1, TimeUnit.SECONDS));
        assertInternalRequest(questServer.takeRequest(1, TimeUnit.SECONDS));
        RecordedRequest runtimeRequest = runtimeServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(runtimeRequest).isNotNull();
        assertThat(runtimeRequest.getPath()).isEqualTo("/api/chat");
        assertThat(runtimeRequest.getBody().readUtf8())
                .contains("\"stream\":false")
                .contains("\"messages\":")
                .contains("\"format\":");
    }

    @Test
    void suggestions_invalid_json_then_successful_repair_retry() throws Exception {
        enqueueCoachSettings(true, "Walk daily");
        enqueueQuestContext();
        enqueueRuntimePayload("not valid json");
        enqueueRuntimePayload(validAiPayload());

        mvc.perform(post("/coach/suggestions")
                        .with(jwt().jwt(token -> token.subject("u1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"includeRecentHistory\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.suggestions.length()").value(3));

        userServer.takeRequest(1, TimeUnit.SECONDS);
        questServer.takeRequest(1, TimeUnit.SECONDS);
        RecordedRequest firstRuntime = runtimeServer.takeRequest(1, TimeUnit.SECONDS);
        RecordedRequest secondRuntime = runtimeServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(firstRuntime).isNotNull();
        assertThat(secondRuntime).isNotNull();
        assertThat(secondRuntime.getPath()).isEqualTo("/api/chat");
        assertThat(secondRuntime.getBody().readUtf8())
                .contains("\"messages\":")
                .contains("Your previous output was invalid.");
    }

    @Test
    void suggestions_invalid_json_twice_returns_fallback() throws Exception {
        enqueueCoachSettings(true, "Walk daily");
        enqueueQuestContext();
        enqueueRuntimePayload("not valid json");
        enqueueRuntimePayload("still not valid json");

        mvc.perform(post("/coach/suggestions")
                        .with(jwt().jwt(token -> token.subject("u1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"includeRecentHistory\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FALLBACK"))
                .andExpect(jsonPath("$.source").value("SYSTEM"))
                .andExpect(jsonPath("$.suggestions.length()").value(3))
                .andExpect(jsonPath("$.suggestions[0].title").value("Take one step toward Walk daily"));

        userServer.takeRequest(1, TimeUnit.SECONDS);
        questServer.takeRequest(1, TimeUnit.SECONDS);
        runtimeServer.takeRequest(1, TimeUnit.SECONDS);
        runtimeServer.takeRequest(1, TimeUnit.SECONDS);
    }

    @Test
    void suggestions_runtime_timeout_returns_fallback() throws Exception {
        enqueueCoachSettings(true, "Walk daily");
        enqueueQuestContext();
        runtimeServer.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(ollamaResponse(validAiPayload()))
                .setBodyDelay(1, TimeUnit.SECONDS));

        mvc.perform(post("/coach/suggestions")
                        .with(jwt().jwt(token -> token.subject("u1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"includeRecentHistory\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FALLBACK"))
                .andExpect(jsonPath("$.source").value("SYSTEM"))
                .andExpect(jsonPath("$.suggestions.length()").value(3));

        userServer.takeRequest(1, TimeUnit.SECONDS);
        questServer.takeRequest(1, TimeUnit.SECONDS);
        runtimeServer.takeRequest(2, TimeUnit.SECONDS);
    }

    @Test
    void suggestions_rejects_when_opt_in_disabled() throws Exception {
        enqueueCoachSettings(false, null);

        mvc.perform(post("/coach/suggestions")
                        .with(jwt().jwt(token -> token.subject("u1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("AI Coach opt-in is not enabled"));

        userServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(questServer.takeRequest(200, TimeUnit.MILLISECONDS)).isNull();
        assertThat(runtimeServer.takeRequest(200, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void suggestions_requires_authentication() throws Exception {
        mvc.perform(post("/coach/suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private static void enqueueCoachSettings(boolean enabled, String goal) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("aiCoachEnabled", enabled);
        body.put("coachGoal", goal);
        userServer.enqueue(jsonResponse(body));
    }

    private static void enqueueQuestContext() throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("activeQuestTitles", List.of("Evening walk", "Stretch"));
        body.put("recentCompletions", List.of(
                Map.of("title", "Morning run", "completedAt", "2026-03-01T08:00:00Z"),
                Map.of("title", "Hydrate", "completedAt", "2026-02-28T10:00:00Z")
        ));
        body.put("activeQuestCount", 2);
        body.put("totalCompletedCount", 7);
        questServer.enqueue(jsonResponse(body));
    }

    private static void enqueueRuntimePayload(String payload) throws IOException {
        runtimeServer.enqueue(jsonResponse(Map.of(
                "message", Map.of(
                        "role", "assistant",
                        "content", payload
                )
        )));
    }

    private static MockResponse jsonResponse(Object body) throws IOException {
        return new MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(JSON.writeValueAsString(body));
    }

    private static String validAiPayload() {
        return """
                {
                  "suggestions": [
                    {
                      "title": "Take a 15-minute walk after dinner",
                      "description": "Go for a short walk after dinner to keep your movement goal realistic and consistent.",
                      "category": "FITNESS",
                      "estimatedMinutes": 15,
                      "difficulty": "easy",
                      "reason": "It matches your recent small wins and keeps momentum realistic."
                    },
                    {
                      "title": "Prepare tomorrow's workout clothes tonight",
                      "description": "Lay out your workout clothes tonight so tomorrow's session has less friction and setup time.",
                      "category": "HABIT",
                      "estimatedMinutes": 5,
                      "difficulty": "easy",
                      "reason": "This reduces friction and supports your exercise goal."
                    },
                    {
                      "title": "Do a short 10-minute stretching session",
                      "description": "Spend ten minutes on gentle stretching so you can stay active without overloading yourself.",
                      "category": "FITNESS",
                      "estimatedMinutes": 10,
                      "difficulty": "easy",
                      "reason": "This is a low-pressure way to stay consistent."
                    }
                  ],
                  "reflection": "You seem to do best with short, realistic actions. Keeping the next steps small helps maintain progress.",
                  "nudge": "If motivation is low, just commit to two minutes of movement."
                }
                """;
    }

    private static String ollamaResponse(String payload) throws IOException {
        return JSON.writeValueAsString(Map.of(
                "message", Map.of(
                        "role", "assistant",
                        "content", payload
                )
        ));
    }

    private static void drainServer(MockWebServer server) throws InterruptedException {
        while (server.takeRequest(10, TimeUnit.MILLISECONDS) != null) {
            // Drain leftover requests between tests so earlier failures do not pollute later assertions.
        }
    }

    private static void assertInternalRequest(RecordedRequest request) {
        assertThat(request).isNotNull();
        assertThat(request.getHeader("X-Internal-Token")).isEqualTo("test-internal-token");
    }

    private static synchronized void ensureServersStarted() {
        try {
            if (!serversStarted) {
                userServer.start();
                questServer.start();
                runtimeServer.start();
                serversStarted = true;
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to start MockWebServer", ex);
        }
    }
}

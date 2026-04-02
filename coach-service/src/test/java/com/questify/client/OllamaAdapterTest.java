package com.questify.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.questify.config.CoachProperties;
import com.questify.provider.GenerationOptions;
import com.questify.provider.GenerationPrompt;
import com.questify.provider.OllamaAdapter;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaAdapterTest {

    private final MockWebServer server = new MockWebServer();

    @BeforeEach
    void setUp() throws IOException {
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void generate_posts_expected_ollama_request_shape_and_returns_response_field() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody("{\"response\":\"{\\\"status\\\":\\\"SUCCESS\\\"}\"}"));

        var properties = new CoachProperties();
        properties.setRuntimeBaseUrl(server.url("/").toString());
        var adapter = new OllamaAdapter(WebClient.builder(), properties);
        var schema = new ObjectMapper().readTree("""
                {"type":"object","properties":{"status":{"type":"string"}}}
                """);

        String result = adapter.generate(
                new GenerationPrompt("system prompt", "user prompt"),
                new GenerationOptions("smollm2:1.7b", Duration.ofSeconds(2), 400, 0.3d, schema)
        );

        var request = server.takeRequest();
        String body = request.getBody().readUtf8();

        assertThat(request.getPath()).isEqualTo("/api/generate");
        assertThat(body).contains("\"stream\":false");
        assertThat(body).contains("\"model\":\"smollm2:1.7b\"");
        assertThat(body).contains("\"prompt\":\"user prompt\"");
        assertThat(body).contains("\"system\":\"system prompt\"");
        assertThat(body).contains("\"format\":");
        assertThat(body).contains("\"temperature\":0.3");
        assertThat(body).contains("\"num_predict\":400");
        assertThat(result).isEqualTo("{\"status\":\"SUCCESS\"}");
    }
}

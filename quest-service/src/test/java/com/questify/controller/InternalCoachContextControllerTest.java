package com.questify.controller;

import com.questify.config.SecurityConfig;
import com.questify.dto.CoachContextDtos.CoachContextRes;
import com.questify.dto.CoachContextDtos.RecentCompletionRes;
import com.questify.service.CoachContextService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalCoachContextController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
class InternalCoachContextControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean CoachContextService service;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void internalEndpoint_401_without_internal_token() throws Exception {
        mvc.perform(get("/internal/users/u1/coach-context").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalEndpoint_200_with_internal_token() throws Exception {
        when(service.getCoachContext("u1", true)).thenReturn(new CoachContextRes(
                List.of("Walk daily"),
                List.of(new RecentCompletionRes("Stretch", Instant.parse("2026-03-01T08:00:00Z"))),
                1L,
                4L
        ));

        mvc.perform(get("/internal/users/u1/coach-context")
                        .header("X-Internal-Token", "dev-internal-token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeQuestTitles[0]").value("Walk daily"))
                .andExpect(jsonPath("$.recentCompletions[0].title").value("Stretch"))
                .andExpect(jsonPath("$.activeQuestCount").value(1))
                .andExpect(jsonPath("$.totalCompletedCount").value(4));
    }
}

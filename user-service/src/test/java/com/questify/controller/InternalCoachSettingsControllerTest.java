package com.questify.controller;

import com.questify.config.SecurityConfig;
import com.questify.dto.ProfileDtos.CoachSettingsRes;
import com.questify.service.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalCoachSettingsController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
@TestPropertySource(properties = "SECURITY_INTERNAL_TOKEN=test-internal-token")
class InternalCoachSettingsControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean UserProfileService service;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void internalEndpoint_403_without_internal_token() throws Exception {
        mvc.perform(get("/internal/users/u1/coach-settings").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void internalEndpoint_200_with_internal_token() throws Exception {
        when(service.getCoachSettings("u1")).thenReturn(new CoachSettingsRes(true, "Walk daily"));

        mvc.perform(get("/internal/users/u1/coach-settings")
                        .header("X-Internal-Token", "test-internal-token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiCoachEnabled").value(true))
                .andExpect(jsonPath("$.coachGoal").value("Walk daily"));
    }
}

package com.questify.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.questify.config.JwtAuth;
import com.questify.domain.UserProfile;
import com.questify.dto.ProfileDtos.*;
import com.questify.service.UserProfileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({
        UserControllerTest.MethodSecurityOnly.class,
        UserControllerTest.TestSecurityConfig.class
})
class UserControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockitoBean UserProfileService service;
    @MockitoBean JwtAuth jwt;

    @MockitoBean JwtDecoder jwtDecoder;

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityOnly {}

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testSecurity(HttpSecurity http) throws Exception {
            http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(reg -> reg.anyRequest().permitAll());
            return http.build();
        }
    }

    @BeforeEach
    void defaults() {
        when(jwtDecoder.decode(anyString())).thenReturn(
                Jwt.withTokenValue("test-token")
                        .header("alg", "none")
                        .claim("sub", "test-user")
                        .issuedAt(Instant.parse("2025-01-01T00:00:00Z"))
                        .expiresAt(Instant.parse("2026-01-01T00:00:00Z"))
                        .build()
        );
    }

    @AfterEach
    void clearCtx() {
        SecurityContextHolder.clearContext();
    }

    /* ---------------- helpers ---------------- */

    private static UserProfile p(String id, String username) {
        return UserProfile.builder()
                .userId(id).username(username)
                .displayName(username)
                .email(username + "@x.com")
                .avatarUrl("http://a.png")
                .bio(null)
                .build();
    }

    private static UserProfile deleted(String id, String username) {
        var prof = p(id, username);
        prof.setDeletedAt(Instant.parse("2025-01-10T00:00:00Z"));
        return prof;
    }

    private static MockHttpSession sessionWithAuth(Authentication auth) {
        var ctx = new SecurityContextImpl();
        ctx.setAuthentication(auth);

        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx);
        return session;
    }

    private static Authentication auth(long id) {
        return UsernamePasswordAuthenticationToken.authenticated("u" + id, "n/a", List.of());
    }

    /* =========================================================================================
     * HAPPY PATHS
     * ========================================================================================= */

    @Test
    void me_200_sets_no_store_and_calls_ensure() throws Exception {
        var a = auth(10);

        when(jwt.userId(any())).thenReturn("u10");
        when(jwt.username(any())).thenReturn("alice");
        when(jwt.name(any())).thenReturn("Alice");
        when(jwt.email(any())).thenReturn("a@x.com");
        when(jwt.avatar(any())).thenReturn("http://a.png");

        when(service.ensure("u10", "alice", "Alice", "a@x.com", "http://a.png"))
                .thenReturn(p("u10", "alice"));

        mvc.perform(get("/users/me")
                        .session(sessionWithAuth(a))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        verify(service).ensure("u10", "alice", "Alice", "a@x.com", "http://a.png");
    }

    @Test
    void me_410_when_profile_deleted() throws Exception {
        var a = auth(10);

        when(jwt.userId(any())).thenReturn("u10");
        when(jwt.username(any())).thenReturn("alice");
        when(jwt.name(any())).thenReturn("Alice");
        when(jwt.email(any())).thenReturn("a@x.com");
        when(jwt.avatar(any())).thenReturn("http://a.png");

        when(service.ensure("u10", "alice", "Alice", "a@x.com", "http://a.png"))
                .thenReturn(deleted("u10", "alice"));

        mvc.perform(get("/users/me")
                        .session(sessionWithAuth(a))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isGone())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.message").value("Profile is deleted"));
    }

    @Test
    void updateMe_200_sets_no_store() throws Exception {
        var a = auth(10);

        when(jwt.userId(any())).thenReturn("u10");
        when(service.upsertMe(eq("u10"), any())).thenReturn(
                p("u10", "alice").toBuilder().displayName("Alice Cooper").bio("hi").build()
        );

        var req = """
                {"displayName":"Alice Cooper","bio":"hi"}
                """;

        mvc.perform(put("/users/me")
                        .session(sessionWithAuth(a))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        verify(service).upsertMe(eq("u10"), argThat(r ->
                "Alice Cooper".equals(r.displayName()) && "hi".equals(r.bio())
        ));
    }

    @Test
    void updateMe_410_when_service_says_deleted() throws Exception {
        var a = auth(10);

        when(jwt.userId(any())).thenReturn("u10");
        when(service.upsertMe(eq("u10"), any())).thenThrow(new IllegalStateException("Profile has been deleted"));

        var req = """
                {"displayName":"Alice Cooper","bio":"hi"}
                """;

        mvc.perform(put("/users/me")
                        .session(sessionWithAuth(a))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isGone())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.message").value("Profile is deleted"));
    }

    @Test
    void exportMe_200_calls_ensure_then_export() throws Exception {
        var a = auth(10);

        when(jwt.userId(any())).thenReturn("u10");
        when(jwt.username(any())).thenReturn("alice");
        when(jwt.name(any())).thenReturn("Alice");
        when(jwt.email(any())).thenReturn("a@x.com");
        when(jwt.avatar(any())).thenReturn("http://a.png");

        when(service.ensure("u10", "alice", "Alice", "a@x.com", "http://a.png"))
                .thenReturn(p("u10", "alice"));

        when(service.exportMe("u10")).thenReturn(
                new ExportRes(
                        "u10","alice","Alice","a@x.com","http://a.png",null,
                        Instant.parse("2025-01-01T00:00:00Z"),
                        Instant.parse("2025-01-02T00:00:00Z"),
                        null,
                        null
                )
        );

        mvc.perform(get("/users/me/export")
                        .session(sessionWithAuth(a))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        verify(service).ensure("u10", "alice", "Alice", "a@x.com", "http://a.png");
        verify(service).exportMe("u10");
    }

    @Test
    void deleteMe_200_calls_ensure_then_delete() throws Exception {
        var a = auth(10);

        when(jwt.userId(any())).thenReturn("u10");
        when(jwt.username(any())).thenReturn("alice");
        when(jwt.name(any())).thenReturn("Alice");
        when(jwt.email(any())).thenReturn("a@x.com");
        when(jwt.avatar(any())).thenReturn("http://a.png");

        when(service.ensure("u10", "alice", "Alice", "a@x.com", "http://a.png"))
                .thenReturn(p("u10", "alice"));

        when(service.deleteMe("u10")).thenReturn(
                new DeleteMeRes(
                        "u10",
                        Instant.parse("2025-01-03T00:00:00Z"),
                        Instant.parse("2025-01-04T00:00:00Z")
                )
        );

        mvc.perform(delete("/users/me")
                        .session(sessionWithAuth(a))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        verify(service).ensure("u10", "alice", "Alice", "a@x.com", "http://a.png");
        verify(service).deleteMe("u10");
    }

    @Test
    void byId_200() throws Exception {
        when(service.get("u1")).thenReturn(p("u1", "alice"));

        mvc.perform(get("/users/u1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        verify(service).get("u1");
    }

    @Test
    void search_200_calls_service() throws Exception {
        when(service.search("al")).thenReturn(List.of(p("u1", "alice"), p("u2", "alina")));

        mvc.perform(get("/users").param("username", "al").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        verify(service).search("al");
    }

    @Test
    void bulk_200_calls_service() throws Exception {
        when(service.bulk(List.of("u1","u2"))).thenReturn(List.of(p("u1","alice"), p("u2","bob")));

        var req = new BulkRequest(List.of("u1","u2"));

        mvc.perform(post("/users/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        verify(service).bulk(List.of("u1","u2"));
    }

    /* =========================================================================================
     * UNHAPPY / AUTHZ
     * ========================================================================================= */

    @Test
    void me_403_when_not_authenticated() throws Exception {
        mvc.perform(get("/users/me").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateMe_403_when_not_authenticated() throws Exception {
        mvc.perform(put("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"X\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }


    @Test
    void exportMe_403_when_not_authenticated() throws Exception {
        mvc.perform(get("/users/me/export").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteMe_403_when_not_authenticated() throws Exception {
        mvc.perform(delete("/users/me").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    /* =========================================================================================
     * EDGE CASES
     * ========================================================================================= */

    @Test
    void search_defaults_to_empty_string_when_param_missing() throws Exception {
        when(service.search("")).thenReturn(List.of());

        mvc.perform(get("/users").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(service).search("");
    }
}

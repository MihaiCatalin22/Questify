package com.questify.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.questify.config.JwtAuth;
import com.questify.domain.UserProfile;
import com.questify.dto.ProfileDtos.BulkRequest;
import com.questify.service.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
public class UserControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockitoBean UserProfileService service;
    @MockitoBean JwtAuth jwt;

    private static UserProfile p(String id, String username) {
        return UserProfile.builder()
                .userId(id).username(username)
                .displayName(username)
                .email(username + "@x.com")
                .avatarUrl("http://a.png")
                .bio(null)
                .build();
    }

    @Test
    void me_creates_or_returns_and_sets_no_store() throws Exception {
        when(jwt.userId(any())).thenReturn("u10");
        when(jwt.username(any())).thenReturn("alice");
        when(jwt.name(any())).thenReturn("Alice");
        when(jwt.email(any())).thenReturn("a@x.com");
        when(jwt.avatar(any())).thenReturn("http://a.png");

        when(service.ensure("u10", "alice", "Alice", "a@x.com", "http://a.png"))
                .thenReturn(p("u10","alice"));

        mvc.perform(get("/users/me").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        verify(service).ensure("u10", "alice", "Alice", "a@x.com", "http://a.png");
    }

    @Test
    void updateMe_updates_profile_and_sets_no_store() throws Exception {
        when(jwt.userId(any())).thenReturn("u10");
        when(service.upsertMe(eq("u10"), any())).thenReturn(
                p("u10","alice").toBuilder().displayName("Alice Cooper").bio("hi").build()
        );

        var req = """
                {"displayName":"Alice Cooper","bio":"hi"}
                """;

        mvc.perform(put("/users/me")
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
    void byId_returns_public_view() throws Exception {
        when(service.get("u1")).thenReturn(p("u1","alice"));

        mvc.perform(get("/users/u1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void search_calls_service_with_prefix() throws Exception {
        when(service.search("al")).thenReturn(List.of(p("u1","alice"), p("u2","alina")));

        mvc.perform(get("/users").param("username", "al").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        verify(service).search("al");
    }

    @Test
    void bulk_returns_list() throws Exception {
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
}


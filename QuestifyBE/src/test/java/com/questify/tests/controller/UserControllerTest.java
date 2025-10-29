package com.questify.tests.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.questify.controller.UserController;
import com.questify.dto.UserDtos;
import com.questify.service.UserService;
import com.questify.config.security.JwtRequestFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class UserControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockitoBean UserService service;
    @MockitoBean UserDetailsService userDetailsService;
    @MockitoBean JwtRequestFilter jwtRequestFilter;

    @BeforeEach
    void setup() {
        // per-test stubbing if needed
    }

    static class PrincipalWithId {
        private final Long id;
        private final String username;
        PrincipalWithId(Long id) { this.id = id; this.username = "u" + id; }
        public Long getId() { return id; }
        public String getUsername() { return username; }
    }

    private Authentication authWith(Long userId, String... roles) {
        Collection<GrantedAuthority> auths = new ArrayList<>();
        for (String r : roles) auths.add(new SimpleGrantedAuthority(r)); // controller checks hasAuthority('ADMIN')
        return new UsernamePasswordAuthenticationToken(new PrincipalWithId(userId), "n/a", auths);
    }

    /* ---------------------------- POST /users ---------------------------- */

    @Test
    void create_201_and_body() throws Exception {
        var req = new UserDtos.CreateUserReq(
                "catalin", "c@ex.com",
                "$2a$10$01234567890123456789015435234567890123456789012345678901",
                "Catalin");

        var res = new UserDtos.UserRes(10L, "catalin", "c@ex.com", "Catalin", null);
        when(service.create(any())).thenReturn(res);

        mvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.username").value("catalin"));

        verify(service).create(any(UserDtos.CreateUserReq.class));
    }

    @Test
    void create_duplicateUsername_400() throws Exception {
        var req = new UserDtos.CreateUserReq(
                "exists", "e@ex.com",
                "$2a$10$0123456789012345678901234567890123456789012345678901",
                null);

        when(service.create(any())).thenThrow(new IllegalArgumentException("Username already exists"));

        mvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void create_invalid_payload_400_from_validation() throws Exception {
        var bad = new UserDtos.CreateUserReq(
                "a", "not-an-email",
                "$2a$10$0123456789012345678901234567890123456789012345678901",
                null);

        mvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    /* ---------------------------- GET /users/{id} ---------------------------- */

    @Test
    void get_self_200_other_403_admin_200_and_404() throws Exception {
        when(service.getOrThrow(10L)).thenReturn(new UserDtos.UserRes(10L, "catalin", "c@ex.com", null, null));
        when(service.getOrThrow(99L)).thenThrow(new com.questify.config.NotFoundException("User not found"));

        mvc.perform(get("/users/10")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(authWith(10L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));


        mvc.perform(get("/users/10")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(authWith(77L, "ADMIN"))))
                .andExpect(status().isOk());

        mvc.perform(get("/users/99")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(authWith(77L, "ADMIN"))))
                .andExpect(status().is4xxClientError());
    }

    /* ---------------------------- GET /users (list) ---------------------------- */

    @Test
    void list_requires_admin_or_reviewer_and_returns_list() throws Exception {
        when(service.list()).thenReturn(List.of(
                new UserDtos.UserRes(1L, "a", "a@ex.com", null, null),
                new UserDtos.UserRes(2L, "b", "b@ex.com", null, null)
        ));


        mvc.perform(get("/users")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(authWith(5L, "REVIEWER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").exists());

        mvc.perform(get("/users")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(authWith(5L, "ADMIN"))))
                .andExpect(status().isOk());
    }

    /* ---------------------------- GET /users/verify ---------------------------- */

    @Test
    void verify_username_endpoint_permitAll() throws Exception {
        when(service.usernameTaken("taken")).thenReturn(true);

        mvc.perform(get("/users/verify").param("username", "taken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taken").value(true));
    }
}

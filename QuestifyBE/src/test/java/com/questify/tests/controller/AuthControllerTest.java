package com.questify.tests.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.questify.config.security.JwtRequestFilter;
import com.questify.controller.AuthController;
import com.questify.config.security.JwtUtil;
import com.questify.domain.Role;
import com.questify.domain.User;
import com.questify.dto.AuthDtos;
import com.questify.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockitoBean
    UserDetailsService userDetailsService;
    @MockitoBean
    JwtRequestFilter jwtRequestFilter;
    @MockitoBean UserRepository users;
    @MockitoBean PasswordEncoder encoder;
    @MockitoBean JwtUtil jwt;

    /* ---------------------------- helpers ---------------------------- */

    private User userEntity() {
        User u = new User();
        u.setId(42L);
        u.setUsername("neo");
        u.setEmail("neo@matrix.io");
        u.setDisplayName("The One");
        u.setPasswordHash("$2a$10$hash");
        u.setRoles(Set.of(Role.USER));
        u.setCreatedAt(Instant.now());
        u.setUpdatedAt(Instant.now());
        return u;
    }

    /* ---------------------------- POST /register ---------------------------- */

    @Test
    void register_201_and_body() throws Exception {
        var req = new AuthDtos.RegisterReq(
                "neo_theone",
                "neo@matrix.io",
                "RedPill#123!",     // password (3rd)
                "The One Neo"       // displayName (4th)
        );

        when(users.existsByUsername("neo_theone")).thenReturn(false);
        when(users.existsByEmail("neo@matrix.io")).thenReturn(false);
        when(encoder.encode("RedPill#123!")).thenReturn("$2a$10$hash");
        when(jwt.generateToken("neo_theone")).thenReturn("jwt-token");
        when(users.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(42L);
            if (u.getCreatedAt() == null) u.setCreatedAt(Instant.now());
            u.setUpdatedAt(Instant.now());
            return u;
        });

        mvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.id").value(42))
                .andExpect(jsonPath("$.user.username").value("neo_theone"))
                .andExpect(jsonPath("$.user.email").value("neo@matrix.io"))
                .andExpect(jsonPath("$.jwt").value("jwt-token"));

        verify(encoder).encode("RedPill#123!");
        verify(jwt).generateToken("neo_theone");
        verify(users).save(any(User.class));
    }

    @Test
    void register_conflict_on_username() throws Exception {
        var req = new AuthDtos.RegisterReq(
                "neo_theone",
                "neo@matrix.io",
                "RedPill#123!",     // password (3rd)
                "The One Neo"       // displayName (4th)
        );
        when(users.existsByUsername("neo_theone")).thenReturn(true);

        mvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void register_conflict_on_email() throws Exception {
        var req = new AuthDtos.RegisterReq(
                "neo_theone",
                "neo@matrix.io",
                "RedPill#123!",     // password (3rd)
                "The One Neo"       // displayName (4th)
        );
        when(users.existsByUsername("neo_theone")).thenReturn(false);
        when(users.existsByEmail("neo@matrix.io")).thenReturn(true);

        mvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    /* ---------------------------- POST /login ---------------------------- */

    @Test
    void login_by_username_200_and_token() throws Exception {
        var u = userEntity();
        when(users.findByUsername("neo")).thenReturn(Optional.of(u));
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());
        when(encoder.matches("red-pill-123", "$2a$10$hash")).thenReturn(true);
        when(jwt.generateToken("neo")).thenReturn("jwt-token");

        var body = om.writeValueAsString(new AuthController.LoginReq("neo", "red-pill-123"));

        mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(42))
                .andExpect(jsonPath("$.user.username").value("neo"))
                .andExpect(jsonPath("$.jwt").value("jwt-token"));
    }

    @Test
    void login_by_email_200_and_token() throws Exception {
        var u = userEntity();
        when(users.findByUsername("neo@matrix.io")).thenReturn(Optional.empty());
        when(users.findByEmail("neo@matrix.io")).thenReturn(Optional.of(u));
        when(encoder.matches("pw", "$2a$10$hash")).thenReturn(true);
        when(jwt.generateToken("neo")).thenReturn("jwt-token");

        var body = om.writeValueAsString(new AuthController.LoginReq("neo@matrix.io", "pw"));

        mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("neo"))
                .andExpect(jsonPath("$.jwt").value("jwt-token"));
    }

    @Test
    void login_invalid_password_401() throws Exception {
        var u = userEntity();
        when(users.findByUsername("neo")).thenReturn(Optional.of(u));
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());
        when(encoder.matches("wrong", "$2a$10$hash")).thenReturn(false);

        var body = om.writeValueAsString(new AuthController.LoginReq("neo", "wrong"));

        mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_user_not_found_401() throws Exception {
        when(users.findByUsername("trinity")).thenReturn(Optional.empty());
        when(users.findByEmail("trinity")).thenReturn(Optional.empty());

        var body = om.writeValueAsString(new AuthController.LoginReq("trinity", "pw"));

        mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }
}

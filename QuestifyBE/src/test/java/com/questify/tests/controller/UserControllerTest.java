package com.questify.tests.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.questify.Questify;
import com.questify.domain.User;
import com.questify.dto.UserDtos;
import com.questify.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = Questify.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @MockitoBean UserRepository repo;

    User saved;

    @BeforeEach
    void setup() {
        saved = User.builder()
                .id(10L)
                .username("catalin")
                .email("c@ex.com")
                .passwordHash("$2a$10$0123456789012345678987601234567890123456789012345678901")
                .build();
    }

    @Test
    void create_201_and_body() throws Exception {
        var req = new UserDtos.CreateUserReq(
                "catalin", "c@ex.com",
                "$2a$10$01234567890123456789015435234567890123456789012345678901",
                "Catalin");

        when(repo.existsByUsername("catalin")).thenReturn(false);
        when(repo.existsByEmail("c@ex.com")).thenReturn(false);
        when(repo.save(any(User.class))).thenAnswer(inv -> {
            var u = (User) inv.getArgument(0);
            u.setId(10L);
            return u;
        });

        mvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.username").value("catalin"));
    }

    @Test
    void create_duplicateUsername_400() throws Exception {
        var req = new UserDtos.CreateUserReq(
                "exists", "e@ex.com",
                "$2a$10$0123456789012345678901234567890123456789012345678901",
                null);

        when(repo.existsByUsername("exists")).thenReturn(true);

        mvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void get_200_or_404() throws Exception {
        when(repo.findById(10L)).thenReturn(Optional.of(saved));
        mvc.perform(get("/users/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));

        when(repo.findById(99L)).thenReturn(Optional.empty());
        mvc.perform(get("/users/99"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void verify_username_endpoint() throws Exception {
        when(repo.existsByUsername("taken")).thenReturn(true);
        mvc.perform(get("/users/verify").param("username", "taken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taken").value(true));
    }
}

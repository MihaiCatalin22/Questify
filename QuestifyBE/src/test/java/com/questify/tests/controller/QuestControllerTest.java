package com.questify.tests.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.questify.config.security.JwtRequestFilter;
import com.questify.controller.QuestController;
import com.questify.domain.QuestCategory;
import com.questify.domain.QuestStatus;
import com.questify.domain.QuestVisibility;
import com.questify.dto.QuestDtos;
import com.questify.service.QuestService;
import com.questify.service.SubmissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithSecurityContext;
import org.springframework.security.test.context.support.WithSecurityContextFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = QuestController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({
        QuestControllerTest.MethodSecurityOnly.class,
        QuestControllerTest.PageSerializationConfig.class
})
class QuestControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockitoBean(name = "questService") QuestService service;
    @MockitoBean SubmissionService submissions;

    @MockitoBean(name = "jwtRequestFilter") JwtRequestFilter jwtRequestFilter;
    @MockitoBean UserDetailsService userDetailsService;

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityOnly {}

    @TestConfiguration
    @EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
    static class PageSerializationConfig {}

    /* ---------------------------- lightweight auth helper ---------------------------- */

    @Retention(RetentionPolicy.RUNTIME)
    @WithSecurityContext(factory = WithCud.Factory.class)
    public @interface WithCud {
        long id();
        String[] roles() default {};
        class Factory implements WithSecurityContextFactory<WithCud> {
            @Override public SecurityContext createSecurityContext(WithCud a) {
                var ctx = SecurityContextHolder.createEmptyContext();
                Collection<GrantedAuthority> auths = new ArrayList<>();
                for (String r : a.roles()) {
                    auths.add(new SimpleGrantedAuthority("ROLE_" + r));
                    auths.add(new SimpleGrantedAuthority(r));
                }
                Authentication auth = UsernamePasswordAuthenticationToken.authenticated(
                        new Object(){ @Override public String toString(){ return "u" + a.id(); }},
                        "n/a",
                        auths
                );
                ctx.setAuthentication(auth);
                return ctx;
            }
        }
    }

    private QuestDtos.QuestRes sample() {
        Instant now = Instant.now();
        return new QuestDtos.QuestRes(
                1L, "Quest Title", "Quest Desc",
                QuestCategory.OTHER, QuestStatus.ACTIVE,
                now, now.plusSeconds(3600),
                now, now,
                10L, 0, false, QuestVisibility.PUBLIC
        );
    }

    @AfterEach void clearCtx() { SecurityContextHolder.clearContext(); }

    /* ---------------------------- POST /quests ---------------------------- */

    @Test
    @WithCud(id = 10)
    void create_201_and_body() throws Exception {
        var req = new QuestDtos.CreateQuestReq(
                "Quest Title", "Quest Desc goes here",
                QuestCategory.OTHER,
                Instant.now(), Instant.now().plusSeconds(3600),
                QuestVisibility.PUBLIC, 10L
        );
        when(service.create(any())).thenReturn(sample());

        mvc.perform(post("/quests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Quest Title"));
    }

    /* ---------------------------- GET /quests ---------------------------- */

    @Test
    void list_quests_returns_page() throws Exception {
        when(service.list(isNull(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(sample())));

        mvc.perform(get("/quests").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1));
    }

    /* ---------------------------- GET /quests/{id} ---------------------------- */

    @Test
    void get_by_id_200() throws Exception {
        when(service.getOrThrow(1L)).thenReturn(sample());

        mvc.perform(get("/quests/1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    /* ---------------------------- PATCH /quests/{id}/status ---------------------------- */

    @Test
    @WithCud(id = 10)
    void update_status_returns_updated() throws Exception {
        var req = new QuestDtos.UpdateQuestStatusReq(QuestStatus.ARCHIVED);
        var updated = new QuestDtos.QuestRes(
                1L, "Quest Title", "Quest Desc",
                QuestCategory.OTHER, QuestStatus.ARCHIVED,
                Instant.now(), Instant.now().plusSeconds(3600),
                Instant.now(), Instant.now(),
                10L, 0, false, QuestVisibility.PUBLIC
        );

        when(service.isOwner(eq(1L), any())).thenReturn(true);
        when(service.updateStatus(1L, QuestStatus.ARCHIVED)).thenReturn(updated);

        mvc.perform(patch("/quests/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    /* ---------------------------- POST /quests/{id}/archive ---------------------------- */

    @Test
    @WithCud(id = 99, roles = "REVIEWER")
    void archive_returns_quest() throws Exception {
        when(service.archive(1L)).thenReturn(sample());

        mvc.perform(post("/quests/1/archive").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    /* ---------------------------- GET /quests/by-user/{userId} ---------------------------- */

    @Test
    void list_by_creator_returns_page() throws Exception {
        when(service.listByCreator(eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sample())));

        mvc.perform(get("/quests/by-user/10").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1));
    }

    /* ---------------------------- GET /quests/search ---------------------------- */

    @Test
    void search_returns_page() throws Exception {
        when(service.search(eq("Quest"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sample())));

        mvc.perform(get("/quests/search").param("q", "Quest").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1));
    }

    /* ---------------------------- GET /quests/mine ---------------------------- */

    @Test
    @WithCud(id = 10)
    void mine_requires_auth_and_returns_page() throws Exception {
        when(service.listMine(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sample())));

        mvc.perform(get("/quests/mine").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1));
    }

    /* ---------------------------- GET /quests/discover ---------------------------- */

    @Test
    void discover_returns_page() throws Exception {
        when(service.listDiscover(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sample())));

        mvc.perform(get("/quests/discover").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1));
    }

    /* ---------------------------- GET /quests/{id}/submissions ---------------------------- */

    @Test
    @WithCud(id = 5)
    void submissions_for_quest_smoke() throws Exception {
        when(submissions.listForQuest(eq(7L), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/quests/7/submissions").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}

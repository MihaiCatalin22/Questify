package com.questify.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.questify.domain.Quest;
import com.questify.domain.QuestCategory;
import com.questify.domain.QuestStatus;
import com.questify.domain.QuestVisibility;
import com.questify.dto.QuestDtos;
import com.questify.repository.QuestParticipantRepository;
import com.questify.service.CompletionService;
import com.questify.service.QuestService;
import com.questify.config.JwtAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
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

    /* ------ Controller collaborators (mock everything) ------ */
    @MockitoBean(name = "questService") QuestService service;
    @MockitoBean QuestParticipantRepository participantRepo;
    @MockitoBean CompletionService completionService;
    @MockitoBean JwtAuth jwt;


    /* Provide a mock bean for the SpEL: @questSecurity.isOwner(...) */
    public interface QuestSecurityBean { boolean isOwner(Long id, Authentication auth); }
    @MockitoBean(name = "questSecurity") QuestSecurityBean questSecurity;

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

    private Quest quest(long id, String owner, QuestStatus status) {
        Quest q = new Quest();
        q.setId(id);
        q.setTitle("Q" + id);
        q.setDescription("D" + id);
        q.setCategory(QuestCategory.OTHER);
        q.setStatus(status);
        q.setVisibility(QuestVisibility.PUBLIC);
        q.setCreatedByUserId(owner);
        q.setStartDate(Instant.parse("2025-01-01T00:00:00Z"));
        q.setEndDate(Instant.parse("2025-12-31T23:59:59Z"));
        return q;
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
                QuestVisibility.PUBLIC, "u10" // createdByUserId is String in this controller
        );
        when(jwt.userId(any())).thenReturn("u10");
        when(service.create(eq(req), eq("u10"))).thenReturn(quest(1L, "u10", QuestStatus.ACTIVE));
        when(service.participantsCount(1L)).thenReturn(0);

        mvc.perform(post("/quests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/quests/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Q1"));
    }

    /* ---------------------------- PUT /quests/{id} ---------------------------- */
    @Test
    @WithCud(id = 10)
    void update_returns_updated_body() throws Exception {
        when(questSecurity.isOwner(eq(10L), any())).thenReturn(true);
        when(jwt.userId(any())).thenReturn("u10");

        var req = new QuestDtos.UpdateQuestReq(
                " New Title ", " New Desc ",
                QuestCategory.OTHER,
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-12-31T23:59:59Z"),
                QuestVisibility.PUBLIC
        );
        var updated = quest(10L, "u10", QuestStatus.ACTIVE);
        updated.setTitle(" New Title ");
        updated.setDescription(" New Desc ");

        when(service.update(10L, req, "u10")).thenReturn(updated);
        when(service.participantsCount(10L)).thenReturn(3);
        when(completionService.isCompleted(10L, "u10")).thenReturn(true);

        mvc.perform(put("/quests/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.title").value(" New Title "));
    }

    /* ---------------------------- PATCH /quests/{id}/status ---------------------------- */
    @Test
    @WithCud(id = 10)
    void update_status_returns_updated() throws Exception {
        when(questSecurity.isOwner(eq(4L), any())).thenReturn(true);
        when(jwt.userId(any())).thenReturn("u10");

        var req = new QuestDtos.UpdateQuestStatusReq(QuestStatus.ARCHIVED);
        var q = quest(4L, "u10", QuestStatus.ARCHIVED);

        when(service.updateStatus(4L, req, "u10")).thenReturn(q);
        when(service.participantsCount(4L)).thenReturn(1);
        when(completionService.isCompleted(4L, "u10")).thenReturn(true);

        mvc.perform(patch("/quests/4/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    /* ---------------------------- POST /quests/{id}/archive ---------------------------- */
    @Test
    @WithCud(id = 99, roles = "REVIEWER")
    void archive_returns_quest() throws Exception {
        when(jwt.userId(any())).thenReturn("u99");

        var q = quest(8L, "u99", QuestStatus.ARCHIVED);
        when(service.archive(8L, "u99")).thenReturn(q);
        when(service.participantsCount(8L)).thenReturn(2);
        when(completionService.isCompleted(8L, "u99")).thenReturn(false);

        mvc.perform(post("/quests/8/archive").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(8))
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    /* ---------------------------- POST/DELETE /quests/{id}/join ---------------------------- */
    @Test
    @WithCud(id = 5)
    void join_noContent_calls_service() throws Exception {
        when(jwt.userId(any())).thenReturn("u5");

        mvc.perform(post("/quests/5/join"))
                .andExpect(status().isNoContent());

        verify(service).join(5L, "u5");
    }

    @Test
    @WithCud(id = 5)
    void leave_noContent_calls_service() throws Exception {
        when(jwt.userId(any())).thenReturn("u5");

        mvc.perform(delete("/quests/5/join"))
                .andExpect(status().isNoContent());

        verify(service).leave(5L, "u5");
    }

    /* ---------------------------- GET /quests/{id}/participants ---------------------------- */
    @Test
    void participants_empty_page_200() throws Exception {
        when(participantRepo.findByQuest_Id(7L)).thenReturn(List.of());

        mvc.perform(get("/quests/7/participants").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.page.size").value(25))
                .andExpect(jsonPath("$.page.number").value(0));
    }


    /* ---------------------------- GET /quests ---------------------------- */
    @Test
    void list_noStatus_calls_discoverActive() throws Exception {
        var q = quest(1L, "owner", QuestStatus.ACTIVE);
        when(service.discoverActive(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(q)));
        when(service.participantsCount(1L)).thenReturn(0);

        mvc.perform(get("/quests").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1));

        verify(service, times(1)).discoverActive(any(Pageable.class));
        verify(service, never()).listByStatus(any(), any());
    }

    @Test
    void list_withStatus_calls_listByStatus() throws Exception {
        var q = quest(2L, "owner", QuestStatus.ARCHIVED);
        when(service.listByStatus(eq(QuestStatus.ARCHIVED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(q)));
        when(service.participantsCount(2L)).thenReturn(0);

        mvc.perform(get("/quests")
                        .param("status", "ARCHIVED")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(2));

        verify(service, times(1)).listByStatus(eq(QuestStatus.ARCHIVED), any(Pageable.class));
        verify(service, never()).discoverActive(any(Pageable.class));
    }

    /* ---------------------------- GET /quests/{id} ---------------------------- */
    @Test
    void get_by_id_200() throws Exception {
        var q = quest(3L, "x", QuestStatus.ACTIVE);
        when(service.get(3L)).thenReturn(q);
        when(service.participantsCount(3L)).thenReturn(0);

        mvc.perform(get("/quests/3").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3));
    }

    /* ---------------------------- GET /quests/discover ---------------------------- */
    @Test
    void discover_returns_page() throws Exception {
        var q = quest(11L, "o", QuestStatus.ACTIVE);
        when(service.discoverActive(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(q)));
        when(service.participantsCount(11L)).thenReturn(1);

        mvc.perform(get("/quests/discover").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(11));
    }

    /* ---------------------------- GET /quests/search ---------------------------- */
    @Test
    void search_returns_page() throws Exception {
        var q = quest(12L, "o", QuestStatus.ACTIVE);
        when(service.searchPublic(eq("Quest"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(q)));
        when(service.participantsCount(12L)).thenReturn(0);

        mvc.perform(get("/quests/search").param("q", "Quest").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(12));
    }
    
    /* ---------------------------- GET /quests/mine-or-participating ---------------------------- */
    @Test
    @WithCud(id = 10)
    void mine_or_participating_returns_page() throws Exception {
        when(jwt.userId(any())).thenReturn("u10");
        var q = quest(14L, "u10", QuestStatus.ACTIVE);
        when(service.mineOrParticipating(eq("u10"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(q)));
        when(service.participantsCount(14L)).thenReturn(1);
        when(completionService.isCompleted(14L, "u10")).thenReturn(false);

        mvc.perform(get("/quests/mine-or-participating").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(14));
    }
}

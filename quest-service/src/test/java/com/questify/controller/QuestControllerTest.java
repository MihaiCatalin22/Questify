package com.questify.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.questify.config.JwtAuth;
import com.questify.domain.*;
import com.questify.dto.QuestDtos;
import com.questify.repository.QuestParticipantRepository;
import com.questify.service.CompletionService;
import com.questify.service.QuestService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.*;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithSecurityContext;
import org.springframework.security.test.context.support.WithSecurityContextFactory;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = QuestController.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({
        QuestControllerTest.MethodSecurityOnly.class,
        QuestControllerTest.PageSerializationConfig.class,
        QuestControllerTest.TestHttpSecurityPermitAll.class
})
class QuestControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockitoBean QuestService service;
    @MockitoBean QuestParticipantRepository participantRepo;
    @MockitoBean CompletionService completionService;
    @MockitoBean JwtAuth jwt;

    // keep this if your app has oauth2-resource-server on the classpath
    @MockitoBean JwtDecoder jwtDecoder;

    /* SpEL bean used by: @PreAuthorize("@questSecurity.isOwner(#id, authentication)") */
    public interface QuestSecurityBean { boolean isOwner(Long id, Authentication auth); }
    @MockitoBean(name = "questSecurity") QuestSecurityBean questSecurity;

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityOnly {}

    @TestConfiguration
    @EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
    static class PageSerializationConfig {}

    /**
     * Permit all at HTTP layer, so we test controller + method security.
     * CSRF stays enabled (default) so we keep using .with(csrf()) in non-GET tests.
     */
    @TestConfiguration
    static class TestHttpSecurityPermitAll {
        @Bean
        @Order(0)
        SecurityFilterChain testChain(HttpSecurity http) throws Exception {
            return http
                    .authorizeHttpRequests(reg -> reg.anyRequest().permitAll())
                    .build();
        }
    }

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
                        // principal string becomes "u{id}"
                        "u" + a.id(),
                        "n/a",
                        auths
                );
                ctx.setAuthentication(auth);
                return ctx;
            }
        }
    }

    @BeforeEach
    void defaults() {
        // deny by default unless test overrides
        when(questSecurity.isOwner(anyLong(), any())).thenReturn(false);

        // avoid null Page returns => controller mapping NPE
        when(service.discoverActive(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(service.listByStatus(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(service.searchPublic(anyString(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(service.mine(anyString(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(service.mineByStatus(anyString(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(service.mineOrParticipating(anyString(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(service.mineOrParticipatingFiltered(anyString(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(service.mineOrParticipatingWithStatus(anyString(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        // accept any bearer token if some config tries to decode
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

    private Quest quest(long id, String owner, QuestStatus status) {
        Quest q = new Quest();
        q.setId(id);
        q.setTitle("Q" + id);
        q.setDescription("This is a valid quest description " + id);
        q.setCategory(QuestCategory.OTHER);
        q.setStatus(status);
        q.setVisibility(QuestVisibility.PUBLIC);
        q.setCreatedByUserId(owner);
        q.setStartDate(Instant.parse("2025-01-01T00:00:00Z"));
        q.setEndDate(Instant.parse("2025-12-31T23:59:59Z"));
        return q;
    }

    private QuestDtos.CreateQuestReq validCreateReq(String createdBy) {
        return new QuestDtos.CreateQuestReq(
                "Valid Title",
                "Valid description text",
                QuestCategory.OTHER,
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-02T00:00:00Z"),
                QuestVisibility.PUBLIC,
                createdBy
        );
    }

    private QuestDtos.UpdateQuestReq validUpdateReq() {
        return new QuestDtos.UpdateQuestReq(
                "Valid Title Updated",
                "Valid description text updated",
                QuestCategory.OTHER,
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-02T00:00:00Z"),
                QuestVisibility.PUBLIC
        );
    }

    /* =========================================================================================
     * HAPPY PATHS
     * ========================================================================================= */

    @Test
    @WithCud(id = 10)
    void mineOrParticipatingSummary_200() throws Exception {
        when(jwt.userId(any())).thenReturn("u10");
        when(service.countMineOrParticipatingFiltered("u10", null)).thenReturn(5L);
        when(completionService.countCompletedInMineOrParticipatingFiltered("u10", null)).thenReturn(2L);

        mvc.perform(get("/quests/mine-or-participating/summary")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questsTotal").value(5))
                .andExpect(jsonPath("$.questsCompleted").value(2));
    }

    @Test
    @WithCud(id = 10)
    void create_201_and_location() throws Exception {
        var req = validCreateReq("u10");

        when(jwt.userId(any())).thenReturn("u10");
        when(service.create(eq(req), eq("u10"))).thenReturn(quest(1L, "u10", QuestStatus.ACTIVE));
        when(service.participantsCount(1L)).thenReturn(0);

        mvc.perform(post("/quests")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/quests/1"))
                .andExpect(jsonPath("$.id").value(1));

        verify(service).create(eq(req), eq("u10"));
    }

    @Test
    @WithCud(id = 10)
    void update_200_when_owner() throws Exception {
        when(questSecurity.isOwner(eq(10L), any())).thenReturn(true);
        when(jwt.userId(any())).thenReturn("u10");

        var req = validUpdateReq();
        var updated = quest(10L, "u10", QuestStatus.ACTIVE);
        updated.setTitle(req.title());
        updated.setDescription(req.description());

        when(service.update(10L, req, "u10")).thenReturn(updated);
        when(service.participantsCount(10L)).thenReturn(3);
        when(completionService.isCompleted(10L, "u10")).thenReturn(true);

        mvc.perform(put("/quests/10")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.title").value(req.title()));

        verify(service).update(10L, req, "u10");
        verify(completionService).isCompleted(10L, "u10");
    }

    @Test
    @WithCud(id = 10)
    void updateStatus_200_when_owner() throws Exception {
        when(questSecurity.isOwner(eq(4L), any())).thenReturn(true);
        when(jwt.userId(any())).thenReturn("u10");

        var req = new QuestDtos.UpdateQuestStatusReq(QuestStatus.ARCHIVED);
        var q = quest(4L, "u10", QuestStatus.ARCHIVED);

        when(service.updateStatus(4L, req, "u10")).thenReturn(q);
        when(service.participantsCount(4L)).thenReturn(1);
        when(completionService.isCompleted(4L, "u10")).thenReturn(true);

        mvc.perform(patch("/quests/4/status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    @WithCud(id = 10)
    void archivePost_200_when_owner() throws Exception {
        when(questSecurity.isOwner(eq(8L), any())).thenReturn(true);
        when(jwt.userId(any())).thenReturn("u10");

        var q = quest(8L, "u10", QuestStatus.ARCHIVED);
        when(service.archive(8L, "u10")).thenReturn(q);

        mvc.perform(post("/quests/8/archive")
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(8))
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    @WithCud(id = 99, roles = "REVIEWER")
    void archivePost_200_when_reviewer_role_allows() throws Exception {
        when(jwt.userId(any())).thenReturn("u99");
        when(service.archive(9L, "u99")).thenReturn(quest(9L, "u99", QuestStatus.ARCHIVED));

        mvc.perform(post("/quests/9/archive")
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9));
    }

    @Test
    @WithCud(id = 5)
    void join_204_calls_service() throws Exception {
        when(jwt.userId(any())).thenReturn("u5");

        mvc.perform(post("/quests/5/join").with(csrf()))
                .andExpect(status().isNoContent());

        verify(service).join(5L, "u5");
    }

    @Test
    @WithCud(id = 5)
    void leave_204_calls_service() throws Exception {
        when(jwt.userId(any())).thenReturn("u5");

        mvc.perform(delete("/quests/5/join").with(csrf()))
                .andExpect(status().isNoContent());

        verify(service).leave(5L, "u5");
    }

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

    @Test
    void list_noStatus_calls_discoverActive() throws Exception {
        var q = quest(1L, "owner", QuestStatus.ACTIVE);
        when(service.discoverActive(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(q)));

        mvc.perform(get("/quests").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1));

        verify(service).discoverActive(any(Pageable.class));
        verify(service, never()).listByStatus(any(), any());
    }

    @Test
    void list_withStatus_calls_listByStatus() throws Exception {
        var q = quest(2L, "owner", QuestStatus.ARCHIVED);
        when(service.listByStatus(eq(QuestStatus.ARCHIVED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(q)));

        mvc.perform(get("/quests")
                        .param("status", "ARCHIVED")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(2));

        verify(service).listByStatus(eq(QuestStatus.ARCHIVED), any(Pageable.class));
        verify(service, never()).discoverActive(any(Pageable.class));
    }

    @Test
    @WithCud(id = 10)
    void get_by_id_200_authenticated_path_calls_completed() throws Exception {
        when(jwt.userId(any())).thenReturn("u10");
        when(service.get(3L)).thenReturn(quest(3L, "x", QuestStatus.ACTIVE));
        when(completionService.isCompleted(3L, "u10")).thenReturn(true);

        mvc.perform(get("/quests/3").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3));

        verify(completionService).isCompleted(3L, "u10");
    }

    @Test
    void discover_returns_page() throws Exception {
        var q = quest(11L, "o", QuestStatus.ACTIVE);
        when(service.discoverActive(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(q)));

        mvc.perform(get("/quests/discover").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(11));
    }

    @Test
    void search_returns_page() throws Exception {
        var q = quest(12L, "o", QuestStatus.ACTIVE);
        when(service.searchPublic(eq("Quest"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(q)));

        mvc.perform(get("/quests/search").param("q", "Quest").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(12));
    }

    @Test
    @WithCud(id = 10)
    void mineOrParticipating_200_default_branch_calls_mineOrParticipating() throws Exception {
        when(jwt.userId(any())).thenReturn("u10");
        var q = quest(30L, "u10", QuestStatus.ACTIVE);
        when(service.mineOrParticipating(eq("u10"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(q)));

        mvc.perform(get("/quests/mine-or-participating").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(30));

        verify(service).mineOrParticipating(eq("u10"), any(Pageable.class));
        verify(service, never()).mineOrParticipatingFiltered(anyString(), any(), any(Pageable.class));
        verify(service, never()).mineOrParticipatingWithStatus(anyString(), any(), any(Pageable.class));
    }

    /* =========================================================================================
     * UNHAPPY / AUTHZ
     * ========================================================================================= */

    @Test
    void mineOrParticipatingSummary_403_when_not_authenticated() throws Exception {
        mvc.perform(get("/quests/mine-or-participating/summary"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_403_when_not_authenticated() throws Exception {
        // IMPORTANT: body must be VALID, otherwise you'll get 400 before @PreAuthorize runs.
        var req = validCreateReq("u1");

        mvc.perform(post("/quests")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        verify(service, never()).create(any(), anyString());
    }

    @Test
    @WithCud(id = 10)
    void update_403_when_not_owner() throws Exception {
        when(questSecurity.isOwner(eq(10L), any())).thenReturn(false);

        // IMPORTANT: body must be VALID, otherwise you'll get 400 before SpEL @PreAuthorize runs.
        var req = validUpdateReq();

        mvc.perform(put("/quests/10")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        verify(service, never()).update(anyLong(), any(), anyString());
    }

    @Test
    void join_403_when_not_authenticated() throws Exception {
        mvc.perform(post("/quests/5/join").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void mine_403_when_not_authenticated() throws Exception {
        mvc.perform(get("/quests/mine"))
                .andExpect(status().isForbidden());
    }

    @Test
    void mineOrParticipating_403_when_not_authenticated() throws Exception {
        mvc.perform(get("/quests/mine-or-participating"))
                .andExpect(status().isForbidden());
    }

    /* =========================================================================================
     * EDGE CASES
     * ========================================================================================= */

    @Test
    void list_clamps_size_to_100_and_page_to_min_0() throws Exception {
        when(service.discoverActive(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(quest(1L, "o", QuestStatus.ACTIVE))));

        ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);

        mvc.perform(get("/quests")
                        .param("page", "-5")
                        .param("size", "500")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(service).discoverActive(cap.capture());
        Pageable p = cap.getValue();
        assertThat(p.getPageNumber()).isEqualTo(0);
        assertThat(p.getPageSize()).isEqualTo(100);
    }

    @Test
    @WithCud(id = 10)
    void create_400_when_invalid_body() throws Exception {
        when(jwt.userId(any())).thenReturn("u10");

        // missing title etc -> should violate @Valid
        String badJson = "{\"description\":\"short\",\"category\":\"OTHER\",\"visibility\":\"PUBLIC\",\"createdByUserId\":\"u10\"}";

        mvc.perform(post("/quests")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(badJson))
                .andExpect(status().isBadRequest());
    }
}

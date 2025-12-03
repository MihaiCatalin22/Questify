package com.questify.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.questify.client.QuestAccessClient;
import com.questify.config.JwtAuth;
import com.questify.domain.ReviewStatus;
import com.questify.domain.Submission;
import com.questify.dto.SubmissionDtos;
import com.questify.service.SubmissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.mock.web.MockMultipartFile;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = SubmissionController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({
        SubmissionControllerTest.MethodSecurityOnly.class,
        SubmissionControllerTest.PageSerializationConfig.class
})
class SubmissionControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @BeforeEach
    void defaults() {
        given(service.byStatus(any(ReviewStatus.class), anyInt(), anyInt()))
                .willReturn(new PageImpl<>(List.of()));
        given(service.pending(anyInt(), anyInt()))
                .willReturn(new PageImpl<>(List.of()));
        given(service.mine(anyString(), anyInt(), anyInt()))
                .willReturn(new PageImpl<>(List.of()));
    }

    /* ---------- collaborators (all mocked) ---------- */
    @MockitoBean SubmissionService service;
    @MockitoBean JwtAuth jwt;
    @MockitoBean QuestAccessClient questAccess;

    /* SpEL bean used by: @PreAuthorize("@submissionSecurity.canRead(#id, authentication)") */
    public interface SubmissionSecurityBean { boolean canRead(Long id, Authentication auth); }
    @MockitoBean(name = "submissionSecurity") SubmissionSecurityBean submissionSecurity;

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

    private Submission sub(long id, long questId, String user, ReviewStatus status, String proofKey, String note) {
        Submission s = new Submission();
        s.setId(id);
        s.setQuestId(questId);
        s.setUserId(user);
        s.setStatus(status);
        s.setProofKey(proofKey);
        s.setNote(note);
        s.setCreatedAt(Instant.parse("2025-01-01T00:00:00Z"));
        return s;
    }

    @AfterEach void clearCtx() { SecurityContextHolder.clearContext(); }

    /* ---------------------------- POST /submissions (JSON) ---------------------------- */

    @Test
    @WithCud(id = 10)
    void createJson_201_and_body() throws Exception {
        var req = new SubmissionDtos.CreateSubmissionReq(9L, "proof/key.png", "hello");
        when(jwt.userId(any())).thenReturn("u10");
        when(service.create(eq("u10"), eq(req))).thenReturn(sub(1L, 9L, "u10", ReviewStatus.PENDING, "proof/key.png", "hello"));

        mvc.perform(post("/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/submissions/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.questId").value(9))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    /* ---------------------------- POST /submissions (multipart) ---------------------------- */

    @Test
    @WithCud(id = 5)
    void createMultipart_201_and_body() throws Exception {
        when(jwt.userId(any())).thenReturn("u5");
        MockMultipartFile file = new MockMultipartFile("file", "p.png", "image/png", new byte[]{1,2,3});
        when(service.createFromMultipart(eq(7L), eq("c"), any(), eq("u5"), eq("abc123")))
                .thenReturn(sub(2L, 7L, "u5", ReviewStatus.PENDING, "uploaded/key", "c"));

        mvc.perform(multipart("/submissions")
                        .file(file)
                        .param("questId", "7")
                        .param("comment", "c")
                        .header("Authorization", "Bearer abc123")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/submissions/2"))
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.questId").value(7));
    }

    /* ---------------------------- GET /submissions/quest/{questId} ---------------------------- */

    @Test
    @WithCud(id = 42, roles = "REVIEWER")
    void forQuest_elevated_allows_and_returns_page() throws Exception {
        when(jwt.userId(any())).thenReturn("u42");
        when(questAccess.allowed(anyString(), anyLong())).thenReturn(true); // robust even if elevated calc changes
        var s = sub(3L, 9L, "u1", ReviewStatus.PENDING, "k", null);
        when(service.forQuest(9L, 0, 10)).thenReturn(new PageImpl<>(List.of(s)));

        mvc.perform(get("/submissions/quest/9").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(3));
    }

    /* ---------------------------- GET /submissions/{id} (SpEL security) ---------------------------- */

    @Test
    @WithCud(id = 5)
    void byId_200() throws Exception {
        when(submissionSecurity.canRead(eq(77L), any())).thenReturn(true);
        var s = sub(77L, 1L, "u5", ReviewStatus.PENDING, "k", null);
        when(service.get(77L)).thenReturn(s);

        mvc.perform(get("/submissions/77").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(77));
    }

    /* ---------------------------- GET /submissions/mine ---------------------------- */

    @Test
    @WithCud(id = 7)
    void mine_requires_auth_and_returns_page() throws Exception {
        when(jwt.userId(any())).thenReturn("u7");
        var s = sub(10L, 2L, "u7", ReviewStatus.REJECTED, null, "n");
        when(service.mine(eq("u7"), eq(0), eq(10))).thenReturn(new PageImpl<>(List.of(s)));

        mvc.perform(get("/submissions/mine").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10));
    }

    /* ---------------------------- GET /submissions/pending ---------------------------- */

    @Test
    @WithCud(id = 100, roles = "REVIEWER")
    void pending_requires_reviewer_and_returns_page() throws Exception {
        var s = sub(12L, 3L, "uX", ReviewStatus.PENDING, null, null);
        when(service.pending(0, 10)).thenReturn(new PageImpl<>(List.of(s)));

        mvc.perform(get("/submissions/pending").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    /* ---------------------------- POST /submissions/{id}/review ---------------------------- */

    @Test
    @WithCud(id = 100, roles = "REVIEWER")
    void review_returns_updated() throws Exception {
        when(jwt.userId(any())).thenReturn("u100");
        var req = new SubmissionDtos.ReviewReq(ReviewStatus.APPROVED, "ok");
        var reviewed = sub(55L, 8L, "u8", ReviewStatus.APPROVED, "k", "ok");
        when(service.review(55L, req, "u100")).thenReturn(reviewed);

        mvc.perform(post("/submissions/55/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(55))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    /* ---------------------------- GET /submissions (list with role branching) ---------------------------- */


    @Test
    @WithCud(id = 11)
    void list_non_elevated_calls_mine() throws Exception {
        when(jwt.userId(any())).thenReturn("u11");
        var s = sub(23L, 5L, "u11", ReviewStatus.REJECTED, null, null);
        when(service.mine(eq("u11"), eq(0), eq(10)))
                .thenReturn(new PageImpl<>(List.of(s)));

        mvc.perform(get("/submissions").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(23));

        verify(service).mine(eq("u11"), eq(0), eq(10));
        verify(service, never()).pending(anyInt(), anyInt());
        verify(service, never()).byStatus(any(), anyInt(), anyInt());
    }

    /* ---------------------------- GET /submissions/{id}/proof (redirect) ---------------------------- */

    @Test
    @WithCud(id = 10)
    void proof_owner_receives_redirect() throws Exception {
        when(jwt.userId(any())).thenReturn("u10");
        var s = sub(66L, 9L, "u10", ReviewStatus.PENDING, "proof/key", null);
        when(service.get(66L)).thenReturn(s);
        when(service.signedGetUrl("proof/key")).thenReturn("https://signed/url");

        mvc.perform(get("/submissions/66/proof"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://signed/url"));
    }

    /* ---------------------------- GET /submissions/{id}/proof-url (json url) ---------------------------- */

    @Test
    @WithCud(id = 10, roles = "REVIEWER")
    void proofUrl_reviewer_receives_json() throws Exception {
        when(jwt.userId(any())).thenReturn("u10");
        var s = sub(77L, 9L, "u7", ReviewStatus.PENDING, "proof/key", null);
        when(service.get(77L)).thenReturn(s);
        when(questAccess.allowed(anyString(), anyLong())).thenReturn(true);
        when(service.signedGetUrl("proof/key")).thenReturn("https://signed/url");

        mvc.perform(get("/submissions/77/proof-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://signed/url"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900));
    }
}
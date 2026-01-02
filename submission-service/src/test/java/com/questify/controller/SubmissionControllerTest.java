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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = SubmissionController.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({
        SubmissionControllerTest.MethodSecurityOnly.class,
        SubmissionControllerTest.PageSerializationConfig.class
})
class SubmissionControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    /* ---------- collaborators (all mocked) ---------- */
    @MockitoBean SubmissionService service;
    @MockitoBean JwtAuth jwt;
    @MockitoBean QuestAccessClient questAccess;

    @MockitoBean JwtDecoder jwtDecoder;

    public interface SubmissionSecurityBean { boolean canRead(Long id, Authentication auth); }
    @MockitoBean(name = "submissionSecurity") SubmissionSecurityBean submissionSecurity;

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityOnly {}

    @TestConfiguration
    @EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
    static class PageSerializationConfig {}

    @BeforeEach
    void defaults() {
        // prevent accidental null Page returns => NPE in controller
        when(service.all(anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of()));
        when(service.byStatus(any(ReviewStatus.class), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of()));
        when(service.pending(anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of()));
        when(service.mine(anyString(), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of()));
        when(service.forQuest(anyLong(), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of()));
        when(service.signedGetUrlsForSubmission(anyLong())).thenReturn(List.of());

        when(questAccess.allowed(anyString(), anyLong())).thenReturn(false);
        when(submissionSecurity.canRead(anyLong(), any())).thenReturn(false);

        // Any Bearer token is accepted in tests (so Authorization header won't cause BadJwtException)
        when(jwtDecoder.decode(anyString())).thenReturn(
                Jwt.withTokenValue("test-token")
                        .header("alg", "none")
                        .claim("sub", "test-user")
                        .issuedAt(Instant.parse("2025-01-01T00:00:00Z"))
                        .expiresAt(Instant.parse("2026-01-01T00:00:00Z"))
                        .build()
        );
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

    @AfterEach
    void clearCtx() {
        SecurityContextHolder.clearContext();
    }

    /* =========================================================================================
     * HAPPY PATHS
     * ========================================================================================= */

    @Test
    @WithCud(id = 10)
    void mineSummary_200() throws Exception {
        when(jwt.userId(any())).thenReturn("u10");
        when(service.countMine("u10")).thenReturn(7L);

        mvc.perform(get("/submissions/mine/summary").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submissionsTotal").value(7));
    }

    @Test
    @WithCud(id = 10)
    void createJson_201_and_body() throws Exception {
        var req = new SubmissionDtos.CreateSubmissionReq(9L, "proof/key.png", "hello");
        when(jwt.userId(any())).thenReturn("u10");
        when(service.create(eq("u10"), eq(req)))
                .thenReturn(sub(1L, 9L, "u10", ReviewStatus.PENDING, "proof/key.png", "hello"));

        mvc.perform(post("/submissions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/submissions/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.questId").value(9))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

//    @Test
//    @WithCud(id = 5)
//    void createMultipart_single_201_and_body_and_bearer() throws Exception {
//        when(jwt.userId(any())).thenReturn("u5");
//        MockMultipartFile file = new MockMultipartFile("file", "p.png", "image/png", new byte[]{1,2,3});
//        when(service.createFromMultipart(eq(7L), eq("c"), any(), eq("u5"), eq("abc123")))
//                .thenReturn(sub(2L, 7L, "u5", ReviewStatus.PENDING, "uploaded/key", "c"));
//
//        mvc.perform(multipart("/submissions")
//                        .file(file)
//                        .param("questId", "7")
//                        .param("comment", "c")
//                        .header("Authorization", "Bearer abc123")
//                        .accept(MediaType.APPLICATION_JSON))
//                //.andExpect(status().isCreated())
//                .andExpect(header().string("Location", "/submissions/2"))
//                .andExpect(jsonPath("$.id").value(2))
//                .andExpect(jsonPath("$.questId").value(7));
//
//        verify(service).createFromMultipart(eq(7L), eq("c"), any(), eq("u5"), eq("abc123"));
//        verify(service, never()).createFromMultipartMany(anyLong(), any(), anyList(), anyString(), any());
//    }
//
//    @Test
//    @WithCud(id = 5)
//    void createMultipart_multi_201_uses_many() throws Exception {
//        when(jwt.userId(any())).thenReturn("u5");
//
//        var f1 = new MockMultipartFile("files", "a.png", "image/png", new byte[]{1});
//        var f2 = new MockMultipartFile("files", "b.png", "image/png", new byte[]{2});
//
//        when(service.createFromMultipartMany(eq(7L), eq("c"), anyList(), eq("u5"), eq("tok")))
//                .thenReturn(sub(3L, 7L, "u5", ReviewStatus.PENDING, "k", "c"));
//
//        mvc.perform(multipart("/submissions")
//                        .file(f1)
//                        .file(f2)
//                        .param("questId", "7")
//                        .param("comment", "c")
//                        .header("Authorization", "Bearer tok")
//                        .accept(MediaType.APPLICATION_JSON))
//                //.andExpect(status().isCreated())
//                .andExpect(header().string("Location", "/submissions/3"))
//                .andExpect(jsonPath("$.id").value(3))
//                .andExpect(jsonPath("$.questId").value(7));
//
//        verify(service).createFromMultipartMany(eq(7L), eq("c"), anyList(), eq("u5"), eq("tok"));
//        verify(service, never()).createFromMultipart(anyLong(), any(), any(), anyString(), any());
//    }

    @Test
    @WithCud(id = 42, roles = "REVIEWER")
    void forQuest_elevated_allows_and_returns_page() throws Exception {
        when(jwt.userId(any())).thenReturn("u42");
        var s = sub(3L, 9L, "u1", ReviewStatus.PENDING, "k", null);
        when(service.forQuest(9L, 0, 10)).thenReturn(new PageImpl<>(List.of(s)));

        mvc.perform(get("/submissions/quest/9").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(3));
    }

    @Test
    @WithCud(id = 5)
    void byId_200_when_spel_allows() throws Exception {
        when(submissionSecurity.canRead(eq(77L), any())).thenReturn(true);
        when(service.get(77L)).thenReturn(sub(77L, 1L, "u5", ReviewStatus.PENDING, "k", null));

        mvc.perform(get("/submissions/77").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(77));
    }

    @Test
    @WithCud(id = 7)
    void mine_200_returns_page() throws Exception {
        when(jwt.userId(any())).thenReturn("u7");
        when(service.mine(eq("u7"), eq(0), eq(10)))
                .thenReturn(new PageImpl<>(List.of(sub(10L, 2L, "u7", ReviewStatus.REJECTED, null, "n"))));

        mvc.perform(get("/submissions/mine").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10));
    }

    @Test
    @WithCud(id = 100, roles = "REVIEWER")
    void pending_200_reviewer_returns_page() throws Exception {
        when(service.pending(0, 10))
                .thenReturn(new PageImpl<>(List.of(sub(12L, 3L, "uX", ReviewStatus.PENDING, null, null))));

        mvc.perform(get("/submissions/pending").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test
    @WithCud(id = 100, roles = "REVIEWER")
    void review_200_reviewer_returns_updated() throws Exception {
        when(jwt.userId(any())).thenReturn("u100");
        var req = new SubmissionDtos.ReviewReq(ReviewStatus.APPROVED, "ok");
        when(service.review(eq(55L), eq(req), eq("u100")))
                .thenReturn(sub(55L, 8L, "u8", ReviewStatus.APPROVED, "k", "ok"));

        mvc.perform(post("/submissions/55/review")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(55))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @WithCud(id = 11)
    void list_non_elevated_calls_mine() throws Exception {
        when(jwt.userId(any())).thenReturn("u11");
        when(service.mine(eq("u11"), eq(0), eq(10)))
                .thenReturn(new PageImpl<>(List.of(sub(23L, 5L, "u11", ReviewStatus.REJECTED, null, null))));

        mvc.perform(get("/submissions")
                        .param("page", "0").param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(23));

        verify(service).mine(eq("u11"), eq(0), eq(10));
        verify(service, never()).all(anyInt(), anyInt());
        verify(service, never()).byStatus(any(), anyInt(), anyInt());
    }

    @Test
    @WithCud(id = 1, roles = "REVIEWER")
    void list_elevated_without_status_calls_all() throws Exception {
        when(service.all(eq(0), eq(10)))
                .thenReturn(new PageImpl<>(List.of(sub(90L, 9L, "uX", ReviewStatus.PENDING, null, null))));

        mvc.perform(get("/submissions")
                        .param("page", "0").param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(90));

        verify(service).all(eq(0), eq(10));
        verify(service, never()).mine(anyString(), anyInt(), anyInt());
        verify(service, never()).byStatus(any(), anyInt(), anyInt());
    }

    @Test
    @WithCud(id = 1, roles = "ADMIN")
    void list_elevated_with_status_calls_byStatus() throws Exception {
        when(service.byStatus(eq(ReviewStatus.REJECTED), eq(0), eq(10)))
                .thenReturn(new PageImpl<>(List.of(sub(91L, 9L, "uX", ReviewStatus.REJECTED, null, null))));

        mvc.perform(get("/submissions")
                        .param("status", "REJECTED")
                        .param("page", "0").param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("REJECTED"));

        verify(service).byStatus(eq(ReviewStatus.REJECTED), eq(0), eq(10));
        verify(service, never()).all(anyInt(), anyInt());
        verify(service, never()).mine(anyString(), anyInt(), anyInt());
    }

    @Test
    @WithCud(id = 10)
    void proof_owner_receives_redirect() throws Exception {
        when(jwt.userId(any())).thenReturn("u10");
        when(service.get(66L)).thenReturn(sub(66L, 9L, "u10", ReviewStatus.PENDING, "proof/key", null));
        when(service.signedGetUrl("proof/key")).thenReturn("https://signed/url");

        mvc.perform(get("/submissions/66/proof"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://signed/url"));
    }

    @Test
    @WithCud(id = 10, roles = "REVIEWER")
    void proofUrl_reviewer_receives_json() throws Exception {
        when(jwt.userId(any())).thenReturn("u10");
        when(service.get(77L)).thenReturn(sub(77L, 9L, "u7", ReviewStatus.PENDING, "proof/key", null));
        when(service.signedGetUrl("proof/key")).thenReturn("https://signed/url");

        mvc.perform(get("/submissions/77/proof-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://signed/url"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900));
    }

    @Test
    @WithCud(id = 10)
    void proofUrls_owner_receives_json_list() throws Exception {
        when(jwt.userId(any())).thenReturn("u10");
        when(service.get(88L)).thenReturn(sub(88L, 9L, "u10", ReviewStatus.PENDING, "ignored", null));
        when(service.signedGetUrlsForSubmission(88L))
                .thenReturn(List.of("https://u/1", "https://u/2"));

        mvc.perform(get("/submissions/88/proof-urls").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urls[0]").value("https://u/1"))
                .andExpect(jsonPath("$.urls[1]").value("https://u/2"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900));
    }

    /* =========================================================================================
     * UNHAPPY / AUTHZ
     * ========================================================================================= */

    @Test
    void mineSummary_401_when_not_authenticated() throws Exception {
        mvc.perform(get("/submissions/mine/summary").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createJson_401_when_not_authenticated() throws Exception {
        var req = new SubmissionDtos.CreateSubmissionReq(9L, "k", "x");

        mvc.perform(post("/submissions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createMultipart_401_when_not_authenticated() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "p.png", "image/png", new byte[]{1});

        mvc.perform(multipart("/submissions")
                        .file(file)
                        .param("questId", "7")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }


    @Test
    void mine_401_when_not_authenticated() throws Exception {
        mvc.perform(get("/submissions/mine").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_401_when_not_authenticated() throws Exception {
        mvc.perform(get("/submissions").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithCud(id = 7) // authenticated but not reviewer/admin
    void pending_403_when_not_reviewer() throws Exception {
        mvc.perform(get("/submissions/pending").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithCud(id = 7) // authenticated but not reviewer/admin
    void review_403_when_not_reviewer() throws Exception {
        var req = new SubmissionDtos.ReviewReq(ReviewStatus.APPROVED, "ok");

        mvc.perform(post("/submissions/55/review")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithCud(id = 5)
    void byId_403_when_spel_denies() throws Exception {
        when(submissionSecurity.canRead(eq(77L), any())).thenReturn(false);

        mvc.perform(get("/submissions/77").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithCud(id = 2)
    void forQuest_403_when_not_allowed() throws Exception {
        when(jwt.userId(any())).thenReturn("u2");
        when(questAccess.allowed("u2", 9L)).thenReturn(false);

        mvc.perform(get("/submissions/quest/9").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithCud(id = 2)
    void proof_403_when_not_allowed() throws Exception {
        when(jwt.userId(any())).thenReturn("u2");
        when(service.get(66L)).thenReturn(sub(66L, 9L, "u1", ReviewStatus.PENDING, "proof/key", null));
        when(questAccess.allowed("u2", 9L)).thenReturn(false);

        mvc.perform(get("/submissions/66/proof"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithCud(id = 2)
    void proofUrl_403_when_not_allowed() throws Exception {
        when(jwt.userId(any())).thenReturn("u2");
        when(service.get(77L)).thenReturn(sub(77L, 9L, "u1", ReviewStatus.PENDING, "proof/key", null));
        when(questAccess.allowed("u2", 9L)).thenReturn(false);

        mvc.perform(get("/submissions/77/proof-url").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithCud(id = 2)
    void proofUrls_403_when_not_allowed() throws Exception {
        when(jwt.userId(any())).thenReturn("u2");
        when(service.get(88L)).thenReturn(sub(88L, 9L, "u1", ReviewStatus.PENDING, "proof/key", null));
        when(questAccess.allowed("u2", 9L)).thenReturn(false);

        mvc.perform(get("/submissions/88/proof-urls").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    /* =========================================================================================
     * EDGE CASES
     * ========================================================================================= */

    @Test
    @WithCud(id = 10)
    void createJson_400_when_invalid() throws Exception {
        when(jwt.userId(any())).thenReturn("u10");
        var bad = new SubmissionDtos.CreateSubmissionReq(null, null, "x"); // violates @NotNull/@NotBlank

        mvc.perform(post("/submissions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithCud(id = 5)
    void createMultipart_400_when_no_file_provided() throws Exception {
        when(jwt.userId(any())).thenReturn("u5");

        mvc.perform(multipart("/submissions")
                        .with(csrf())
                        .param("questId", "7")
                        .param("comment", "c")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithCud(id = 10)
    void proof_404_when_no_proofKey() throws Exception {
        when(jwt.userId(any())).thenReturn("u10");
        when(service.get(66L)).thenReturn(sub(66L, 9L, "u10", ReviewStatus.PENDING, "   ", null));

        mvc.perform(get("/submissions/66/proof"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithCud(id = 10)
    void proofUrl_404_when_no_proofKey() throws Exception {
        when(jwt.userId(any())).thenReturn("u10");
        when(service.get(77L)).thenReturn(sub(77L, 9L, "u10", ReviewStatus.PENDING, "", null));

        mvc.perform(get("/submissions/77/proof-url").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithCud(id = 10)
    void proofUrls_404_when_empty_list() throws Exception {
        when(jwt.userId(any())).thenReturn("u10");
        when(service.get(88L)).thenReturn(sub(88L, 9L, "u10", ReviewStatus.PENDING, "ignored", null));
        when(service.signedGetUrlsForSubmission(88L)).thenReturn(List.of());

        mvc.perform(get("/submissions/88/proof-urls").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}

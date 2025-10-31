package com.questify.tests.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.questify.config.security.CustomUserDetails;
import com.questify.config.security.JwtRequestFilter;
import com.questify.controller.SubmissionController;
import com.questify.domain.ReviewStatus;
import com.questify.dto.SubmissionDtos.CreateSubmissionReq;
import com.questify.dto.SubmissionDtos.ReviewSubmissionReq;
import com.questify.dto.SubmissionDtos.SubmissionRes;
import com.questify.service.SubmissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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

@WebMvcTest(controllers = SubmissionController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({
        SubmissionControllerTest.MethodSecurityOnly.class,
        SubmissionControllerTest.AuthArgumentResolverConfig.class,
        SubmissionControllerTest.PageSerializationConfig.class
})
class SubmissionControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockitoBean SubmissionService service;
    @MockitoBean UserDetailsService userDetailsService;
    @MockitoBean JwtRequestFilter jwtRequestFilter;

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityOnly { }

    /** Make controller method param `Authentication` come from SecurityContextHolder. */
    @TestConfiguration
    static class AuthArgumentResolverConfig implements WebMvcConfigurer {
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new HandlerMethodArgumentResolver() {
                @Override public boolean supportsParameter(MethodParameter parameter) {
                    return Authentication.class.isAssignableFrom(parameter.getParameterType());
                }
                @Override public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                                        NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                    return SecurityContextHolder.getContext().getAuthentication();
                }
            });
        }
    }

    @TestConfiguration
    @EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
    static class PageSerializationConfig { }

    /* ---------------------------- Custom @WithCud ---------------------------- */

    @Retention(RetentionPolicy.RUNTIME)
    @WithSecurityContext(factory = WithCud.Factory.class)
    public @interface WithCud {
        long id();
        String username() default "";
        String[] roles() default {}; // e.g. "REVIEWER"
        class Factory implements WithSecurityContextFactory<WithCud> {
            @Override
            public org.springframework.security.core.context.SecurityContext createSecurityContext(WithCud withCud) {
                SecurityContext ctx = SecurityContextHolder.createEmptyContext();

                CustomUserDetails cud = mock(CustomUserDetails.class);
                String uname = withCud.username().isEmpty() ? ("u" + withCud.id()) : withCud.username();
                when(cud.getId()).thenReturn(withCud.id());
                when(cud.getUsername()).thenReturn(uname);

                Collection<GrantedAuthority> auths = new ArrayList<>();
                for (String r : withCud.roles()) {
                    auths.add(new SimpleGrantedAuthority("ROLE_" + r));
                    auths.add(new SimpleGrantedAuthority(r)); // your hasAnyRole accepts either
                }

                Authentication auth = UsernamePasswordAuthenticationToken.authenticated(cud, "n/a", auths);
                ctx.setAuthentication(auth);
                return ctx;
            }
        }
    }

    private SubmissionRes sample() {
        return new SubmissionRes(
                1L, 10L, 5L, "Answer text", "http://proof.com", "note",
                ReviewStatus.PENDING, null,
                Instant.now(), Instant.now(),
                null, null, null, null, false
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /* ---------------------------- POST /submissions (JSON) ---------------------------- */

    @Test
    @WithCud(id = 5)
    void create_json_200_and_body() throws Exception {
        var req = new CreateSubmissionReq(10L, 5L, "Answer text", "http://proof.com", "note");
        when(service.createFromJson(any())).thenReturn(sample());

        mvc.perform(post("/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.reviewStatus").value("PENDING"))
                .andExpect(jsonPath("$.proofUrl").value("http://proof.com"));

        verify(service).createFromJson(any(CreateSubmissionReq.class));
    }

    /* ---------------------------- GET /submissions/../quests/{id}/submissions ---------------------------- */

    @Test
    @WithCud(id = 5)
    void list_for_quest_returns_page() throws Exception {
        var page = new PageImpl<>(List.of(sample()));
        when(service.listForQuest(eq(10L), isNull(), any(Pageable.class))).thenReturn(page);

        mvc.perform(get("/submissions/../quests/10/submissions").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].reviewStatus").value("PENDING"));
    }

    /* ---------------------------- GET /submissions (list current user's or all) ---------------------------- */



    /* ---------------------------- GET /submissions/{id} ---------------------------- */

    @Test
    @WithCud(id = 5)
    void get_by_id_200() throws Exception {
        when(service.getOrThrow(1L)).thenReturn(sample());

        mvc.perform(get("/submissions/1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    /* ---------------------------- POST /submissions/{id}/review ---------------------------- */



    /* ---------------------------- GET /submissions/{id}/proof-url ---------------------------- */

    @Test
    @WithCud(id = 5)
    void proof_url_returns_map() throws Exception {
        when(service.buildPresignedProofUrl(eq(7L), any())).thenReturn("https://signed");

        mvc.perform(get("/submissions/7/proof-url")
                        .param("ttlSeconds", "120")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://signed"))
                .andExpect(jsonPath("$.expiresInSeconds").value(120));
    }

    /* ---------------------------- GET /submissions/{id}/proof ---------------------------- */

    @Test
    @WithCud(id = 5)
    void proof_redirects_or_streams() throws Exception {
        ResponseEntity<InputStreamResource> redirect =
                ResponseEntity.<InputStreamResource>status(302)
                        .header("Location", "https://public")
                        .build();

        when(service.streamProof(9L)).thenReturn(redirect);

        mvc.perform(get("/submissions/9/proof"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "https://public"));
    }
}

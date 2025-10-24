package com.questify.tests.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.questify.Questify;
import com.questify.domain.ReviewStatus;
import com.questify.dto.SubmissionDtos.*;
import com.questify.service.SubmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = Questify.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class SubmissionControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @MockitoBean SubmissionService service;

    SubmissionRes sample;

    @BeforeEach
    void setup() {
        sample = new SubmissionRes(
                1L,
                5L,
                10L,
                "Answer text",
                "http://proof.com",
                ReviewStatus.PENDING,
                "Pending review",
                Instant.now()
        );
    }

    @Test
    void create_submission_201_and_body() throws Exception {
        var req = new CreateSubmissionReq(
                5L,
                10L,
                "Answer text",
                "http://proof.com"
        );

        when(service.create(any())).thenReturn(sample);

        mvc.perform(post("/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk()) // change to .isCreated() if your controller returns CREATED
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.reviewStatus").value("PENDING"))
                .andExpect(jsonPath("$.proofUrl").value("http://proof.com"));
    }


    @Test
    void list_for_quest_returns_page() throws Exception {
        var list = new PageImpl<>(List.of(sample));
        when(service.listForQuest(eq(10L), any(), any(PageRequest.class))).thenReturn(list);

        mvc.perform(get("/submissions/by-quest/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].reviewStatus").value("PENDING"));
    }

    @Test
    void list_for_user_returns_page() throws Exception {
        var list = new PageImpl<>(List.of(sample));
        when(service.listForUser(eq(5L), any(PageRequest.class))).thenReturn(list);

        mvc.perform(get("/submissions/by-user/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1));
    }

    @Test
    void review_submission_returns_updated() throws Exception {
        var req = new ReviewSubmissionReq(ReviewStatus.APPROVED, "Looks good");
        var reviewed = new SubmissionRes(
                1L, 5L, 10L,
                "Answer text",
                "http://proof.com",
                ReviewStatus.APPROVED,
                "Looks good",
                Instant.now()
        );

        when(service.review(eq(1L), any(ReviewSubmissionReq.class))).thenReturn(reviewed);

        mvc.perform(post("/submissions/1/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("APPROVED"))
                .andExpect(jsonPath("$.reviewNote").value("Looks good"));
    }
}

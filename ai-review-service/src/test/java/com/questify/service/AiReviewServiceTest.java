package com.questify.service;

import com.questify.client.ProofClient;
import com.questify.client.QuestClient;
import com.questify.client.SubmissionClient;
import com.questify.domain.AiReviewRunSource;
import com.questify.domain.AiReviewRecommendation;
import com.questify.domain.AiReviewResult;
import com.questify.provider.ModelClient;
import com.questify.repository.AiReviewAttemptRepository;
import com.questify.repository.AiReviewResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiReviewServiceTest {

    @Mock AiReviewResultRepository results;
    @Mock AiReviewAttemptRepository attempts;
    @Mock QuestClient quests;
    @Mock SubmissionClient submissions;
    @Mock ProofClient proofs;
    @Mock ModelClient model;

    AiReviewService service;

    @BeforeEach
    void setUp() {
        service = new AiReviewService(results, attempts, quests, submissions, proofs, model);
        ReflectionTestUtils.setField(service, "modelName", "llava:7b");
        when(attempts.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void reviewSubmission_stores_model_recommendation_for_image_proof() {
        when(results.findBySubmissionId(10L)).thenReturn(Optional.empty());
        when(quests.getQuest(5L)).thenReturn(new QuestClient.QuestContext("Daily run", "Submit proof of a run."));
        when(proofs.getProofs(10L)).thenReturn(List.of(new ProofClient.ProofObject("proof/a.png", "image/png", "BASE64IMG")));
        when(model.generate(any())).thenReturn("""
                {"recommendation":"LIKELY_VALID","confidence":0.82,"reasons":["The comment and image match a completed run."],"mediaSupported":true}
                """);
        when(results.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        AiReviewResult out = service.reviewSubmission(new AiReviewService.SubmissionCreated(
                10L, 5L, "u1", "Finished a 20 minute run.", Instant.parse("2026-05-01T10:00:00Z")
        ));

        assertThat(out.getRecommendation()).isEqualTo(AiReviewRecommendation.LIKELY_VALID);
        assertThat(out.getConfidence()).isEqualTo(0.82);
        assertThat(out.getModel()).isEqualTo("llava:7b");
        assertThat(out.getReasons()).contains("completed run");

        ArgumentCaptor<AiReviewResult> saved = ArgumentCaptor.forClass(AiReviewResult.class);
        verify(results).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getSubmissionId()).isEqualTo(10L);
        verify(attempts).save(any());
    }

    @Test
    void reviewSubmission_marks_unsupported_when_no_supported_images_are_available() {
        when(results.findBySubmissionId(11L)).thenReturn(Optional.empty());
        when(quests.getQuest(5L)).thenReturn(new QuestClient.QuestContext("Practice guitar", "Upload proof."));
        when(proofs.getProofs(11L)).thenReturn(List.of(new ProofClient.ProofObject("proof/v.mp4", "video/mp4", null)));
        when(results.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        AiReviewResult out = service.reviewSubmission(new AiReviewService.SubmissionCreated(
                11L, 5L, "u1", "I practiced.", Instant.parse("2026-05-01T10:00:00Z")
        ));

        assertThat(out.getRecommendation()).isEqualTo(AiReviewRecommendation.UNSUPPORTED_MEDIA);
        assertThat(out.getReasons()).contains("No supported image proof was available for AI review.");
        verifyNoInteractions(model);
        verify(attempts).save(any());
    }

    @Test
    void reviewSubmission_stores_ai_failed_when_model_call_fails() {
        when(results.findBySubmissionId(12L)).thenReturn(Optional.empty());
        when(quests.getQuest(5L)).thenReturn(new QuestClient.QuestContext("Read chapter", "Show notes."));
        when(proofs.getProofs(12L)).thenReturn(List.of(new ProofClient.ProofObject("proof/a.jpg", "image/jpeg", "BASE64IMG")));
        when(model.generate(any())).thenThrow(new RuntimeException("ollama down"));
        when(results.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        AiReviewResult out = service.reviewSubmission(new AiReviewService.SubmissionCreated(
                12L, 5L, "u1", "I read it.", Instant.parse("2026-05-01T10:00:00Z")
        ));

        assertThat(out.getRecommendation()).isEqualTo(AiReviewRecommendation.AI_FAILED);
        assertThat(out.getReasons()).contains("AI review failed; manual review is required.");
        verify(attempts).save(any());
    }

    @Test
    void reviewSubmission_stores_ai_failed_when_context_fetch_fails_and_does_not_throw() {
        when(results.findBySubmissionId(13L)).thenReturn(Optional.empty());
        when(quests.getQuest(5L)).thenThrow(new RuntimeException("quest service unavailable"));
        when(results.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        AiReviewResult out = service.reviewSubmission(new AiReviewService.SubmissionCreated(
                13L, 5L, "u1", "I did it.", Instant.parse("2026-05-01T10:00:00Z")
        ));

        assertThat(out.getRecommendation()).isEqualTo(AiReviewRecommendation.AI_FAILED);
        assertThat(out.getReasons()).contains("AI review failed; manual review is required.");
        verifyNoInteractions(model);
        verify(attempts).save(any());
    }

    @Test
    void rerunForSubmission_forces_overwrite_of_existing_result() {
        AiReviewResult existing = AiReviewResult.builder()
                .id(99L)
                .submissionId(20L)
                .questId(8L)
                .userId("u-old")
                .recommendation(AiReviewRecommendation.AI_FAILED)
                .confidence(0.0)
                .model("llava:7b")
                .reasons("old")
                .mediaSupported(true)
                .reviewedAt(Instant.parse("2026-05-01T00:00:00Z"))
                .build();

        when(results.findBySubmissionId(20L)).thenReturn(Optional.of(existing));
        when(submissions.getSubmissionContext(20L)).thenReturn(new SubmissionClient.SubmissionContext(
                20L, 8L, "u1", "done", Instant.parse("2026-05-01T10:00:00Z"), List.of("proof/a.png")
        ));
        when(quests.getQuest(8L)).thenReturn(new QuestClient.QuestContext("Run", "Do a run"));
        when(proofs.getProofsFromKeys(List.of("proof/a.png")))
                .thenReturn(List.of(new ProofClient.ProofObject("proof/a.png", "image/png", "BASE64IMG")));
        when(model.generate(any())).thenReturn("""
                {"recommendation":"LIKELY_VALID","confidence":0.91,"reasons":["Good match"],"mediaSupported":true}
                """);
        when(results.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        AiReviewResult out = service.rerunForSubmission(20L, AiReviewRunSource.MANUAL, "reviewer-1");

        assertThat(out.getId()).isEqualTo(99L);
        assertThat(out.getRecommendation()).isEqualTo(AiReviewRecommendation.LIKELY_VALID);
        assertThat(out.getConfidence()).isEqualTo(0.91);
        assertThat(out.getUserId()).isEqualTo("u1");
        verify(attempts, atLeastOnce()).save(any());
    }
}

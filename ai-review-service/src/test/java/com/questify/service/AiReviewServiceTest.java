package com.questify.service;

import com.questify.client.ProofClient;
import com.questify.client.QuestClient;
import com.questify.client.SubmissionClient;
import com.questify.domain.AiReviewRecommendation;
import com.questify.domain.AiReviewResult;
import com.questify.domain.AiReviewRunSource;
import com.questify.provider.ModelClient;
import com.questify.repository.AiReviewAttemptRepository;
import com.questify.repository.AiReviewResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        when(attempts.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(results.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void reviewSubmission_returns_likely_valid_when_required_evidence_matches() {
        when(results.findBySubmissionId(10L)).thenReturn(Optional.empty());
        when(quests.getQuest(5L)).thenReturn(new QuestClient.QuestContext(
                "Solve 4 algebra equations",
                "Upload solved worksheet with four equations.",
                List.of("equations", "worksheet"),
                List.of("steps"),
                List.of("game hud"),
                0.75,
                "generic"
        ));
        when(proofs.getProofs(10L)).thenReturn(List.of(new ProofClient.ProofObject("proof/math.png", "image/png", "BASE64")));

        when(model.generate(any()))
                .thenReturn(new ModelClient.ModelResponse(
                        "{\"ocr_text\":[\"equations\",\"worksheet\"],\"quality\":\"HIGH\"}",
                        "qwen2.5vl:7b", false, null))
                .thenReturn(new ModelClient.ModelResponse(
                        "{\"visible_objects\":[\"worksheet\"],\"visible_text\":[\"equations\"],\"scene_type\":\"study desk\",\"activity_clues\":[\"solved steps\"],\"uncertainty_flags\":[]}",
                        "qwen2.5vl:7b", false, null));

        AiReviewResult out = service.reviewSubmission(new AiReviewService.SubmissionCreated(
                10L, 5L, "u1", "done", Instant.parse("2026-05-01T10:00:00Z")
        ));

        assertThat(out.getRecommendation()).isEqualTo(AiReviewRecommendation.LIKELY_VALID);
        assertThat(out.isGeneratedPolicy()).isFalse();
        assertThat(out.getMatchedEvidence()).contains("equations");
    }

    @Test
    void reviewSubmission_returns_likely_invalid_when_disqualifier_matches() {
        when(results.findBySubmissionId(11L)).thenReturn(Optional.empty());
        when(quests.getQuest(6L)).thenReturn(new QuestClient.QuestContext(
                "Practice geography",
                "Upload notes or map-based proof.",
                List.of("map"),
                List.of("country"),
                List.of("game hud", "menu screen"),
                0.75,
                "generic"
        ));
        when(proofs.getProofs(11L)).thenReturn(List.of(new ProofClient.ProofObject("proof/game.png", "image/png", "BASE64")));
        when(model.generate(any()))
                .thenReturn(new ModelClient.ModelResponse(
                        "{\"ocr_text\":[\"inventory\",\"skills\"],\"quality\":\"MEDIUM\"}",
                        "qwen2.5vl:3b", true, "primary timeout"))
                .thenReturn(new ModelClient.ModelResponse(
                        "{\"visible_objects\":[\"game hud\"],\"visible_text\":[\"inventory\"],\"scene_type\":\"video game ui\",\"activity_clues\":[\"menu screen\"],\"uncertainty_flags\":[]}",
                        "qwen2.5vl:3b", true, "primary timeout"));

        AiReviewResult out = service.reviewSubmission(new AiReviewService.SubmissionCreated(
                11L, 6L, "u1", "done", Instant.parse("2026-05-01T10:00:00Z")
        ));

        assertThat(out.getRecommendation()).isEqualTo(AiReviewRecommendation.LIKELY_INVALID);
        assertThat(out.isFallbackUsed()).isTrue();
        assertThat(out.getMatchedDisqualifiers()).contains("game hud");
    }

    @Test
    void reviewSubmission_marks_unsupported_when_no_supported_images_are_available() {
        when(results.findBySubmissionId(12L)).thenReturn(Optional.empty());
        when(quests.getQuest(5L)).thenReturn(new QuestClient.QuestContext(
                "Practice guitar", "Upload proof.", List.of(), List.of(), List.of(), 0.7, "generic"));
        when(proofs.getProofs(12L)).thenReturn(List.of(new ProofClient.ProofObject("proof/v.mp4", "video/mp4", null)));

        AiReviewResult out = service.reviewSubmission(new AiReviewService.SubmissionCreated(
                12L, 5L, "u1", "I practiced.", Instant.parse("2026-05-01T10:00:00Z")
        ));

        assertThat(out.getRecommendation()).isEqualTo(AiReviewRecommendation.UNSUPPORTED_MEDIA);
        verifyNoInteractions(model);
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
                .model("qwen2.5vl:3b")
                .reasons("old")
                .mediaSupported(true)
                .reviewedAt(Instant.parse("2026-05-01T00:00:00Z"))
                .build();

        when(results.findBySubmissionId(20L)).thenReturn(Optional.of(existing));
        when(submissions.getSubmissionContext(20L)).thenReturn(new SubmissionClient.SubmissionContext(
                20L, 8L, "u1", "done", Instant.parse("2026-05-01T10:00:00Z"), List.of("proof/a.png")
        ));
        when(quests.getQuest(8L)).thenReturn(new QuestClient.QuestContext(
                "Run", "Do a run", List.of("distance"), List.of(), List.of("game hud"), 0.75, "generic"));
        when(proofs.getProofsFromKeys(List.of("proof/a.png")))
                .thenReturn(List.of(new ProofClient.ProofObject("proof/a.png", "image/png", "BASE64")));
        when(model.generate(any()))
                .thenReturn(new ModelClient.ModelResponse(
                        "{\"ocr_text\":[\"distance\"],\"quality\":\"HIGH\"}",
                        "qwen2.5vl:7b", false, null))
                .thenReturn(new ModelClient.ModelResponse(
                        "{\"visible_objects\":[\"running app\"],\"visible_text\":[\"distance\"],\"scene_type\":\"fitness app\",\"activity_clues\":[\"distance metric\"],\"uncertainty_flags\":[]}",
                        "qwen2.5vl:7b", false, null));

        AiReviewResult out = service.rerunForSubmission(20L, AiReviewRunSource.MANUAL, "reviewer-1");

        assertThat(out.getId()).isEqualTo(99L);
        assertThat(out.getRecommendation()).isEqualTo(AiReviewRecommendation.LIKELY_VALID);
        assertThat(out.getUserId()).isEqualTo("u1");
        verify(attempts, atLeastOnce()).save(any());
    }
}

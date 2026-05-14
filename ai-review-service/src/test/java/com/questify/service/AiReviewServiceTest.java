package com.questify.service;

import com.questify.client.ProofClient;
import com.questify.client.QuestClient;
import com.questify.client.SubmissionClient;
import com.questify.domain.AiReviewRecommendation;
import com.questify.domain.AiReviewResult;
import com.questify.domain.AiReviewRunSource;
import com.questify.domain.AiReviewRunStatus;
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
        service = new AiReviewService(results, attempts, quests, submissions, proofs, model, Runnable::run);
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
                        "qwen2.5vl:7b", false, null))
                .thenReturn(new ModelClient.ModelResponse(
                        "{\"matched_claims\":[\"equations worksheet\"],\"contradictions\":[],\"disqualifier_hits\":[],\"evidence_strength\":\"HIGH\",\"notes\":[]}",
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
                        "qwen2.5vl:3b", true, "primary timeout"))
                .thenReturn(new ModelClient.ModelResponse(
                        "{\"matched_claims\":[],\"contradictions\":[\"evidence is game UI\"],\"disqualifier_hits\":[\"game hud\"],\"evidence_strength\":\"LOW\",\"notes\":[]}",
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
                .status(AiReviewRunStatus.FAILED)
                .confidence(0.0)
                .supportScore(0.0)
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
                        "qwen2.5vl:7b", false, null))
                .thenReturn(new ModelClient.ModelResponse(
                        "{\"matched_claims\":[\"distance metric\"],\"contradictions\":[],\"disqualifier_hits\":[],\"evidence_strength\":\"HIGH\",\"notes\":[]}",
                        "qwen2.5vl:7b", false, null));

        AiReviewResult out = service.rerunForSubmission(20L, AiReviewRunSource.MANUAL, "reviewer-1");

        assertThat(out.getId()).isEqualTo(99L);
        assertThat(out.getStatus()).isIn(AiReviewRunStatus.PENDING, AiReviewRunStatus.COMPLETED);
        assertThat(out.getUserId()).isEqualTo("u1");
        verify(model, atLeast(2)).generate(any());
        verify(attempts, atLeastOnce()).save(any());
    }

    @Test
    void reviewSubmission_phrase_required_evidence_matches_by_tokens_not_literal_substring() {
        when(results.findBySubmissionId(30L)).thenReturn(Optional.empty());
        when(quests.getQuest(9L)).thenReturn(new QuestClient.QuestContext(
                "Code a simple web page",
                "Create a web page and submit coding evidence.",
                List.of("code a simple web page"),
                List.of(),
                List.of("game hud"),
                0.75,
                "generic"
        ));
        when(proofs.getProofs(30L)).thenReturn(List.of(new ProofClient.ProofObject("proof/code.png", "image/png", "BASE64")));
        when(model.generate(any()))
                .thenReturn(new ModelClient.ModelResponse(
                        "{\"ocr_text\":[\"public class Example\"],\"quality\":\"HIGH\"}",
                        "qwen2.5vl:3b", false, null))
                .thenReturn(new ModelClient.ModelResponse(
                        "{\"visible_objects\":[\"code editor\"],\"visible_text\":[\"public class Example\"],\"scene_type\":\"coding workspace\",\"activity_clues\":[\"web page code\"],\"uncertainty_flags\":[]}",
                        "qwen2.5vl:3b", false, null))
                .thenReturn(new ModelClient.ModelResponse(
                        "{\"matched_claims\":[\"web page code\"],\"contradictions\":[],\"disqualifier_hits\":[],\"evidence_strength\":\"HIGH\",\"notes\":[]}",
                        "qwen2.5vl:3b", false, null));

        AiReviewResult out = service.reviewSubmission(new AiReviewService.SubmissionCreated(
                30L, 9L, "u1", "implemented", Instant.parse("2026-05-01T10:00:00Z")
        ));

        assertThat(out.getMatchedEvidence()).contains("code a simple web page");
        assertThat(out.getDecisionPath()).isEqualTo("required_satisfied");
    }

    @Test
    void reviewSubmission_accepts_symbolic_algebra_work_when_semantic_evidence_matches_quest() {
        when(results.findBySubmissionId(40L)).thenReturn(Optional.empty());
        when(quests.getQuest(14L)).thenReturn(new QuestClient.QuestContext(
                "Solve 5 algebra problems",
                "Upload a picture of your work showing five solved algebra problems.",
                List.of("solve", "algebra"),
                List.of("worked steps", "written notes", "result screenshot"),
                List.of("video game interface", "unrelated commercial product"),
                0.75,
                "generic"
        ));
        when(proofs.getProofs(40L)).thenReturn(List.of(new ProofClient.ProofObject("proof/algebra.png", "image/png", "BASE64")));
        when(model.generate(any()))
                .thenReturn(new ModelClient.ModelResponse(
                        "{\"ocr_text\":[\"1) 2x+3=11 -> x=4\",\"2) 5y-7=18 -> y=5\",\"3) 3a+2=14 -> a=4\"],\"quality\":\"HIGH\"}",
                        "qwen2.5vl:7b", false, null))
                .thenReturn(new ModelClient.ModelResponse(
                        "{\"visible_objects\":[\"notebook page\",\"handwritten worksheet\"],\"visible_text\":[\"2x+3=11\",\"5y-7=18\",\"3a+2=14\"],\"scene_type\":\"study work photo\",\"activity_clues\":[\"multiple worked problem solutions\",\"arithmetic steps beside equations\"],\"uncertainty_flags\":[]}",
                        "qwen2.5vl:7b", false, null))
                .thenReturn(new ModelClient.ModelResponse(
                        "{\"matched_claims\":[\"visible handwritten equations with answers\"],\"missing_evidence\":[],\"unrelated_evidence\":[],\"contradictions\":[],\"disqualifier_hits\":[],\"relevance_score\":0.92,\"support_score\":0.86,\"evidence_strength\":\"HIGH\",\"notes\":[\"The image contains equations and worked steps tied to the requested task.\"]}",
                        "qwen2.5vl:7b", false, null));

        AiReviewResult out = service.reviewSubmission(new AiReviewService.SubmissionCreated(
                40L, 14L, "u1", "Here are my five problems.", Instant.parse("2026-05-01T10:00:00Z")
        ));

        assertThat(out.getRecommendation()).isEqualTo(AiReviewRecommendation.LIKELY_VALID);
        assertThat(out.getConfidence()).isGreaterThanOrEqualTo(0.75);
        assertThat(out.getSupportScore()).isGreaterThanOrEqualTo(0.75);
        assertThat(out.getObservedSignals()).contains("visible handwritten equations with answers");
    }

    @Test
    void reviewSubmission_rejects_unrelated_product_photo_with_low_support_confidence() {
        when(results.findBySubmissionId(41L)).thenReturn(Optional.empty());
        when(quests.getQuest(14L)).thenReturn(new QuestClient.QuestContext(
                "Solve 5 algebra problems",
                "Upload a picture of your work showing five solved algebra problems.",
                List.of("solve", "algebra"),
                List.of("worked steps", "written notes", "result screenshot"),
                List.of("video game interface", "unrelated commercial product"),
                0.75,
                "generic"
        ));
        when(proofs.getProofs(41L)).thenReturn(List.of(new ProofClient.ProofObject("proof/red-bull.jpg", "image/jpeg", "BASE64")));
        when(model.generate(any()))
                .thenReturn(new ModelClient.ModelResponse(
                        "{\"ocr_text\":[\"RED BULL\"],\"quality\":\"HIGH\"}",
                        "qwen2.5vl:7b", false, null))
                .thenReturn(new ModelClient.ModelResponse(
                        "{\"visible_objects\":[\"red bull can\",\"commercial product\"],\"visible_text\":[\"RED BULL\"],\"scene_type\":\"product photo\",\"activity_clues\":[],\"uncertainty_flags\":[]}",
                        "qwen2.5vl:7b", false, null))
                .thenReturn(new ModelClient.ModelResponse(
                        "{\"matched_claims\":[],\"missing_evidence\":[\"no visible algebra work\",\"no solved problems\"],\"unrelated_evidence\":[\"commercial drink can\"],\"contradictions\":[\"The image is a product photo, not school work.\"],\"disqualifier_hits\":[\"unrelated commercial product\"],\"relevance_score\":0.03,\"support_score\":0.02,\"evidence_strength\":\"LOW\",\"notes\":[\"Visible evidence is unrelated to the requested algebra task.\"]}",
                        "qwen2.5vl:7b", false, null));

        AiReviewResult out = service.reviewSubmission(new AiReviewService.SubmissionCreated(
                41L, 14L, "u1", "done", Instant.parse("2026-05-01T10:00:00Z")
        ));

        assertThat(out.getRecommendation()).isEqualTo(AiReviewRecommendation.LIKELY_INVALID);
        assertThat(out.getConfidence()).isLessThan(0.25);
        assertThat(out.getSupportScore()).isLessThan(0.20);
        assertThat(out.getMatchedDisqualifiers()).contains("unrelated commercial product");
        assertThat(out.getMissingEvidence()).contains("no visible algebra work");
    }

    @Test
    void reviewSubmission_rejects_unrelated_screenshot_with_low_support_confidence() {
        when(results.findBySubmissionId(42L)).thenReturn(Optional.empty());
        when(quests.getQuest(15L)).thenReturn(new QuestClient.QuestContext(
                "Read chapter 5 and summarize",
                "Upload summary notes or a highlighted page from chapter 5.",
                List.of("chapter 5", "summary notes"),
                List.of("highlighted page", "written summary"),
                List.of("unrelated app screen", "commercial product"),
                0.70,
                "generic"
        ));
        when(proofs.getProofs(42L)).thenReturn(List.of(new ProofClient.ProofObject("proof/app.png", "image/png", "BASE64")));
        when(model.generate(any()))
                .thenReturn(new ModelClient.ModelResponse(
                        "{\"ocr_text\":[\"Notifications\",\"Settings\",\"Upgrade plan\"],\"quality\":\"HIGH\"}",
                        "qwen2.5vl:7b", false, null))
                .thenReturn(new ModelClient.ModelResponse(
                        "{\"visible_objects\":[\"phone app screen\",\"settings menu\"],\"visible_text\":[\"Notifications\",\"Settings\",\"Upgrade plan\"],\"scene_type\":\"application screenshot\",\"activity_clues\":[\"account settings\"],\"uncertainty_flags\":[]}",
                        "qwen2.5vl:7b", false, null))
                .thenReturn(new ModelClient.ModelResponse(
                        "{\"matched_claims\":[],\"missing_evidence\":[\"no chapter 5 page\",\"no summary notes\"],\"unrelated_evidence\":[\"settings screen\"],\"contradictions\":[\"The screenshot is unrelated to reading or summarizing.\"],\"disqualifier_hits\":[\"unrelated app screen\"],\"relevance_score\":0.04,\"support_score\":0.03,\"evidence_strength\":\"LOW\",\"notes\":[\"No visible reading evidence is present.\"]}",
                        "qwen2.5vl:7b", false, null));

        AiReviewResult out = service.reviewSubmission(new AiReviewService.SubmissionCreated(
                42L, 15L, "u1", "finished", Instant.parse("2026-05-01T10:00:00Z")
        ));

        assertThat(out.getRecommendation()).isEqualTo(AiReviewRecommendation.LIKELY_INVALID);
        assertThat(out.getConfidence()).isLessThan(0.25);
        assertThat(out.getSupportScore()).isLessThan(0.20);
        assertThat(out.getMissingEvidence()).contains("no chapter 5 page");
    }

    @Test
    void reviewSubmission_keeps_ambiguous_partial_evidence_unclear() {
        when(results.findBySubmissionId(43L)).thenReturn(Optional.empty());
        when(quests.getQuest(16L)).thenReturn(new QuestClient.QuestContext(
                "Read chapter 5 and summarize",
                "Upload summary notes or a highlighted page from chapter 5.",
                List.of("chapter 5", "summary notes"),
                List.of("highlighted page", "written summary"),
                List.of("unrelated app screen", "commercial product"),
                0.70,
                "generic"
        ));
        when(proofs.getProofs(43L)).thenReturn(List.of(new ProofClient.ProofObject("proof/blurred-notes.jpg", "image/jpeg", "BASE64")));
        when(model.generate(any()))
                .thenReturn(new ModelClient.ModelResponse(
                        "{\"ocr_text\":[\"chapter\",\"notes\"],\"quality\":\"LOW\"}",
                        "qwen2.5vl:7b", false, null))
                .thenReturn(new ModelClient.ModelResponse(
                        "{\"visible_objects\":[\"paper notes\"],\"visible_text\":[\"chapter\",\"notes\"],\"scene_type\":\"study notes photo\",\"activity_clues\":[\"handwritten notes\"],\"uncertainty_flags\":[\"text is partially unreadable\",\"chapter number is not clear\"]}",
                        "qwen2.5vl:7b", false, null))
                .thenReturn(new ModelClient.ModelResponse(
                        "{\"matched_claims\":[\"paper notes may relate to reading\"],\"missing_evidence\":[\"chapter 5 is not clearly visible\",\"summary content is not readable\"],\"unrelated_evidence\":[],\"contradictions\":[],\"disqualifier_hits\":[],\"relevance_score\":0.48,\"support_score\":0.42,\"evidence_strength\":\"MEDIUM\",\"notes\":[\"Some study notes are visible, but the exact chapter and summary cannot be verified.\"]}",
                        "qwen2.5vl:7b", false, null));

        AiReviewResult out = service.reviewSubmission(new AiReviewService.SubmissionCreated(
                43L, 16L, "u1", "I read it.", Instant.parse("2026-05-01T10:00:00Z")
        ));

        assertThat(out.getRecommendation()).isEqualTo(AiReviewRecommendation.UNCLEAR);
        assertThat(out.getConfidence()).isBetween(0.25, 0.55);
        assertThat(out.getMissingEvidence()).contains("chapter 5 is not clearly visible");
    }
}

package com.questify.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.questify.client.ProofClient;
import com.questify.client.QuestClient;
import com.questify.domain.AiReviewRecommendation;
import com.questify.domain.AiReviewResult;
import com.questify.provider.AiReviewPrompt;
import com.questify.provider.ModelClient;
import com.questify.repository.AiReviewResultRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AiReviewService {
    private final AiReviewResultRepository results;
    private final QuestClient quests;
    private final ProofClient proofs;
    private final ModelClient model;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${ai-review.model:llava:7b}")
    private String modelName;

    public AiReviewService(AiReviewResultRepository results, QuestClient quests, ProofClient proofs, ModelClient model) {
        this.results = results;
        this.quests = quests;
        this.proofs = proofs;
        this.model = model;
    }

    @Transactional
    public AiReviewResult reviewSubmission(SubmissionCreated event) {
        var existing = results.findBySubmissionId(event.submissionId()).orElse(null);
        if (existing != null) return existing;

        try {
            QuestClient.QuestContext quest = quests.getQuest(event.questId());
            List<ProofClient.ProofObject> proofObjects = event.proofKeys().isEmpty()
                    ? proofs.getProofs(event.submissionId())
                    : proofs.getProofsFromKeys(event.proofKeys());
            List<String> images = supportedImages(proofObjects);

            if (images.isEmpty()) {
                return save(event, AiReviewRecommendation.UNSUPPORTED_MEDIA, 0.0,
                        "No supported image proof was available for AI review.", false, null);
            }

            String prompt = """
                    Review this Questify submission as advisory evidence only.
                    Quest title: %s
                    Quest description: %s
                    Student comment: %s

                    Return JSON only:
                    {"recommendation":"LIKELY_VALID|UNCLEAR|LIKELY_INVALID","confidence":0.0-1.0,"reasons":["short reason"],"mediaSupported":true}
                    Do not claim certainty. If the image/comment do not clearly support the quest, use UNCLEAR or LIKELY_INVALID.
                    """.formatted(quest.title(), quest.description(), event.note() == null ? "" : event.note());

            String raw = model.generate(new AiReviewPrompt(prompt, images));
            ParsedReview parsed = parse(raw);
            return save(event, parsed.recommendation(), parsed.confidence(), String.join("\n", parsed.reasons()), true, raw);
        } catch (Exception e) {
            return save(event, AiReviewRecommendation.AI_FAILED, 0.0,
                    "AI review failed; manual review is required.", true, e.toString());
        }
    }

    @Transactional(readOnly = true)
    public AiReviewResult getForSubmission(Long submissionId) {
        return results.findBySubmissionId(submissionId).orElse(null);
    }

    private AiReviewResult save(SubmissionCreated event, AiReviewRecommendation recommendation, double confidence,
                                String reasons, boolean mediaSupported, String raw) {
        return results.save(AiReviewResult.builder()
                .submissionId(event.submissionId())
                .questId(event.questId())
                .userId(event.userId())
                .recommendation(recommendation)
                .confidence(Math.max(0.0, Math.min(1.0, confidence)))
                .model(modelName)
                .reasons(reasons == null || reasons.isBlank() ? "Manual review is required." : reasons)
                .rawOutput(raw)
                .mediaSupported(mediaSupported)
                .reviewedAt(Instant.now())
                .build());
    }

    private List<String> supportedImages(List<ProofClient.ProofObject> proofObjects) {
        List<String> images = new ArrayList<>();
        for (ProofClient.ProofObject proof : proofObjects == null ? List.<ProofClient.ProofObject>of() : proofObjects) {
            if (proof.base64() == null || proof.base64().isBlank()) continue;
            String contentType = proof.contentType() == null ? "" : proof.contentType().toLowerCase(Locale.ROOT);
            if (contentType.startsWith("image/")) images.add(proof.base64());
        }
        return images;
    }

    private ParsedReview parse(String raw) throws Exception {
        JsonNode node = mapper.readTree(extractJson(raw));
        AiReviewRecommendation recommendation = switch (node.path("recommendation").asText("UNCLEAR")) {
            case "LIKELY_VALID" -> AiReviewRecommendation.LIKELY_VALID;
            case "LIKELY_INVALID" -> AiReviewRecommendation.LIKELY_INVALID;
            default -> AiReviewRecommendation.UNCLEAR;
        };
        double confidence = node.path("confidence").asDouble(0.0);
        List<String> reasons = new ArrayList<>();
        JsonNode reasonNode = node.path("reasons");
        if (reasonNode.isArray()) {
            reasonNode.forEach(reason -> reasons.add(reason.asText()));
        }
        if (reasons.isEmpty()) reasons.add("Manual review is required.");
        return new ParsedReview(recommendation, confidence, reasons);
    }

    private static String extractJson(String raw) {
        if (raw == null) return "{}";
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) return raw.substring(start, end + 1);
        return raw;
    }

    private record ParsedReview(AiReviewRecommendation recommendation, double confidence, List<String> reasons) {}

    public record SubmissionCreated(Long submissionId, Long questId, String userId, String note, Instant submittedAt,
                                    List<String> proofKeys) {
        public SubmissionCreated(Long submissionId, Long questId, String userId, String note, Instant submittedAt) {
            this(submissionId, questId, userId, note, submittedAt, List.of());
        }
    }
}

package com.questify.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.questify.client.ProofClient;
import com.questify.client.QuestClient;
import com.questify.client.SubmissionClient;
import com.questify.domain.AiReviewAttempt;
import com.questify.domain.AiReviewRecommendation;
import com.questify.domain.AiReviewResult;
import com.questify.domain.AiReviewRunSource;
import com.questify.provider.AiReviewPrompt;
import com.questify.provider.ModelClient;
import com.questify.repository.AiReviewAttemptRepository;
import com.questify.repository.AiReviewResultRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class AiReviewService {
    private final AiReviewResultRepository results;
    private final AiReviewAttemptRepository attempts;
    private final QuestClient quests;
    private final SubmissionClient submissions;
    private final ProofClient proofs;
    private final ModelClient model;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${ai-review.model:llava:7b}")
    private String modelName;

    public AiReviewService(AiReviewResultRepository results,
                           AiReviewAttemptRepository attempts,
                           QuestClient quests,
                           SubmissionClient submissions,
                           ProofClient proofs,
                           ModelClient model) {
        this.results = results;
        this.attempts = attempts;
        this.quests = quests;
        this.submissions = submissions;
        this.proofs = proofs;
        this.model = model;
    }

    @Transactional
    public AiReviewResult reviewSubmission(SubmissionCreated event) {
        return runReview(event, AiReviewRunSource.KAFKA, "kafka-listener", false);
    }

    @Transactional
    public AiReviewResult rerunForSubmission(Long submissionId, AiReviewRunSource source, String triggeredBy) {
        var context = submissions.getSubmissionContext(submissionId);
        if (context == null || context.submissionId() == null || context.questId() == null || context.userId() == null) {
            throw new IllegalArgumentException("Submission context missing required fields for id=" + submissionId);
        }
        SubmissionCreated event = new SubmissionCreated(
                context.submissionId(),
                context.questId(),
                context.userId(),
                context.note(),
                context.submittedAt(),
                context.proofKeys()
        );
        return runReview(event, source, triggeredBy == null ? source.name().toLowerCase(Locale.ROOT) : triggeredBy, true);
    }

    private AiReviewResult runReview(SubmissionCreated event, AiReviewRunSource source, String triggeredBy, boolean force) {
        log.info("AI review run started submissionId={} source={} triggeredBy={} force={} proofKeys={}",
                event.submissionId(), source, triggeredBy, force,
                event.proofKeys() == null ? 0 : event.proofKeys().size());

        var existing = results.findBySubmissionId(event.submissionId()).orElse(null);
        if (!force && existing != null) {
            log.info("AI review skipped submissionId={} source={} reason=already_present recommendation={} confidence={}",
                    event.submissionId(), source, existing.getRecommendation(), existing.getConfidence());
            recordAttempt(event.submissionId(), source, triggeredBy, "SKIPPED_ALREADY_PRESENT",
                    existing.getRecommendation(), existing.getConfidence(), "Result already present");
            return existing;
        }

        try {
            QuestClient.QuestContext quest = quests.getQuest(event.questId());
            List<ProofClient.ProofObject> proofObjects = event.proofKeys().isEmpty()
                    ? proofs.getProofs(event.submissionId())
                    : proofs.getProofsFromKeys(event.proofKeys());
            List<String> images = supportedImages(proofObjects);
            log.info("AI review context prepared submissionId={} questId={} proofsFetched={} supportedImages={}",
                    event.submissionId(), event.questId(), proofObjects == null ? 0 : proofObjects.size(), images.size());

            if (images.isEmpty()) {
                log.warn("AI review unsupported media submissionId={} questId={} reason=no_supported_images",
                        event.submissionId(), event.questId());
                return saveAndRecord(existing, event, AiReviewRecommendation.UNSUPPORTED_MEDIA, 0.0,
                        "No supported image proof was available for AI review.", false, null,
                        source, triggeredBy, "UNSUPPORTED_MEDIA");
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
            log.info("AI review model response received submissionId={} rawPreview={}",
                    event.submissionId(), truncate(raw, 400));
            ParsedReview parsed = parse(raw);
            log.info("AI review parsed submissionId={} recommendation={} confidence={}",
                    event.submissionId(), parsed.recommendation(), parsed.confidence());
            return saveAndRecord(existing, event, parsed.recommendation(), parsed.confidence(),
                    String.join("\n", parsed.reasons()), true, raw, source, triggeredBy, "SUCCESS");
        } catch (Exception e) {
            log.error("AI review failed submissionId={} source={} triggeredBy={} error={}",
                    event.submissionId(), source, triggeredBy, e.toString(), e);
            return saveAndRecord(existing, event, AiReviewRecommendation.AI_FAILED, 0.0,
                    "AI review failed; manual review is required. " + truncate(e.getMessage(), 300), true, e.toString(),
                    source, triggeredBy, "FAILED");
        }
    }

    @Transactional(readOnly = true)
    public AiReviewResult getForSubmission(Long submissionId) {
        return results.findBySubmissionId(submissionId).orElse(null);
    }

    private AiReviewResult saveAndRecord(AiReviewResult existing,
                                         SubmissionCreated event,
                                         AiReviewRecommendation recommendation,
                                         double confidence,
                                         String reasons,
                                         boolean mediaSupported,
                                         String raw,
                                         AiReviewRunSource source,
                                         String triggeredBy,
                                         String outcome) {
        AiReviewResult target = existing == null ? new AiReviewResult() : existing;
        target.setSubmissionId(event.submissionId());
        target.setQuestId(event.questId());
        target.setUserId(event.userId());
        target.setRecommendation(recommendation);
        target.setConfidence(Math.max(0.0, Math.min(1.0, confidence)));
        target.setModel(modelName);
        target.setReasons(reasons == null || reasons.isBlank() ? "Manual review is required." : reasons);
        target.setRawOutput(raw);
        target.setMediaSupported(mediaSupported);
        target.setReviewedAt(Instant.now());
        AiReviewResult saved = results.save(target);

        recordAttempt(event.submissionId(), source, triggeredBy, outcome, recommendation, saved.getConfidence(), raw);
        return saved;
    }

    private void recordAttempt(Long submissionId,
                               AiReviewRunSource source,
                               String triggeredBy,
                               String outcome,
                               AiReviewRecommendation recommendation,
                               Double confidence,
                               String detail) {
        attempts.save(AiReviewAttempt.builder()
                .submissionId(submissionId)
                .runSource(source)
                .triggeredBy(triggeredBy)
                .outcome(outcome)
                .recommendation(recommendation)
                .confidence(confidence)
                .detail(truncate(detail, 4000))
                .reviewedAt(Instant.now())
                .build());
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
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

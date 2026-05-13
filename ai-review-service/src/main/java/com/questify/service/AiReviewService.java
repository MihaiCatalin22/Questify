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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
public class AiReviewService {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-z0-9]{3,}");
    private static final Set<String> STOPWORDS = Set.of(
            "this", "that", "with", "from", "into", "your", "have", "been", "were", "will", "then", "than",
            "there", "their", "about", "quest", "proof", "image", "student", "comment", "submission",
            "manual", "review", "show", "shows", "showing", "activity", "valid", "match", "matches",
            "relevant", "looks", "good", "appears", "photo", "screenshot"
    );
    private static final List<String> DEFAULT_DISQUALIFIERS = List.of(
            "game", "video game", "hud", "ui overlay", "menu screen", "meme", "cartoon", "cinematic"
    );

    private final AiReviewResultRepository results;
    private final AiReviewAttemptRepository attempts;
    private final QuestClient quests;
    private final SubmissionClient submissions;
    private final ProofClient proofs;
    private final ModelClient model;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ConcurrentHashMap<Long, ReentrantLock> submissionLocks = new ConcurrentHashMap<>();

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
        AiReviewResult existing = results.findBySubmissionId(submissionId).orElse(null);
        var context = submissions.getSubmissionContext(submissionId);
        if (context == null || context.submissionId() == null || context.questId() == null || context.userId() == null) {
            if (existing != null) {
                log.warn("AI review rerun context unavailable, returning existing result submissionId={} source={}",
                        submissionId, source);
                recordAttempt(submissionId, source, triggeredBy, "SKIPPED_CONTEXT_UNAVAILABLE",
                        existing.getRecommendation(), existing.getConfidence(), "Context unavailable, returned existing result");
                return existing;
            }
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
        return withSubmissionLock(event.submissionId(), () -> runReviewLocked(event, source, triggeredBy, force));
    }

    private AiReviewResult runReviewLocked(SubmissionCreated event, AiReviewRunSource source, String triggeredBy, boolean force) {
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
            List<ProofClient.ProofObject> proofObjects = event.proofKeys() == null || event.proofKeys().isEmpty()
                    ? proofs.getProofs(event.submissionId())
                    : proofs.getProofsFromKeys(event.proofKeys());
            List<String> images = supportedImages(proofObjects);
            if (images.isEmpty()) {
                return saveAndRecord(existing, event, BuildResult.unsupportedMedia(), source, triggeredBy, "UNSUPPORTED_MEDIA");
            }

            Policy policy = toPolicy(quest);

            ModelClient.ModelResponse ocrRaw = model.generate(new AiReviewPrompt(buildOcrPrompt(quest, event), images));
            OcrExtraction ocrExtraction = parseOcrExtraction(ocrRaw.content());

            ModelClient.ModelResponse observationRaw = model.generate(new AiReviewPrompt(
                    buildObservationPrompt(quest, event, ocrExtraction),
                    images
            ));
            Observation observation = parseObservation(observationRaw.content(), ocrExtraction);

            Scorecard scorecard = evaluate(policy, observation);
            BuildResult review = finalizeDecision(policy, scorecard, observation, ocrRaw, observationRaw);
            return saveAndRecord(existing, event, review, source, triggeredBy, "SUCCESS");
        } catch (Exception e) {
            log.error("AI review failed submissionId={} source={} triggeredBy={} error={}",
                    event.submissionId(), source, triggeredBy, e.toString(), e);
            BuildResult failed = BuildResult.failed("AI review failed; manual review is required. " + truncate(e.getMessage(), 300));
            return saveAndRecord(existing, event, failed, source, triggeredBy, "FAILED");
        }
    }

    private AiReviewResult withSubmissionLock(Long submissionId, java.util.function.Supplier<AiReviewResult> action) {
        ReentrantLock lock = submissionLocks.computeIfAbsent(submissionId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
            if (!lock.hasQueuedThreads()) {
                submissionLocks.remove(submissionId, lock);
            }
        }
    }

    @Transactional(readOnly = true)
    public AiReviewResult getForSubmission(Long submissionId) {
        return results.findBySubmissionId(submissionId).orElse(null);
    }

    private AiReviewResult saveAndRecord(AiReviewResult existing,
                                         SubmissionCreated event,
                                         BuildResult build,
                                         AiReviewRunSource source,
                                         String triggeredBy,
                                         String outcome) {
        AiReviewResult target = existing == null ? new AiReviewResult() : existing;
        target.setSubmissionId(event.submissionId());
        target.setQuestId(event.questId());
        target.setUserId(event.userId());
        target.setRecommendation(build.recommendation());
        target.setConfidence(clamp01(build.confidence()));
        target.setModel(build.modelUsed() == null ? "n/a" : build.modelUsed());
        target.setModelUsed(build.modelUsed());
        target.setFallbackUsed(build.fallbackUsed());
        target.setFallbackReason(truncate(build.fallbackReason(), 500));
        target.setReasons(toMultiline(build.reasons()));
        target.setDecisionNote(truncate(build.decisionNote(), 500));
        target.setGeneratedPolicy(build.generatedPolicy());
        target.setMatchedEvidence(toMultiline(build.matchedEvidence()));
        target.setMissingEvidence(toMultiline(build.missingEvidence()));
        target.setMatchedDisqualifiers(toMultiline(build.matchedDisqualifiers()));
        target.setOcrSnippets(toMultiline(build.ocrSnippets()));
        target.setObservedSignals(toMultiline(build.observedSignals()));
        target.setDecisionPath(truncate(build.decisionPath(), 500));
        target.setRawOutput(truncate(build.rawOutput(), 12000));
        target.setMediaSupported(build.mediaSupported());
        target.setReviewedAt(Instant.now());

        AiReviewResult saved;
        try {
            saved = results.saveAndFlush(target);
        } catch (DataIntegrityViolationException race) {
            log.warn("AI review upsert race hit submissionId={}, retrying as update", event.submissionId());
            AiReviewResult current = results.findBySubmissionId(event.submissionId()).orElseThrow(() -> race);
            current.setQuestId(target.getQuestId());
            current.setUserId(target.getUserId());
            current.setRecommendation(target.getRecommendation());
            current.setConfidence(target.getConfidence());
            current.setModel(target.getModel());
            current.setModelUsed(target.getModelUsed());
            current.setFallbackUsed(target.isFallbackUsed());
            current.setFallbackReason(target.getFallbackReason());
            current.setReasons(target.getReasons());
            current.setDecisionNote(target.getDecisionNote());
            current.setGeneratedPolicy(target.isGeneratedPolicy());
            current.setMatchedEvidence(target.getMatchedEvidence());
            current.setMissingEvidence(target.getMissingEvidence());
            current.setMatchedDisqualifiers(target.getMatchedDisqualifiers());
            current.setOcrSnippets(target.getOcrSnippets());
            current.setObservedSignals(target.getObservedSignals());
            current.setDecisionPath(target.getDecisionPath());
            current.setRawOutput(target.getRawOutput());
            current.setMediaSupported(target.isMediaSupported());
            current.setReviewedAt(target.getReviewedAt());
            saved = results.saveAndFlush(current);
        }

        recordAttempt(event.submissionId(), source, triggeredBy, outcome, saved.getRecommendation(), saved.getConfidence(), saved.getDecisionPath());
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

    private String buildOcrPrompt(QuestClient.QuestContext quest, SubmissionCreated event) {
        return """
                You are OCR extraction for proof review.
                Quest title: %s
                Quest description: %s
                Student comment: %s

                Return only JSON:
                {"ocr_text":["line 1","line 2"],"quality":"HIGH|MEDIUM|LOW"}

                Rules:
                - Only include text directly visible in the image.
                - Do not infer missing text.
                - If no readable text exists, return empty ocr_text.
                """.formatted(quest.title(), quest.description(), event.note() == null ? "" : event.note());
    }

    private String buildObservationPrompt(QuestClient.QuestContext quest, SubmissionCreated event, OcrExtraction ocr) {
        return """
                You are visual evidence extractor for Questify AI review.
                Quest title: %s
                Quest description: %s
                Student comment: %s
                OCR text extracted: %s

                Return only JSON:
                {
                  "visible_objects":["..."],
                  "visible_text":["..."],
                  "scene_type":"...",
                  "activity_clues":["..."],
                  "uncertainty_flags":["..."]
                }

                Rules:
                - Describe only directly observable evidence from image(s).
                - No recommendation and no confidence here.
                - If uncertain, populate uncertainty_flags.
                """.formatted(
                quest.title(),
                quest.description(),
                event.note() == null ? "" : event.note(),
                ocr.text().isEmpty() ? "[]" : ocr.text()
        );
    }

    private Policy toPolicy(QuestClient.QuestContext quest) {
        List<String> required = normalizeSignals(quest.requiredEvidence());
        List<String> optional = normalizeSignals(quest.optionalEvidence());
        List<String> disqualifiers = normalizeSignals(quest.disqualifiers());
        String taskType = quest.taskType() == null ? null : quest.taskType().trim();
        double minSupport = clamp01(quest.minSupportScore() <= 0.0 ? 0.7 : quest.minSupportScore());

        boolean generated = required.isEmpty() && optional.isEmpty() && disqualifiers.isEmpty();
        if (!generated) {
            return new Policy(required, optional, disqualifiers, minSupport, taskType, false);
        }

        Set<String> baseTokens = extractTokens(quest.title() + " " + quest.description());
        List<String> autoRequired = baseTokens.stream().limit(4).toList();
        List<String> autoOptional = baseTokens.stream().skip(4).limit(6).toList();
        List<String> autoDisqualifiers = new ArrayList<>(DEFAULT_DISQUALIFIERS);
        return new Policy(
                autoRequired,
                autoOptional,
                autoDisqualifiers,
                0.75,
                taskType == null || taskType.isBlank() ? "generic" : taskType,
                true
        );
    }

    private OcrExtraction parseOcrExtraction(String raw) {
        JsonNode node = parseJsonNode(raw);
        List<String> lines = readStringList(node.path("ocr_text"));
        String quality = node.path("quality").asText("LOW");
        return new OcrExtraction(lines, quality);
    }

    private Observation parseObservation(String raw, OcrExtraction ocr) {
        JsonNode node = parseJsonNode(raw);
        List<String> visibleObjects = readStringList(node.path("visible_objects"));
        List<String> visibleText = dedupeConcat(readStringList(node.path("visible_text")), ocr.text());
        String sceneType = node.path("scene_type").asText("");
        List<String> activityClues = readStringList(node.path("activity_clues"));
        List<String> uncertaintyFlags = readStringList(node.path("uncertainty_flags"));
        return new Observation(visibleObjects, visibleText, sceneType, activityClues, uncertaintyFlags);
    }

    private Scorecard evaluate(Policy policy, Observation observation) {
        List<String> signalParts = new ArrayList<>();
        signalParts.addAll(observation.visibleObjects());
        signalParts.addAll(observation.visibleText());
        signalParts.addAll(observation.activityClues());
        if (observation.sceneType() != null && !observation.sceneType().isBlank()) {
            signalParts.add(observation.sceneType());
        }
        String signalBlob = String.join(" ", signalParts).toLowerCase(Locale.ROOT);
        Set<String> signalTokens = extractTokens(signalBlob);

        List<String> matchedRequired = matchSignals(policy.requiredEvidence(), signalBlob, signalTokens);
        List<String> missingRequired = policy.requiredEvidence().stream()
                .filter(required -> !matchesSignal(signalBlob, signalTokens, required))
                .toList();
        List<String> matchedOptional = matchSignals(policy.optionalEvidence(), signalBlob, signalTokens);
        List<String> matchedDisqualifiers = matchSignals(policy.disqualifiers(), signalBlob, signalTokens);

        List<String> uncertainty = observation.uncertaintyFlags().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();

        double requiredRatio = policy.requiredEvidence().isEmpty()
                ? 0.0
                : ((double) matchedRequired.size() / (double) policy.requiredEvidence().size());
        double optionalRatio = policy.optionalEvidence().isEmpty()
                ? 0.0
                : ((double) matchedOptional.size() / (double) policy.optionalEvidence().size());
        double disqualifierPenalty = matchedDisqualifiers.isEmpty() ? 0.0 : Math.min(1.0, matchedDisqualifiers.size() * 0.35);
        double uncertaintyPenalty = uncertainty.isEmpty() ? 0.0 : Math.min(0.35, uncertainty.size() * 0.1);

        double supportScore = clamp01((requiredRatio * 0.75) + (optionalRatio * 0.25) - disqualifierPenalty - uncertaintyPenalty);
        return new Scorecard(
                matchedRequired,
                missingRequired,
                matchedOptional,
                matchedDisqualifiers,
                uncertainty,
                supportScore
        );
    }

    private BuildResult finalizeDecision(Policy policy,
                                         Scorecard scorecard,
                                         Observation observation,
                                         ModelClient.ModelResponse ocrRaw,
                                         ModelClient.ModelResponse obsRaw) {
        AiReviewRecommendation recommendation;
        String decisionPath;

        boolean hasDisqualifier = !scorecard.matchedDisqualifiers().isEmpty();
        boolean hasRequired = !policy.requiredEvidence().isEmpty();
        boolean requiredSatisfied = hasRequired && scorecard.missingRequired().isEmpty();
        boolean strongSupport = scorecard.supportScore() >= policy.minSupportScore();
        boolean uncertaintyHeavy = scorecard.uncertaintyFlags().size() >= 2;

        if (hasDisqualifier && scorecard.supportScore() < 0.8) {
            recommendation = AiReviewRecommendation.LIKELY_INVALID;
            decisionPath = "disqualifier_hit";
        } else if (requiredSatisfied && strongSupport && !uncertaintyHeavy) {
            recommendation = AiReviewRecommendation.LIKELY_VALID;
            decisionPath = "required_satisfied";
        } else if (hasRequired && scorecard.matchedRequired().isEmpty() && scorecard.supportScore() < 0.35) {
            recommendation = AiReviewRecommendation.LIKELY_INVALID;
            decisionPath = "required_missing";
        } else {
            recommendation = AiReviewRecommendation.UNCLEAR;
            decisionPath = "insufficient_evidence";
        }

        if (policy.generated() && recommendation == AiReviewRecommendation.LIKELY_VALID && scorecard.supportScore() < 0.9) {
            recommendation = AiReviewRecommendation.UNCLEAR;
            decisionPath = "generated_policy_conservative";
        }

        List<String> reasons = new ArrayList<>();
        reasons.add("Support score: %.2f".formatted(scorecard.supportScore()));
        if (!scorecard.matchedRequired().isEmpty()) reasons.add("Matched required: " + String.join(", ", scorecard.matchedRequired()));
        if (!scorecard.missingRequired().isEmpty()) reasons.add("Missing required: " + String.join(", ", scorecard.missingRequired()));
        if (!scorecard.matchedDisqualifiers().isEmpty()) reasons.add("Disqualifiers: " + String.join(", ", scorecard.matchedDisqualifiers()));
        if (reasons.size() > 4) reasons = reasons.subList(0, 4);

        String decisionNote = policy.generated()
                ? "Used generated verification policy from quest text; recommendation is conservative."
                : null;

        String rawCombined = "OCR_MODEL=" + ocrRaw.modelUsed() + "\nOCR_RAW=" + truncate(ocrRaw.content(), 4000) +
                "\nOBS_MODEL=" + obsRaw.modelUsed() + "\nOBS_RAW=" + truncate(obsRaw.content(), 6000);

        return new BuildResult(
                recommendation,
                scorecard.supportScore(),
                reasons,
                decisionNote,
                true,
                obsRaw.modelUsed(),
                ocrRaw.fallbackUsed() || obsRaw.fallbackUsed(),
                firstNonBlank(obsRaw.fallbackReason(), ocrRaw.fallbackReason()),
                policy.generated(),
                scorecard.matchedRequired(),
                scorecard.missingRequired(),
                scorecard.matchedDisqualifiers(),
                observation.visibleText().stream().limit(8).toList(),
                buildObservedSignals(observation),
                decisionPath,
                rawCombined
        );
    }

    private List<String> buildObservedSignals(Observation observation) {
        LinkedHashSet<String> signals = new LinkedHashSet<>();
        signals.addAll(normalizeSignals(observation.visibleObjects()));
        signals.addAll(normalizeSignals(observation.activityClues()));
        signals.addAll(normalizeSignals(observation.visibleText()));
        if (observation.sceneType() != null && !observation.sceneType().isBlank()) {
            signals.add(observation.sceneType().trim());
        }
        return signals.stream().limit(20).toList();
    }

    private static List<String> matchSignals(List<String> candidates, String signalBlob, Set<String> signalTokens) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        return candidates.stream()
                .filter(candidate -> matchesSignal(signalBlob, signalTokens, candidate))
                .distinct()
                .toList();
    }

    private static boolean matchesSignal(String signalBlob, Set<String> signalTokens, String signal) {
        if (signal == null || signal.isBlank()) return false;
        if (containsSignal(signalBlob, signal)) return true;

        Set<String> evidenceTokens = extractTokens(signal);
        if (evidenceTokens.isEmpty()) return false;
        long hits = evidenceTokens.stream().filter(signalTokens::contains).count();
        int requiredHits = requiredTokenHits(evidenceTokens.size());
        return hits >= requiredHits;
    }

    private static int requiredTokenHits(int tokenCount) {
        if (tokenCount <= 1) return 1;
        if (tokenCount <= 3) return tokenCount - 1;
        return Math.max(2, (int) Math.ceil(tokenCount * 0.6));
    }

    private static boolean containsSignal(String signalBlob, String signal) {
        if (signal == null || signal.isBlank()) return false;
        String normalized = signal.trim().toLowerCase(Locale.ROOT);
        return signalBlob.contains(normalized);
    }

    private static List<String> normalizeSignals(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) continue;
            String trimmed = value.trim().toLowerCase(Locale.ROOT);
            if (trimmed.length() < 2) continue;
            if (trimmed.length() > 120) trimmed = trimmed.substring(0, 120);
            normalized.add(trimmed);
        }
        return normalized.stream().toList();
    }

    private static Set<String> extractTokens(String text) {
        String source = text == null ? "" : text.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        Matcher matcher = TOKEN_PATTERN.matcher(source);
        while (matcher.find()) {
            String token = matcher.group();
            if (STOPWORDS.contains(token) || token.chars().allMatch(Character::isDigit)) continue;
            tokens.add(token);
        }
        return tokens.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .limit(10)
                .collect(Collectors.toCollection(LinkedHashSet::new));
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

    private JsonNode parseJsonNode(String raw) {
        try {
            return mapper.readTree(extractJson(raw));
        } catch (Exception ignored) {
            return mapper.createObjectNode();
        }
    }

    private static String extractJson(String raw) {
        if (raw == null) return "{}";
        String cleaned = raw.replace("```json", "").replace("```", "").trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) return cleaned.substring(start, end + 1);
        return cleaned;
    }

    private static List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<String> list = new ArrayList<>();
        node.forEach(value -> {
            String text = value.asText("");
            if (!text.isBlank()) list.add(text.trim());
        });
        return list;
    }

    private static List<String> dedupeConcat(List<String> first, List<String> second) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (first != null) merged.addAll(first);
        if (second != null) merged.addAll(second);
        return merged.stream().filter(value -> value != null && !value.isBlank()).toList();
    }

    private static String toMultiline(List<String> values) {
        if (values == null || values.isEmpty()) return null;
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(Collectors.joining("\n"));
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        return second;
    }

    private record Policy(
            List<String> requiredEvidence,
            List<String> optionalEvidence,
            List<String> disqualifiers,
            double minSupportScore,
            String taskType,
            boolean generated
    ) {}

    private record OcrExtraction(List<String> text, String quality) {}

    private record Observation(
            List<String> visibleObjects,
            List<String> visibleText,
            String sceneType,
            List<String> activityClues,
            List<String> uncertaintyFlags
    ) {}

    private record Scorecard(
            List<String> matchedRequired,
            List<String> missingRequired,
            List<String> matchedOptional,
            List<String> matchedDisqualifiers,
            List<String> uncertaintyFlags,
            double supportScore
    ) {}

    private record BuildResult(
            AiReviewRecommendation recommendation,
            double confidence,
            List<String> reasons,
            String decisionNote,
            boolean mediaSupported,
            String modelUsed,
            boolean fallbackUsed,
            String fallbackReason,
            boolean generatedPolicy,
            List<String> matchedEvidence,
            List<String> missingEvidence,
            List<String> matchedDisqualifiers,
            List<String> ocrSnippets,
            List<String> observedSignals,
            String decisionPath,
            String rawOutput
    ) {
        static BuildResult unsupportedMedia() {
            return new BuildResult(
                    AiReviewRecommendation.UNSUPPORTED_MEDIA,
                    0.0,
                    List.of("No supported image proof was available for AI review."),
                    null,
                    false,
                    null,
                    false,
                    null,
                    false,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    "unsupported_media",
                    null
            );
        }

        static BuildResult failed(String reason) {
            return new BuildResult(
                    AiReviewRecommendation.AI_FAILED,
                    0.0,
                    List.of(reason == null ? "AI review failed; manual review is required." : reason),
                    "Model/runtime failure; manual review required.",
                    true,
                    null,
                    false,
                    null,
                    false,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    "ai_failed",
                    reason
            );
        }
    }

    public record SubmissionCreated(Long submissionId, Long questId, String userId, String note, Instant submittedAt,
                                    List<String> proofKeys) {
        public SubmissionCreated(Long submissionId, Long questId, String userId, String note, Instant submittedAt) {
            this(submissionId, questId, userId, note, submittedAt, List.of());
        }
    }
}

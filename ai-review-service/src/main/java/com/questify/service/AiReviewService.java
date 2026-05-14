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
import com.questify.domain.AiReviewRunStatus;
import com.questify.provider.AiReviewPrompt;
import com.questify.provider.ModelClient;
import com.questify.repository.AiReviewAttemptRepository;
import com.questify.repository.AiReviewResultRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.core.task.TaskExecutor;
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
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class AiReviewService {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-z0-9]{3,}");
    private static final Set<String> STOPWORDS = Set.of(
            "this", "that", "with", "from", "into", "your", "have", "been", "were", "will", "then", "than",
            "there", "their", "about", "quest", "proof", "image", "student", "comment", "submission",
            "manual", "review", "show", "shows", "showing", "activity", "valid", "match", "matches",
            "relevant", "looks", "good", "appears", "photo", "screenshot",
            "key", "important", "most", "quick", "quickly", "simple", "basic", "memorize", "recall",
            "work", "worked", "steps", "step", "written", "result", "upload", "proofs"
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
    private final TaskExecutor taskExecutor;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ConcurrentHashMap<Long, ReentrantLock> submissionLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AtomicBoolean> inFlight = new ConcurrentHashMap<>();

    public AiReviewService(AiReviewResultRepository results,
                           AiReviewAttemptRepository attempts,
                           QuestClient quests,
                           SubmissionClient submissions,
                           ProofClient proofs,
                           ModelClient model,
                           TaskExecutor taskExecutor) {
        this.results = results;
        this.attempts = attempts;
        this.quests = quests;
        this.submissions = submissions;
        this.proofs = proofs;
        this.model = model;
        this.taskExecutor = taskExecutor;
    }

    @Transactional
    public AiReviewResult reviewSubmission(SubmissionCreated event) {
        return runReview(event, AiReviewRunSource.KAFKA, "kafka-listener", false);
    }

    @Transactional
    public AiReviewResult queueRerunForSubmission(Long submissionId, AiReviewRunSource source, String triggeredBy) {
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

        AiReviewResult queued = markQueued(event, source, triggeredBy == null ? source.name().toLowerCase(Locale.ROOT) : triggeredBy);
        startBackgroundRun(event, source, triggeredBy == null ? source.name().toLowerCase(Locale.ROOT) : triggeredBy);
        return queued;
    }

    @Transactional
    public AiReviewResult rerunForSubmission(Long submissionId, AiReviewRunSource source, String triggeredBy) {
        AiReviewResult queued = queueRerunForSubmission(submissionId, source, triggeredBy);
        return queued;
    }

    private AiReviewResult runReview(SubmissionCreated event, AiReviewRunSource source, String triggeredBy, boolean force) {
        return withSubmissionLock(event.submissionId(), () -> runReviewLocked(event, source, triggeredBy, force));
    }

    private void startBackgroundRun(SubmissionCreated event, AiReviewRunSource source, String triggeredBy) {
        AtomicBoolean marker = inFlight.computeIfAbsent(event.submissionId(), ignored -> new AtomicBoolean(false));
        if (!marker.compareAndSet(false, true)) {
            log.info("AI review rerun already in-flight submissionId={} source={}", event.submissionId(), source);
            return;
        }

        taskExecutor.execute(() -> {
            try {
                runReview(event, source, triggeredBy, true);
            } catch (Exception e) {
                log.error("AI review background execution crashed submissionId={} source={} err={}",
                        event.submissionId(), source, e.toString(), e);
            } finally {
                marker.set(false);
                inFlight.remove(event.submissionId(), marker);
            }
        });
    }

    private AiReviewResult markQueued(SubmissionCreated event, AiReviewRunSource source, String triggeredBy) {
        AiReviewResult existing = results.findBySubmissionId(event.submissionId()).orElse(null);
        AiReviewResult queued = existing == null ? new AiReviewResult() : existing;
        queued.setSubmissionId(event.submissionId());
        queued.setQuestId(event.questId());
        queued.setUserId(event.userId());
        queued.setStatus(AiReviewRunStatus.PENDING);
        queued.setRecommendation(existing == null ? AiReviewRecommendation.UNCLEAR : existing.getRecommendation());
        queued.setConfidence(existing == null ? 0.0 : clamp01(existing.getConfidence()));
        queued.setSupportScore(existing == null ? 0.0 : clamp01(existing.getSupportScore()));
        queued.setModel(existing == null ? "n/a" : firstNonBlank(existing.getModel(), "n/a"));
        queued.setModelUsed(existing == null ? null : existing.getModelUsed());
        queued.setFallbackUsed(existing != null && existing.isFallbackUsed());
        queued.setFallbackReason(existing == null ? null : truncate(existing.getFallbackReason(), 500));
        queued.setReasons(existing == null ? "AI review queued." : firstNonBlank(existing.getReasons(), "AI review queued."));
        queued.setDecisionNote("AI review queued and running in background.");
        queued.setGeneratedPolicy(existing != null && existing.isGeneratedPolicy());
        queued.setMatchedEvidence(existing == null ? null : existing.getMatchedEvidence());
        queued.setMissingEvidence(existing == null ? null : existing.getMissingEvidence());
        queued.setMatchedDisqualifiers(existing == null ? null : existing.getMatchedDisqualifiers());
        queued.setOcrSnippets(existing == null ? null : existing.getOcrSnippets());
        queued.setObservedSignals(existing == null ? null : existing.getObservedSignals());
        queued.setDecisionPath("queued");
        queued.setRawOutput(existing == null ? null : existing.getRawOutput());
        queued.setMediaSupported(existing == null || existing.isMediaSupported());
        queued.setReviewedAt(Instant.now());

        AiReviewResult saved = results.saveAndFlush(queued);
        recordAttempt(saved.getSubmissionId(), source, triggeredBy, "QUEUED", saved.getRecommendation(), saved.getConfidence(), "Queued for async rerun");
        return saved;
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

        markRunning(existing, event);
        existing = results.findBySubmissionId(event.submissionId()).orElse(existing);

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
            Set<String> questTokens = buildQuestTokens(quest, policy);

            ModelClient.ModelResponse ocrRaw = model.generate(new AiReviewPrompt(
                    buildOcrPrompt(quest, event),
                    images,
                    AiReviewPrompt.Stage.OCR
            ));
            OcrExtraction ocrExtraction = parseOcrExtraction(ocrRaw.content());

            ModelClient.ModelResponse observationRaw = model.generate(new AiReviewPrompt(
                    buildObservationPrompt(quest, event, ocrExtraction),
                    images,
                    AiReviewPrompt.Stage.OBSERVATION
            ));
            Observation observation = parseObservation(observationRaw.content(), ocrExtraction);
            ModelClient.ModelResponse claimRaw = model.generate(new AiReviewPrompt(
                    buildClaimCheckPrompt(quest, event, policy, observation),
                    List.of(),
                    AiReviewPrompt.Stage.CLAIM_CHECK
            ));
            ClaimCheck claimCheck = parseClaimCheck(claimRaw.content());

            Scorecard scorecard = evaluate(policy, observation, claimCheck, questTokens);
            BuildResult review = finalizeDecision(policy, scorecard, observation, claimCheck, ocrRaw, observationRaw, claimRaw);
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

    private void markRunning(AiReviewResult existing, SubmissionCreated event) {
        AiReviewResult target = existing == null ? new AiReviewResult() : existing;
        target.setSubmissionId(event.submissionId());
        target.setQuestId(event.questId());
        target.setUserId(event.userId());
        target.setStatus(AiReviewRunStatus.RUNNING);
        if (target.getRecommendation() == null) target.setRecommendation(AiReviewRecommendation.UNCLEAR);
        target.setConfidence(clamp01(target.getConfidence()));
        target.setSupportScore(clamp01(target.getSupportScore()));
        if (target.getModel() == null || target.getModel().isBlank()) target.setModel("n/a");
        if (target.getReasons() == null || target.getReasons().isBlank()) target.setReasons("AI review running.");
        target.setDecisionNote("AI review running in background.");
        target.setDecisionPath("running");
        target.setReviewedAt(Instant.now());
        results.saveAndFlush(target);
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
        target.setStatus(build.recommendation() == AiReviewRecommendation.AI_FAILED ? AiReviewRunStatus.FAILED : AiReviewRunStatus.COMPLETED);
        target.setRecommendation(build.recommendation());
        target.setConfidence(clamp01(build.confidence()));
        target.setSupportScore(clamp01(build.supportScore()));
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
            current.setStatus(target.getStatus());
            current.setRecommendation(target.getRecommendation());
            current.setConfidence(target.getConfidence());
            current.setSupportScore(target.getSupportScore());
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

    private String buildClaimCheckPrompt(QuestClient.QuestContext quest,
                                         SubmissionCreated event,
                                         Policy policy,
                                         Observation observation) {
        return """
                You are a contradiction and claim checker for proof review.
                Quest title: %s
                Quest description: %s
                Student comment: %s
                Required evidence: %s
                Optional evidence: %s
                Disqualifiers: %s

                Observed objects: %s
                Observed text: %s
                Scene type: %s
                Activity clues: %s
                Uncertainty flags: %s

                Return only JSON:
                {
                  "matched_claims":["..."],
                  "missing_evidence":["..."],
                  "unrelated_evidence":["..."],
                  "contradictions":["..."],
                  "disqualifier_hits":["..."],
                  "relevance_score":0.0,
                  "support_score":0.0,
                  "evidence_strength":"LOW|MEDIUM|HIGH",
                  "notes":["..."]
                }

                Scoring rules:
                - relevance_score is how specifically the visible evidence relates to this quest, from 0 to 1.
                - support_score is how strongly the visible evidence supports completion of this quest, from 0 to 1.
                - The student comment can explain intent, but it cannot prove completion without visible evidence.
                - If the image is an unrelated object, unrelated product, unrelated app screen, meme, or generic screenshot, use low scores.
                - If the visible work/output is clearly tied to the requested task, use high scores even when OCR is imperfect.
                - Do not mark disqualifier_hits unless the disqualifier is visibly present in the image evidence.
                - Optional evidence can increase support, but missing optional cues should not force low support when topic relevance is clearly strong.
                - Keep all evidence strings concrete and directly observable.
                """.formatted(
                quest.title(),
                quest.description(),
                event.note() == null ? "" : event.note(),
                policy.requiredEvidence(),
                policy.optionalEvidence(),
                policy.disqualifiers(),
                observation.visibleObjects(),
                observation.visibleText(),
                observation.sceneType() == null ? "" : observation.sceneType(),
                observation.activityClues(),
                observation.uncertaintyFlags()
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

        List<String> seedTokens = extractTokens(quest.title() + " " + quest.description()).stream().limit(2).toList();
        String first = seedTokens.isEmpty() ? "task" : seedTokens.get(0);
        String second = seedTokens.size() >= 2 ? seedTokens.get(1) : first;
        List<String> autoRequired = List.of(first, second);
        List<String> autoOptional = List.of("worked steps", "written notes", "result screenshot");
        List<String> autoDisqualifiers = new ArrayList<>(DEFAULT_DISQUALIFIERS);
        return new Policy(
                autoRequired,
                autoOptional,
                autoDisqualifiers,
                0.7,
                taskType == null || taskType.isBlank() ? "generic" : taskType,
                true
        );
    }

    private Set<String> buildQuestTokens(QuestClient.QuestContext quest, Policy policy) {
        String policyText = String.join(" ", dedupeConcat(
                dedupeConcat(policy.requiredEvidence(), policy.optionalEvidence()),
                policy.disqualifiers()
        ));
        return extractAllTokens((quest.title() == null ? "" : quest.title()) + " " +
                (quest.description() == null ? "" : quest.description()) + " " + policyText, 40);
    }

    private OcrExtraction parseOcrExtraction(String raw) {
        JsonNode node = parseJsonNode(raw);
        List<String> lines = readStringList(node.path("ocr_text"));
        if (lines.isEmpty()) {
            lines = fallbackLinesFromRaw(raw);
        }
        String quality = node.path("quality").asText("LOW");
        if ((quality == null || quality.isBlank()) && !lines.isEmpty()) {
            quality = "MEDIUM";
        }
        return new OcrExtraction(lines, quality);
    }

    private Observation parseObservation(String raw, OcrExtraction ocr) {
        JsonNode node = parseJsonNode(raw);
        List<String> visibleObjects = readStringList(node.path("visible_objects"));
        List<String> visibleText = dedupeConcat(readStringList(node.path("visible_text")), ocr.text());
        String sceneType = node.path("scene_type").asText("");
        List<String> activityClues = readStringList(node.path("activity_clues"));
        List<String> uncertaintyFlags = readStringList(node.path("uncertainty_flags"));
        if (visibleObjects.isEmpty() && visibleText.isEmpty() && activityClues.isEmpty()) {
            List<String> fallback = fallbackLinesFromRaw(raw);
            visibleText = dedupeConcat(visibleText, fallback);
            if (!fallback.isEmpty()) {
                activityClues = dedupeConcat(activityClues, List.of("textual study evidence"));
            }
        }
        return new Observation(visibleObjects, visibleText, sceneType, activityClues, uncertaintyFlags);
    }

    private ClaimCheck parseClaimCheck(String raw) {
        JsonNode node = parseJsonNode(raw);
        List<String> matchedClaims = readStringList(node.path("matched_claims"));
        List<String> missingEvidence = readStringList(node.path("missing_evidence"));
        List<String> unrelatedEvidence = readStringList(node.path("unrelated_evidence"));
        List<String> contradictions = readStringList(node.path("contradictions"));
        List<String> disqualifierHits = readStringList(node.path("disqualifier_hits"));
        List<String> notes = readStringList(node.path("notes"));
        double relevanceScore = parseOptionalScore(node.path("relevance_score"));
        double supportScore = parseOptionalScore(node.path("support_score"));
        String evidenceStrength = node.path("evidence_strength").asText("LOW");
        if (evidenceStrength == null || evidenceStrength.isBlank()) {
            evidenceStrength = "LOW";
        }
        return new ClaimCheck(
                matchedClaims,
                missingEvidence,
                unrelatedEvidence,
                contradictions,
                disqualifierHits,
                notes,
                evidenceStrength.trim().toUpperCase(Locale.ROOT),
                relevanceScore,
                supportScore
        );
    }

    private Scorecard evaluate(Policy policy, Observation observation, ClaimCheck claimCheck, Set<String> questTokens) {
        List<String> signalParts = new ArrayList<>();
        signalParts.addAll(observation.visibleObjects());
        signalParts.addAll(observation.visibleText());
        signalParts.addAll(observation.activityClues());
        if (observation.sceneType() != null && !observation.sceneType().isBlank()) {
            signalParts.add(observation.sceneType());
        }
        String signalBlob = String.join(" ", signalParts).toLowerCase(Locale.ROOT);
        Set<String> signalTokens = extractTokens(signalBlob);
        Set<String> relevanceSignalTokens = extractAllTokens(
                signalBlob + " " + String.join(" ", claimCheck.matchedClaims()) + " " + String.join(" ", claimCheck.notes()),
                40
        );

        List<String> matchedRequiredObserved = matchSignals(policy.requiredEvidence(), signalBlob, signalTokens);
        List<String> matchedRequiredFromClaims = matchSignals(
                policy.requiredEvidence(),
                String.join(" ", claimCheck.matchedClaims()),
                extractTokens(String.join(" ", claimCheck.matchedClaims()))
        );
        List<String> matchedRequired = dedupeConcat(matchedRequiredObserved, matchedRequiredFromClaims);
        Set<String> matchedRequiredSet = new LinkedHashSet<>(matchedRequired);
        List<String> missingRequired = policy.requiredEvidence().stream()
                .filter(required -> !matchedRequiredSet.contains(required))
                .toList();
        List<String> matchedOptional = matchSignals(policy.optionalEvidence(), signalBlob, signalTokens);
        List<String> matchedDisqualifiersObserved = matchSignals(policy.disqualifiers(), signalBlob, signalTokens);
        List<String> matchedDisqualifiersModel = matchSignals(
                policy.disqualifiers(),
                String.join(" ", claimCheck.disqualifierHits()),
                extractTokens(String.join(" ", claimCheck.disqualifierHits()))
        );
        List<String> matchedDisqualifiersModelCorroborated = matchedDisqualifiersModel.stream()
                .filter(disqualifier -> matchesSignal(signalBlob, signalTokens, disqualifier))
                .toList();
        List<String> matchedDisqualifiers = dedupeConcat(matchedDisqualifiersObserved, matchedDisqualifiersModelCorroborated);

        double requiredRatio = policy.requiredEvidence().isEmpty()
                ? 0.0
                : ((double) matchedRequired.size() / (double) policy.requiredEvidence().size());
        double optionalRatio = policy.optionalEvidence().isEmpty()
                ? 0.0
                : ((double) matchedOptional.size() / (double) policy.optionalEvidence().size());

        List<String> modelMissingEvidence = normalizeSignals(claimCheck.missingEvidence());
        List<String> missingEvidence = requiredRatio < 0.65
                ? dedupeConcat(missingRequired, modelMissingEvidence)
                : missingRequired;
        List<String> unrelatedEvidence = corroboratedModelSignals(
                normalizeSignals(claimCheck.unrelatedEvidence()),
                signalBlob,
                signalTokens
        );
        List<String> contradictions = corroboratedModelSignals(
                normalizeSignals(claimCheck.contradictions()),
                signalBlob,
                signalTokens
        );

        List<String> uncertainty = observation.uncertaintyFlags().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();

        double disqualifierPenalty = matchedDisqualifiers.isEmpty() ? 0.0 : Math.min(1.0, matchedDisqualifiers.size() * 0.30);
        double contradictionPenalty = contradictions.isEmpty() ? 0.0 : Math.min(0.6, contradictions.size() * 0.18);
        double unrelatedPenalty = unrelatedEvidence.isEmpty() ? 0.0 : Math.min(0.65, unrelatedEvidence.size() * 0.20);
        double uncertaintyPenalty = uncertainty.isEmpty() ? 0.0 : Math.min(0.30, uncertainty.size() * 0.08);
        double strengthBonus = switch (claimCheck.evidenceStrength()) {
            case "HIGH" -> 0.08;
            case "MEDIUM" -> 0.03;
            default -> 0.0;
        };
        double claimMatchBonus = claimCheck.matchedClaims().isEmpty() ? 0.0 : 0.06;
        double tokenQuestRelevance = overlapRatio(questTokens, relevanceSignalTokens);
        double questRelevance = claimCheck.relevanceScore() >= 0.0
                ? clamp01((claimCheck.relevanceScore() * 0.78) + (tokenQuestRelevance * 0.22))
                : tokenQuestRelevance;

        double contextRelevanceScore = clamp01((requiredRatio * 0.58) + (questRelevance * 0.32) + strengthBonus + claimMatchBonus
                - disqualifierPenalty - contradictionPenalty - uncertaintyPenalty - unrelatedPenalty);
        double completionCueScore = optionalRatio;
        double fallbackSupport = clamp01((contextRelevanceScore * 0.88) + (completionCueScore * 0.12));
        double supportScore = claimCheck.supportScore() >= 0.0
                ? clamp01((claimCheck.supportScore() * 0.45) + (fallbackSupport * 0.40) + (questRelevance * 0.15))
                : fallbackSupport;
        boolean highContextMatch = requiredRatio >= 0.70 && questRelevance >= 0.58
                && matchedDisqualifiers.isEmpty()
                && unrelatedEvidence.isEmpty()
                && contradictions.isEmpty();
        if (highContextMatch) {
            supportScore = Math.max(supportScore, clamp01((contextRelevanceScore * 0.90) + (completionCueScore * 0.10)));
        }
        return new Scorecard(
                matchedRequired,
                missingRequired,
                missingEvidence,
                matchedOptional,
                matchedDisqualifiers,
                unrelatedEvidence,
                uncertainty,
                contradictions,
                questRelevance,
                requiredRatio,
                contextRelevanceScore,
                completionCueScore,
                supportScore
        );
    }

    private BuildResult finalizeDecision(Policy policy,
                                         Scorecard scorecard,
                                         Observation observation,
                                         ClaimCheck claimCheck,
                                         ModelClient.ModelResponse ocrRaw,
                                         ModelClient.ModelResponse obsRaw,
                                         ModelClient.ModelResponse claimRaw) {
        AiReviewRecommendation recommendation;
        String decisionPath;
        double confidence;

        boolean hasDisqualifier = !scorecard.matchedDisqualifiers().isEmpty();
        boolean hasUnrelatedEvidence = !scorecard.unrelatedEvidence().isEmpty();
        boolean hasRequired = !policy.requiredEvidence().isEmpty();
        boolean requiredMatchedSome = !scorecard.matchedRequired().isEmpty();
        boolean requiredSatisfied = hasRequired && scorecard.missingRequired().isEmpty();
        double validSupportThreshold = Math.max(0.50, policy.minSupportScore() - 0.20);
        boolean strongSupport = scorecard.supportScore() >= validSupportThreshold;
        boolean uncertaintyHeavy = scorecard.uncertaintyFlags().size() >= 2;
        boolean hasContradiction = !scorecard.contradictions().isEmpty();
        boolean corroboratedNegative = hasDisqualifier || hasUnrelatedEvidence || hasContradiction;
        boolean contradictionsStrong = scorecard.contradictions().size() >= 2;
        boolean modelScoredRelevance = claimCheck.relevanceScore() >= 0.0;
        double lowRelevanceThreshold = modelScoredRelevance ? 0.25 : 0.18;
        double strongRelevanceThreshold = modelScoredRelevance ? 0.54 : 0.30;
        boolean lowQuestRelevance = scorecard.questRelevance() < lowRelevanceThreshold;
        boolean strongQuestRelevance = scorecard.questRelevance() >= strongRelevanceThreshold;
        boolean contextStrong = scorecard.contextRelevanceScore() >= 0.62
                || (strongQuestRelevance && scorecard.requiredCoverage() >= 0.60);
        boolean contextStrongWithoutKeywordHit = scorecard.contextRelevanceScore() >= 0.70
                && scorecard.questRelevance() >= 0.62;
        boolean lowSupport = scorecard.supportScore() < 0.35;
        boolean veryLowSupport = scorecard.supportScore() < 0.20;
        boolean hasObservedEvidence = !observation.visibleObjects().isEmpty()
                || !observation.visibleText().isEmpty()
                || !observation.activityClues().isEmpty();

        if (corroboratedNegative && lowSupport
                && (lowQuestRelevance || scorecard.contextRelevanceScore() < 0.45)) {
            recommendation = AiReviewRecommendation.LIKELY_INVALID;
            decisionPath = "corroborated_negative_low_context";
        } else if (hasRequired && !requiredMatchedSome && lowSupport && lowQuestRelevance) {
            recommendation = AiReviewRecommendation.LIKELY_INVALID;
            decisionPath = "required_missing";
        } else if (contextStrong && strongQuestRelevance
                && !corroboratedNegative
                && hasObservedEvidence
                && (!hasRequired || requiredMatchedSome || contextStrongWithoutKeywordHit)
                && !uncertaintyHeavy) {
            recommendation = AiReviewRecommendation.LIKELY_VALID;
            decisionPath = "context_match";
        } else if (strongSupport && strongQuestRelevance
                && !corroboratedNegative
                && hasObservedEvidence
                && !uncertaintyHeavy) {
            recommendation = AiReviewRecommendation.LIKELY_VALID;
            decisionPath = "semantic_support_satisfied";
        } else if (requiredSatisfied && strongSupport && !uncertaintyHeavy && !corroboratedNegative && !contradictionsStrong && strongQuestRelevance) {
            recommendation = AiReviewRecommendation.LIKELY_VALID;
            decisionPath = "required_satisfied";
        } else if (corroboratedNegative && scorecard.supportScore() < 0.55) {
            recommendation = AiReviewRecommendation.LIKELY_INVALID;
            decisionPath = hasDisqualifier ? "disqualifier_hit" : (hasContradiction ? "contradiction_hit" : "unrelated_evidence");
        } else if (hasObservedEvidence && veryLowSupport && lowQuestRelevance) {
            recommendation = AiReviewRecommendation.LIKELY_INVALID;
            decisionPath = "low_relevance";
        } else {
            recommendation = AiReviewRecommendation.UNCLEAR;
            decisionPath = policy.generated() ? "generated_policy_insufficient_evidence" : "insufficient_evidence";
        }
        confidence = evidenceConfidence(recommendation, scorecard.supportScore());

        List<String> reasons = new ArrayList<>();
        reasons.add("Evidence support: %.2f".formatted(scorecard.supportScore()));
        reasons.add("Quest relevance: %.2f".formatted(scorecard.questRelevance()));
        reasons.add("Context relevance: %.2f".formatted(scorecard.contextRelevanceScore()));
        if (!scorecard.matchedRequired().isEmpty()) reasons.add("Matched required: " + String.join(", ", scorecard.matchedRequired()));
        if (!scorecard.missingEvidence().isEmpty()) reasons.add("Missing evidence: " + String.join(", ", scorecard.missingEvidence()));
        if (!scorecard.matchedOptional().isEmpty()) reasons.add("Matched optional: " + String.join(", ", scorecard.matchedOptional()));
        if (!scorecard.matchedDisqualifiers().isEmpty()) reasons.add("Disqualifiers: " + String.join(", ", scorecard.matchedDisqualifiers()));
        if (!scorecard.unrelatedEvidence().isEmpty()) reasons.add("Unrelated evidence: " + String.join(", ", scorecard.unrelatedEvidence()));
        if (!scorecard.contradictions().isEmpty()) reasons.add("Contradictions: " + String.join(", ", scorecard.contradictions()));
        if (reasons.size() > 4) reasons = reasons.subList(0, 4);

        String decisionNote = policy.generated()
                ? "Used generated verification policy from quest text; recommendation is conservative."
                : null;

        String rawCombined = "OCR_MODEL=" + ocrRaw.modelUsed() + "\nOCR_RAW=" + truncate(ocrRaw.content(), 4000) +
                "\nOBS_MODEL=" + obsRaw.modelUsed() + "\nOBS_RAW=" + truncate(obsRaw.content(), 6000) +
                "\nCLAIM_MODEL=" + claimRaw.modelUsed() + "\nCLAIM_RAW=" + truncate(claimRaw.content(), 4000);

        return new BuildResult(
                recommendation,
                confidence,
                scorecard.supportScore(),
                reasons,
                decisionNote,
                true,
                firstNonBlank(claimRaw.modelUsed(), obsRaw.modelUsed()),
                ocrRaw.fallbackUsed() || obsRaw.fallbackUsed() || claimRaw.fallbackUsed(),
                firstNonBlank(claimRaw.fallbackReason(), firstNonBlank(obsRaw.fallbackReason(), ocrRaw.fallbackReason())),
                policy.generated(),
                dedupeConcat(scorecard.matchedRequired(), normalizeSignals(claimCheck.matchedClaims())),
                scorecard.missingEvidence(),
                dedupeConcat(scorecard.matchedDisqualifiers(), scorecard.unrelatedEvidence()),
                observation.visibleText().stream().limit(8).toList(),
                buildObservedSignals(observation, claimCheck),
                decisionPath,
                rawCombined
        );
    }

    private List<String> buildObservedSignals(Observation observation, ClaimCheck claimCheck) {
        LinkedHashSet<String> signals = new LinkedHashSet<>();
        signals.addAll(normalizeSignals(observation.visibleObjects()));
        signals.addAll(normalizeSignals(observation.activityClues()));
        signals.addAll(normalizeSignals(observation.visibleText()));
        signals.addAll(normalizeSignals(claimCheck.matchedClaims()));
        signals.addAll(normalizeSignals(claimCheck.notes()));
        if (observation.sceneType() != null && !observation.sceneType().isBlank()) {
            signals.add(observation.sceneType().trim());
        }
        return signals.stream().limit(20).toList();
    }

    private static double evidenceConfidence(AiReviewRecommendation recommendation, double supportScore) {
        double score = clamp01(supportScore);
        return switch (recommendation) {
            case LIKELY_VALID -> score;
            case LIKELY_INVALID -> Math.min(score, 0.35);
            case UNCLEAR -> clamp01(Math.min(0.55, Math.max(0.25, score)));
            case UNSUPPORTED_MEDIA, AI_FAILED -> 0.0;
        };
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

    private static List<String> corroboratedModelSignals(List<String> modelSignals,
                                                         String signalBlob,
                                                         Set<String> signalTokens) {
        if (modelSignals == null || modelSignals.isEmpty()) return List.of();
        return modelSignals.stream()
                .filter(value -> {
                    if (value == null || value.isBlank()) return false;
                    if (matchesSignal(signalBlob, signalTokens, value)) return true;
                    Set<String> narrativeTokens = extractAllTokens(value, 20);
                    if (narrativeTokens.isEmpty()) return false;
                    long hits = narrativeTokens.stream().filter(signalTokens::contains).count();
                    int minHits = Math.min(2, narrativeTokens.size());
                    return hits >= minHits;
                })
                .distinct()
                .toList();
    }

    private static double overlapRatio(Set<String> questTokens, Set<String> signalTokens) {
        if (questTokens == null || questTokens.isEmpty() || signalTokens == null || signalTokens.isEmpty()) {
            return 0.0;
        }
        long hits = questTokens.stream().filter(signalTokens::contains).count();
        return clamp01((double) hits / (double) questTokens.size());
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

    private static Set<String> extractAllTokens(String text, int limit) {
        String source = text == null ? "" : text.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        Matcher matcher = TOKEN_PATTERN.matcher(source);
        while (matcher.find()) {
            String token = matcher.group();
            if (STOPWORDS.contains(token) || token.chars().allMatch(Character::isDigit)) continue;
            tokens.add(token);
            if (tokens.size() >= Math.max(1, limit)) break;
        }
        return tokens;
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

    private static double parseOptionalScore(JsonNode scoreNode) {
        if (scoreNode == null || scoreNode.isMissingNode() || scoreNode.isNull()) return -1.0;
        if (scoreNode.isNumber()) return clamp01(scoreNode.asDouble(-1.0));
        if (scoreNode.isTextual()) {
            Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(scoreNode.asText());
            if (matcher.find()) {
                double value = Double.parseDouble(matcher.group(1));
                return clamp01(value > 1.0 ? value / 100.0 : value);
            }
        }
        return -1.0;
    }

    private static String extractJson(String raw) {
        if (raw == null) return "{}";
        String cleaned = raw.replace("```json", "").replace("```", "").trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) return cleaned.substring(start, end + 1);
        return cleaned;
    }

    private static List<String> fallbackLinesFromRaw(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        String cleaned = raw
                .replace("```json", "")
                .replace("```", "")
                .replace("{", " ")
                .replace("}", " ")
                .replace("\"", " ")
                .replace("[", " ")
                .replace("]", " ");
        return List.of(cleaned.split("\\r?\\n"))
                .stream()
                .map(String::trim)
                .filter(line -> line.length() >= 3)
                .filter(line -> !line.startsWith(":"))
                .filter(line -> !line.equalsIgnoreCase("null"))
                .limit(8)
                .toList();
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
            List<String> missingEvidence,
            List<String> matchedOptional,
            List<String> matchedDisqualifiers,
            List<String> unrelatedEvidence,
            List<String> uncertaintyFlags,
            List<String> contradictions,
            double questRelevance,
            double requiredCoverage,
            double contextRelevanceScore,
            double completionCueScore,
            double supportScore
    ) {}

    private record ClaimCheck(
            List<String> matchedClaims,
            List<String> missingEvidence,
            List<String> unrelatedEvidence,
            List<String> contradictions,
            List<String> disqualifierHits,
            List<String> notes,
            String evidenceStrength,
            double relevanceScore,
            double supportScore
    ) {}

    private record BuildResult(
            AiReviewRecommendation recommendation,
            double confidence,
            double supportScore,
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

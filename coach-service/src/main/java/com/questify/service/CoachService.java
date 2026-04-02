package com.questify.service;

import com.questify.config.CoachProperties;
import com.questify.dto.CoachDtos.CoachSuggestionsReq;
import com.questify.dto.CoachDtos.CoachSuggestionsRes;
import com.questify.provider.GenerationOptions;
import com.questify.provider.GenerationPrompt;
import com.questify.provider.ModelClient;
import com.questify.provider.ModelClientException;
import com.questify.provider.ModelTimeoutException;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class CoachService {

    private static final Logger log = LoggerFactory.getLogger(CoachService.class);

    private final CoachContextService coachContextService;
    private final PromptBuilder promptBuilder;
    private final PromptAssets promptAssets;
    private final ModelClient modelClient;
    private final OutputValidator outputValidator;
    private final RetryHandler retryHandler;
    private final FallbackFactory fallbackFactory;
    private final MetricsRecorder metricsRecorder;
    private final CoachProperties properties;
    private final Clock clock;

    public CoachService(CoachContextService coachContextService,
                        PromptBuilder promptBuilder,
                        PromptAssets promptAssets,
                        ModelClient modelClient,
                        OutputValidator outputValidator,
                        RetryHandler retryHandler,
                        FallbackFactory fallbackFactory,
                        MetricsRecorder metricsRecorder,
                        CoachProperties properties,
                        Clock clock) {
        this.coachContextService = coachContextService;
        this.promptBuilder = promptBuilder;
        this.promptAssets = promptAssets;
        this.modelClient = modelClient;
        this.outputValidator = outputValidator;
        this.retryHandler = retryHandler;
        this.fallbackFactory = fallbackFactory;
        this.metricsRecorder = metricsRecorder;
        this.properties = properties;
        this.clock = clock;
    }

    public CoachSuggestionsRes generateSuggestions(String userId, CoachSuggestionsReq request) {
        Timer.Sample sample = metricsRecorder.startRequest();
        try {
            var context = coachContextService.loadContext(userId, request.resolvedIncludeRecentHistory());
            Instant generatedAt = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
            GenerationOptions options = new GenerationOptions(
                    properties.getModel(),
                    Duration.ofMillis(properties.getTimeoutMs()),
                    properties.getMaxOutputTokens(),
                    properties.getTemperature(),
                    promptAssets.schemaNode()
            );
            GenerationPrompt primaryPrompt = promptBuilder.buildPrimaryPrompt(
                    context,
                    request.resolvedMode()
            );

            try {
                var validated = outputValidator.validateSuccessPayload(generate(primaryPrompt, options), generatedAt);
                metricsRecorder.recordSuccess(sample);
                log.info("Coach suggestions generated runtime={} model={} outcome=success", properties.normalizedRuntime(), properties.getModel());
                return validated;
            } catch (ModelOutputValidationException validationFailure) {
                metricsRecorder.recordValidationFailure(validationFailure.category());
                if (retryHandler.shouldRetry(1, validationFailure)) {
                    metricsRecorder.recordRetry();
                    try {
                        GenerationPrompt repairPrompt = promptBuilder.buildRepairPrompt(
                                primaryPrompt,
                                validationFailure.rawOutput(),
                                validationFailure.errors()
                        );
                        var repaired = outputValidator.validateSuccessPayload(generate(repairPrompt, options), generatedAt);
                        metricsRecorder.recordSuccess(sample);
                        log.info("Coach suggestions generated runtime={} model={} outcome=repaired", properties.normalizedRuntime(), properties.getModel());
                        return repaired;
                    } catch (ModelOutputValidationException repairFailure) {
                        metricsRecorder.recordValidationFailure(repairFailure.category());
                        return fallback(sample, "invalid_after_retry");
                    } catch (ModelTimeoutException timeoutFailure) {
                        metricsRecorder.recordTimeout();
                        return fallback(sample, "timeout");
                    } catch (ModelClientException clientFailure) {
                        return fallback(sample, "runtime_failure");
                    }
                }
                return fallback(sample, "invalid_output");
            } catch (ModelTimeoutException timeoutFailure) {
                metricsRecorder.recordTimeout();
                return fallback(sample, "timeout");
            } catch (ModelClientException clientFailure) {
                return fallback(sample, "runtime_failure");
            }
        } catch (AiCoachOptInRequiredException ex) {
            metricsRecorder.recordRejected(sample);
            throw ex;
        } catch (RuntimeException ex) {
            metricsRecorder.recordError(sample);
            throw ex;
        }
    }

    private String generate(GenerationPrompt prompt, GenerationOptions options) {
        String rawOutput = modelClient.generate(prompt, options);
        if (properties.isDebugLogging() && log.isDebugEnabled()) {
            log.debug("Coach model output hash={} chars={}", sha256(rawOutput), rawOutput.length());
        }
        return rawOutput;
    }

    private CoachSuggestionsRes fallback(Timer.Sample sample, String reason) {
        metricsRecorder.recordFallback(sample, reason);
        log.warn("Coach suggestions fallback runtime={} model={} reason={}", properties.normalizedRuntime(), properties.getModel(), reason);
        return fallbackFactory.create();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}

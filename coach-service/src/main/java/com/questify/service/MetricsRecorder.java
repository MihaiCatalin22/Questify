package com.questify.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class MetricsRecorder {

    private final MeterRegistry registry;
    private final Counter requests;
    private final Counter success;
    private final Counter retries;
    private final Counter timeouts;

    public MetricsRecorder(MeterRegistry registry) {
        this.registry = registry;
        this.requests = registry.counter("coach_requests");
        this.success = registry.counter("coach_success");
        this.retries = registry.counter("coach_retry");
        this.timeouts = registry.counter("coach_timeouts");
    }

    public Timer.Sample startRequest() {
        requests.increment();
        return Timer.start(registry);
    }

    public void recordSuccess(Timer.Sample sample) {
        success.increment();
        stop(sample, "SUCCESS");
    }

    public void recordFallback(Timer.Sample sample, String reason) {
        registry.counter("coach_fallback", "reason", sanitize(reason)).increment();
        stop(sample, "FALLBACK");
    }

    public void recordValidationFailure(String category) {
        registry.counter("coach_validation_failures", "category", sanitize(category)).increment();
    }

    public void recordRetry() {
        retries.increment();
    }

    public void recordTimeout() {
        timeouts.increment();
    }

    public void recordRejected(Timer.Sample sample) {
        stop(sample, "REJECTED");
    }

    public void recordError(Timer.Sample sample) {
        stop(sample, "ERROR");
    }

    private void stop(Timer.Sample sample, String outcome) {
        sample.stop(Timer.builder("coach_latency")
                .publishPercentileHistogram()
                .tag("outcome", outcome)
                .register(registry));
    }

    private static String sanitize(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}

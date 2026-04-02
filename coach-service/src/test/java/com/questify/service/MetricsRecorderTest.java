package com.questify.service;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsRecorderTest {

    @Test
    void records_request_success_retry_validation_timeout_and_fallback_metrics() {
        var registry = new SimpleMeterRegistry();
        var recorder = new MetricsRecorder(registry);

        Timer.Sample successSample = recorder.startRequest();
        recorder.recordRetry();
        recorder.recordValidationFailure("schema");
        recorder.recordTimeout();
        recorder.recordSuccess(successSample);

        Timer.Sample fallbackSample = recorder.startRequest();
        recorder.recordFallback(fallbackSample, "runtime_failure");

        assertThat(registry.get("coach_requests").counter().count()).isEqualTo(2d);
        assertThat(registry.get("coach_success").counter().count()).isEqualTo(1d);
        assertThat(registry.get("coach_retry").counter().count()).isEqualTo(1d);
        assertThat(registry.get("coach_timeouts").counter().count()).isEqualTo(1d);
        assertThat(registry.get("coach_validation_failures").tag("category", "schema").counter().count()).isEqualTo(1d);
        assertThat(registry.get("coach_fallback").tag("reason", "runtime_failure").counter().count()).isEqualTo(1d);
        assertThat(registry.get("coach_latency").timers()).hasSize(2);
    }
}

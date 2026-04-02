package com.questify.service;

import com.questify.dto.CoachDtos.CoachSuggestionsRes;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class FallbackFactory {

    private final Clock clock;

    public FallbackFactory(Clock clock) {
        this.clock = clock;
    }

    public CoachSuggestionsRes create() {
        return new CoachSuggestionsRes(
                "FALLBACK",
                "SYSTEM",
                null,
                Instant.now(clock).truncatedTo(ChronoUnit.SECONDS),
                List.of(),
                "Suggestions could not be generated at the moment.",
                "Try again later or continue with one small step from your current goal."
        );
    }
}

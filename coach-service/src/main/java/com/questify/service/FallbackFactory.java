package com.questify.service;

import com.questify.dto.CoachDtos.CoachSuggestionRes;
import com.questify.dto.CoachDtos.CoachSuggestionsRes;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class FallbackFactory {

    private record SuggestionTemplate(
            String title,
            String description,
            String category,
            int estimatedMinutes,
            String difficulty,
            String reason
    ) {}

    private final Clock clock;
    private final GoalFacetExtractor goalFacetExtractor;

    public FallbackFactory(Clock clock, GoalFacetExtractor goalFacetExtractor) {
        this.clock = clock;
        this.goalFacetExtractor = goalFacetExtractor;
    }

    public CoachSuggestionsRes create(CoachPromptContext context, List<String> excludedSuggestionTitles) {
        GoalFacetExtractor.GoalFacets facets = goalFacetExtractor.derive(context);
        return new CoachSuggestionsRes(
                "FALLBACK",
                "SYSTEM",
                null,
                Instant.now(clock).truncatedTo(ChronoUnit.SECONDS),
                buildSuggestions(facets, excludedSuggestionTitles),
                reflection(context),
                nudge(context)
        );
    }

    private static List<CoachSuggestionRes> buildSuggestions(GoalFacetExtractor.GoalFacets facets, List<String> excludedSuggestionTitles) {
        List<SuggestionTemplate> candidates = new ArrayList<>(goalDerivedTemplates(facets));

        Set<String> excluded = new LinkedHashSet<>();
        if (excludedSuggestionTitles != null) {
            for (String title : excludedSuggestionTitles) {
                if (title != null && !title.isBlank()) {
                    excluded.add(title.trim().toLowerCase(Locale.ROOT));
                }
            }
        }

        List<CoachSuggestionRes> suggestions = new ArrayList<>(3);
        Set<String> usedTitles = new LinkedHashSet<>();
        for (SuggestionTemplate candidate : candidates) {
            String normalizedTitle = candidate.title().toLowerCase(Locale.ROOT);
            if (excluded.contains(normalizedTitle) || usedTitles.contains(normalizedTitle)) {
                continue;
            }
            usedTitles.add(normalizedTitle);
            suggestions.add(toResponse(candidate));
            if (suggestions.size() == 3) {
                return List.copyOf(suggestions);
            }
        }

        return List.copyOf(suggestions);
    }

    private static CoachSuggestionRes toResponse(SuggestionTemplate template) {
        return new CoachSuggestionRes(
                template.title(),
                template.description(),
                template.category(),
                template.estimatedMinutes(),
                template.difficulty(),
                template.reason()
        );
    }

    private static List<SuggestionTemplate> goalDerivedTemplates(GoalFacetExtractor.GoalFacets facets) {
        String category = facets.category();
        String primaryTargetLabel = facets.primaryTargetLabel();
        String secondaryTargetLabel = facets.secondaryTargetLabel();

        return List.of(
                new SuggestionTemplate(
                        "Do one focused practice drill",
                        "Spend 15 minutes on one small, clearly defined step related to %s, then stop when the timer ends.".formatted(primaryTargetLabel),
                        category,
                        15,
                        "easy",
                        "A short, sharply scoped drill is easier to finish and repeat than an open-ended session."
                ),
                new SuggestionTemplate(
                        "Review one recent attempt",
                        "Look at one recent attempt related to %s, note what felt weak or unclear, and write one adjustment to try next.".formatted(secondaryTargetLabel),
                        category,
                        15,
                        "medium",
                        "A short review turns vague effort into one concrete improvement for the next attempt."
                ),
                new SuggestionTemplate(
                        "Prepare the next session",
                        "Set up the first step, materials, or environment you need so the next step related to %s is easier to start.".formatted(primaryTargetLabel),
                        category,
                        10,
                        "easy",
                        "Reducing setup friction makes follow-through much more likely when motivation is low."
                ),
                new SuggestionTemplate(
                        "Test one small skill",
                        "Run one short self-check related to %s so you can see what needs attention next without dragging the session out.".formatted(secondaryTargetLabel),
                        category,
                        15,
                        "medium",
                        "A small self-check makes weak spots visible quickly and keeps the next step specific."
                ),
                new SuggestionTemplate(
                        "Write one targeted note",
                        "Write one short note about what is working and what still gets in the way of %s.".formatted(primaryTargetLabel),
                        category,
                        10,
                        "easy",
                        "A quick note keeps progress visible and gives the next session a cleaner starting point."
                ),
                new SuggestionTemplate(
                        "Clear one starting blocker",
                        "Remove one small obstacle that would make the next step related to %s harder to begin.".formatted(primaryTargetLabel),
                        category,
                        10,
                        "easy",
                        "Clearing one blocker now protects momentum when you come back to it."
                ),
                new SuggestionTemplate(
                        "Repeat one short round",
                        "Do one short repeat of a single step related to %s so the motion feels more automatic next time.".formatted(secondaryTargetLabel),
                        category,
                        10,
                        "easy",
                        "A short repeat is enough to reinforce the next useful move without creating overload."
                )
        );
    }

    private static String reflection(CoachPromptContext context) {
        if (context.activeQuestCount() > 0) {
            return "You already have active quests in flight, so the safest backup is a small next step that adds momentum without creating overload.";
        }
        return "The coach fell back to a system-generated pass, so these suggestions stay intentionally small, clear, and easy to start.";
    }

    private static String nudge(CoachPromptContext context) {
        if (!context.activeQuestTitles().isEmpty()) {
            return "Pick the easiest card or pair one with an active quest you already have open.";
        }
        return "Pick the easiest card and do five minutes now so the next step is real, not theoretical.";
    }
}

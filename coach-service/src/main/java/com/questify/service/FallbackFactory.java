package com.questify.service;

import com.questify.dto.CoachDtos.CoachSuggestionRes;
import com.questify.dto.CoachDtos.CoachSuggestionsRes;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class FallbackFactory {

    private record GoalFacets(
            List<String> targets,
            String category
    ) {}

    private record SuggestionTemplate(
            String title,
            String description,
            String category,
            int estimatedMinutes,
            String difficulty,
            String reason
    ) {}

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9 ]");
    private static final Pattern MULTISPACE = Pattern.compile("\\s+");
    private static final Set<String> STOPWORDS = Set.of(
            "i", "me", "my", "mine", "you", "your", "yours", "we", "our", "ours",
            "want", "need", "help", "please", "goal", "goals", "achieve", "achieving",
            "improve", "improving", "better", "become", "create", "creating", "quest", "quests",
            "some", "more", "less", "with", "without", "during", "through", "into", "from",
            "that", "this", "these", "those", "they", "them", "their", "for", "and", "or",
            "the", "a", "an", "to", "of", "in", "on", "at", "by", "after", "before", "over",
            "under", "still", "just", "really", "kind", "sort", "bullshit"
    );
    private static final Map<String, Integer> CATEGORY_HINTS = Map.ofEntries(
            Map.entry("FITNESS", 0),
            Map.entry("STUDY", 0),
            Map.entry("WORK", 0),
            Map.entry("COMMUNITY", 0),
            Map.entry("HOBBY", 0),
            Map.entry("OTHER", 0)
    );

    private final Clock clock;

    public FallbackFactory(Clock clock) {
        this.clock = clock;
    }

    public CoachSuggestionsRes create(CoachPromptContext context, List<String> excludedSuggestionTitles) {
        GoalFacets facets = deriveGoalFacets(context);
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

    private static List<CoachSuggestionRes> buildSuggestions(GoalFacets facets, List<String> excludedSuggestionTitles) {
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

    private static List<SuggestionTemplate> goalDerivedTemplates(GoalFacets facets) {
        String primaryTarget = facets.targets().isEmpty() ? "your current goal" : facets.targets().getFirst();
        String secondaryTarget = facets.targets().size() > 1 ? facets.targets().get(1) : primaryTarget;
        String category = facets.category();
        String primaryTargetLabel = renderTargetLabel(primaryTarget, category);
        String secondaryTargetLabel = renderTargetLabel(secondaryTarget, category);

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

    private static GoalFacets deriveGoalFacets(CoachPromptContext context) {
        List<String> targets = extractTargets(context.goal());
        if (targets.isEmpty()) {
            targets = deriveTargetsFromContext(context);
        }
        if (targets.isEmpty()) {
            targets = List.of("your current goal");
        }

        return new GoalFacets(
                targets,
                inferCategory(context.goal(), targets)
        );
    }

    private static List<String> extractTargets(String goal) {
        if (goal == null || goal.isBlank()) {
            return List.of();
        }

        String normalizedGoal = goal
                .replace('\n', ' ')
                .replaceAll("[!?;,]", ".")
                .replaceAll("\\s+", " ")
                .trim();

        Set<String> collected = new LinkedHashSet<>();
        for (String fragment : normalizedGoal.split("\\.")) {
            String cleaned = normalizeFragment(fragment);
            if (cleaned.isBlank()) {
                continue;
            }

            for (String part : cleaned.split("\\band\\b|\\bor\\b")) {
                String candidate = normalizeTarget(part);
                if (!candidate.isBlank() && candidate.length() >= 3) {
                    collected.add(candidate);
                }
                if (collected.size() == 3) {
                    return List.copyOf(collected);
                }
            }
        }
        return List.copyOf(collected);
    }

    private static List<String> deriveTargetsFromContext(CoachPromptContext context) {
        return context.activeQuestTitles().stream()
                .filter(Objects::nonNull)
                .map(FallbackFactory::normalizeTarget)
                .filter(target -> !target.isBlank())
                .distinct()
                .limit(2)
                .toList();
    }

    private static String normalizeFragment(String fragment) {
        return fragment
                .replaceAll("(?i)\\b(i want to|i need to|i would like to|my goal is to|help me|please|can you|by creating .*|with .* quests.*)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String normalizeTarget(String raw) {
        String lowered = raw.toLowerCase(Locale.ROOT)
                .replaceAll("(?i)\\b(practice|improve|improving|study|learn|learning|focus on|work on|build|develop|train|exercise|reduce|keep up with|stay consistent with|get better at|become better at|do more|spend more time on|spend less time on)\\b", " ")
                .replaceAll("(?i)\\b(my|your|the|a|an|some|more|less|current|easy|medium|hard|difficulty|quests?)\\b", " ");

        String normalized = MULTISPACE.matcher(NON_ALPHANUMERIC.matcher(lowered).replaceAll(" ")).replaceAll(" ").trim();
        List<String> meaningfulTokens = List.of(normalized.split(" ")).stream()
                .filter(token -> !token.isBlank())
                .filter(token -> !STOPWORDS.contains(token))
                .limit(6)
                .toList();

        if (meaningfulTokens.isEmpty()) {
            return "";
        }
        return String.join(" ", meaningfulTokens);
    }

    private static String inferCategory(String goal, List<String> targets) {
        String haystack = (goal == null ? "" : goal) + " " + String.join(" ", targets);
        String normalized = normalizeTarget(haystack);
        Map<String, Integer> scores = new java.util.LinkedHashMap<>(CATEGORY_HINTS);

        addScore(scores, "FITNESS", normalized, "health", "healthy", "fitness", "exercise", "workout", "walk", "run", "gym", "mobility", "stretch");
        addScore(scores, "STUDY", normalized, "study", "learn", "course", "exam", "math", "history", "spanish", "geography", "lecture", "flashcard", "reading", "revise");
        addScore(scores, "WORK", normalized, "work", "career", "project", "job", "productivity", "coding", "code", "email", "meeting");
        addScore(scores, "COMMUNITY", normalized, "friend", "family", "community", "social", "volunteer", "relationship");
        addScore(scores, "HOBBY", normalized, "hobby", "speedrun", "speedrunning", "game", "gaming", "gta", "music", "guitar", "art", "drawing", "creative", "writing", "chess", "photography");

        return scores.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .filter(entry -> entry.getValue() > 0)
                .map(Map.Entry::getKey)
                .orElse("OTHER");
    }

    private static void addScore(Map<String, Integer> scores, String key, String haystack, String... needles) {
        int score = 0;
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                score++;
            }
        }
        scores.computeIfPresent(key, (ignored, current) -> current + score);
    }

    private static String renderTargetLabel(String target, String category) {
        if (target == null || target.isBlank()) {
            return abstractLabel(category);
        }
        int tokenCount = target.split(" ").length;
        if (tokenCount > 2 || target.length() > 20) {
            return abstractLabel(category);
        }
        return target;
    }

    private static String abstractLabel(String category) {
        return switch (category) {
            case "FITNESS" -> "your movement focus";
            case "STUDY" -> "your study focus";
            case "WORK" -> "your current work focus";
            case "COMMUNITY" -> "your connection goal";
            case "HOBBY" -> "your practice focus";
            default -> "your current goal";
        };
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

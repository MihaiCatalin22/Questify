package com.questify.service;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class GoalFacetExtractor {

    public record GoalFacets(
            List<String> targets,
            String category
    ) {
        public String primaryTarget() {
            return targets.isEmpty() ? "" : targets.getFirst();
        }

        public String secondaryTarget() {
            return targets.size() > 1 ? targets.get(1) : primaryTarget();
        }

        public String primaryTargetLabel() {
            return GoalFacetExtractor.renderTargetLabel(primaryTarget(), category);
        }

        public String secondaryTargetLabel() {
            return GoalFacetExtractor.renderTargetLabel(secondaryTarget(), category);
        }

        public String facetSummary() {
            if (targets.isEmpty()) {
                return "- No explicit sub-areas were extracted.\n- Keep suggestions broad, realistic, and varied.";
            }

            String facets = targets.stream()
                    .map(target -> "- " + target)
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("- " + primaryTargetLabel());

            return """
                    %s
                    - Category hint: %s
                    """.formatted(facets, category).trim();
        }

        public String coverageGuidance() {
            if (targets.size() >= 2) {
                return "Spread the 3 suggestions across these sub-areas when reasonable: " + String.join(", ", targets) + ".";
            }
            if (!primaryTargetLabel().isBlank()) {
                return "Vary the angle around %s instead of rephrasing the same action.".formatted(primaryTargetLabel());
            }
            return "Vary the angle of the suggestions instead of repeating the same idea.";
        }
    }

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9 ]");
    private static final Pattern MULTISPACE = Pattern.compile("\\s+");
    private static final Set<String> STOPWORDS = Set.of(
            "i", "me", "my", "mine", "you", "your", "yours", "we", "our", "ours",
            "want", "need", "help", "please", "goal", "goals", "achieve", "achieving",
            "improve", "improving", "better", "become", "create", "creating", "quest", "quests",
            "some", "more", "less", "with", "without", "during", "through", "into", "from",
            "that", "this", "these", "those", "they", "them", "their", "for", "and", "or",
            "the", "a", "an", "to", "of", "in", "on", "at", "by", "after", "before", "over",
            "under", "still", "just", "really", "kind", "sort", "bullshit", "easy", "medium", "hard"
    );
    private static final Map<String, Integer> CATEGORY_HINTS = Map.ofEntries(
            Map.entry("FITNESS", 0),
            Map.entry("STUDY", 0),
            Map.entry("WORK", 0),
            Map.entry("COMMUNITY", 0),
            Map.entry("HOBBY", 0),
            Map.entry("OTHER", 0)
    );

    public GoalFacets derive(CoachPromptContext context) {
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

    private List<String> extractTargets(String goal) {
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

    private List<String> deriveTargetsFromContext(CoachPromptContext context) {
        return context.activeQuestTitles().stream()
                .filter(Objects::nonNull)
                .map(this::normalizeTarget)
                .filter(target -> !target.isBlank())
                .distinct()
                .limit(2)
                .toList();
    }

    private String inferCategory(String goal, List<String> targets) {
        String haystack = (goal == null ? "" : goal) + " " + String.join(" ", targets);
        String normalized = normalizeTarget(haystack);
        Map<String, Integer> scores = new LinkedHashMap<>(CATEGORY_HINTS);

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

    private static String normalizeFragment(String fragment) {
        return fragment
                .replaceAll("(?i)\\b(i want to|i need to|i would like to|my goal is to|help me|please|can you|by creating .*|with .* quests.*)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeTarget(String raw) {
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

    private static void addScore(Map<String, Integer> scores, String key, String haystack, String... needles) {
        int score = 0;
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                score++;
            }
        }
        scores.put(key, scores.getOrDefault(key, 0) + score);
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
}

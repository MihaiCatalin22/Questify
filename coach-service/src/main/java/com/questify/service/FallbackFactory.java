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

    public FallbackFactory(Clock clock) {
        this.clock = clock;
    }

    public CoachSuggestionsRes create(CoachPromptContext context, List<String> excludedSuggestionTitles) {
        String primaryCategory = primaryCategory(context.goal());
        return new CoachSuggestionsRes(
                "FALLBACK",
                "SYSTEM",
                null,
                Instant.now(clock).truncatedTo(ChronoUnit.SECONDS),
                buildSuggestions(primaryCategory, excludedSuggestionTitles),
                reflection(context),
                nudge(context)
        );
    }

    private static List<CoachSuggestionRes> buildSuggestions(String primaryCategory, List<String> excludedSuggestionTitles) {
        List<SuggestionTemplate> candidates = new ArrayList<>(categoryTemplates(primaryCategory));
        candidates.addAll(sharedTemplates());

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

        for (SuggestionTemplate candidate : sharedTemplates()) {
            String normalizedTitle = candidate.title().toLowerCase(Locale.ROOT);
            if (usedTitles.add(normalizedTitle)) {
                suggestions.add(toResponse(candidate));
            }
            if (suggestions.size() == 3) {
                break;
            }
        }

        if (suggestions.size() < 3) {
            for (SuggestionTemplate candidate : candidates) {
                String normalizedTitle = candidate.title().toLowerCase(Locale.ROOT);
                if (usedTitles.add(normalizedTitle)) {
                    suggestions.add(toResponse(candidate));
                }
                if (suggestions.size() == 3) {
                    break;
                }
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

    private static List<SuggestionTemplate> categoryTemplates(String primaryCategory) {
        return switch (primaryCategory) {
            case "GAME_PRACTICE" -> List.of(
                    new SuggestionTemplate(
                            "Practice one short route segment",
                            "Repeat one specific route segment or setup for 15 minutes instead of doing a full run.",
                            "HOBBY",
                            15,
                            "easy",
                            "Short segment drills build consistency faster than scattered full attempts."
                    ),
                    new SuggestionTemplate(
                            "Review one failed attempt",
                            "Watch or replay one recent mistake and write one adjustment to try on the next run.",
                            "HOBBY",
                            15,
                            "medium",
                            "One focused review turns a failed attempt into a specific correction."
                    ),
                    new SuggestionTemplate(
                            "Set up one clean restart routine",
                            "Prepare your timer, splits, notes, and controls so the next practice block starts immediately.",
                            "HABIT",
                            10,
                            "easy",
                            "A clean setup reduces wasted energy between attempts."
                    )
            );
            case "FITNESS" -> List.of(
                    new SuggestionTemplate(
                            "Take a 15-minute walk",
                            "Walk at an easy pace for 15 minutes and stop when the timer ends.",
                            "FITNESS",
                            15,
                            "easy",
                            "A short walk is easy to start and still counts as real progress."
                    ),
                    new SuggestionTemplate(
                            "Do a short stretch routine",
                            "Spend 10 minutes on gentle stretching or mobility to lower the barrier to moving today.",
                            "FITNESS",
                            10,
                            "easy",
                            "Low-friction movement helps keep consistency intact."
                    ),
                    new SuggestionTemplate(
                            "Set up your workout space",
                            "Prepare clothes, shoes, or equipment now so the next session starts with less setup.",
                            "HABIT",
                            10,
                            "easy",
                            "Reducing setup friction makes the next workout easier to begin."
                    )
            );
            case "STUDY" -> List.of(
                    new SuggestionTemplate(
                            "Do one focused study block",
                            "Study one topic for 15 minutes with a timer and stop before the session expands.",
                            "STUDY",
                            15,
                            "easy",
                            "A short study block is easier to repeat than an open-ended session."
                    ),
                    new SuggestionTemplate(
                            "Summarize one topic from memory",
                            "Write a short summary of one topic without notes, then check what you missed.",
                            "STUDY",
                            15,
                            "medium",
                            "Recall practice makes weak spots visible fast."
                    ),
                    new SuggestionTemplate(
                            "Prepare tomorrow's study setup",
                            "Lay out one book, one notebook, and the first task so the next session starts cleanly.",
                            "HABIT",
                            10,
                            "easy",
                            "Preparation removes friction from the next study block."
                    )
            );
            case "WORK" -> List.of(
                    new SuggestionTemplate(
                            "Do one focused work block",
                            "Work on one concrete task for 15 minutes without switching contexts.",
                            "WORK",
                            15,
                            "easy",
                            "A short block turns intention into visible progress."
                    ),
                    new SuggestionTemplate(
                            "Clear one blocker",
                            "Remove one small blocker that would slow down your next work session.",
                            "WORK",
                            10,
                            "easy",
                            "Small unblockers make later work easier to start."
                    ),
                    new SuggestionTemplate(
                            "Write tomorrow's first task",
                            "Choose the first task for tomorrow and write the exact starting step.",
                            "HABIT",
                            10,
                            "easy",
                            "A defined starting step reduces hesitation later."
                    )
            );
            case "HOBBY" -> List.of(
                    new SuggestionTemplate(
                            "Spend 15 minutes on your hobby",
                            "Do one short session and stop when the timer ends, even if it feels unfinished.",
                            "HOBBY",
                            15,
                            "easy",
                            "Short sessions keep the hobby active without pressure."
                    ),
                    new SuggestionTemplate(
                            "Set up your materials",
                            "Prepare the tools or materials you need so the next hobby session starts immediately.",
                            "HABIT",
                            10,
                            "easy",
                            "Preparation protects momentum when time is limited."
                    ),
                    new SuggestionTemplate(
                            "Capture one quick takeaway",
                            "Write one note about what worked, what felt hard, and what to try next time.",
                            "HABIT",
                            10,
                            "easy",
                            "A small note helps the next session start with direction."
                    )
            );
            case "COMMUNITY" -> List.of(
                    new SuggestionTemplate(
                            "Reach out to one person",
                            "Send one short message to start or maintain a connection without overthinking it.",
                            "COMMUNITY",
                            10,
                            "easy",
                            "One clear message is easier to follow through on than a vague social plan."
                    ),
                    new SuggestionTemplate(
                            "Plan one small check-in",
                            "Choose a person and a time for a short check-in later this week.",
                            "COMMUNITY",
                            10,
                            "easy",
                            "Scheduling removes the need to decide again later."
                    ),
                    new SuggestionTemplate(
                            "Write a quick thank-you note",
                            "Send a short thank-you or appreciation message to someone important to you.",
                            "COMMUNITY",
                            10,
                            "easy",
                            "A small positive action strengthens connection without heavy effort."
                    )
            );
            default -> List.of(
                    new SuggestionTemplate(
                            "Do one focused 15-minute session",
                            "Pick one useful action, work on it for 15 minutes, and stop when the timer ends.",
                            "HABIT",
                            15,
                            "easy",
                            "A short session is easier to begin than a perfect plan."
                    ),
                    new SuggestionTemplate(
                            "Prepare your next session",
                            "Set up the materials, timing, or environment for your next attempt so it starts more smoothly.",
                            "HABIT",
                            10,
                            "easy",
                            "Preparation makes the next step easier to begin."
                    ),
                    new SuggestionTemplate(
                            "Write a quick progress note",
                            "Write one short note on what worked, what blocked you, and what to try next.",
                            "HABIT",
                            10,
                            "easy",
                            "A quick review turns effort into a clearer next step."
                    )
            );
        };
    }

    private static List<SuggestionTemplate> sharedTemplates() {
        return List.of(
                new SuggestionTemplate(
                        "Prepare your next session",
                        "Set up the materials, timing, or environment for your next attempt so it starts more smoothly.",
                        "HABIT",
                        10,
                        "easy",
                        "Preparation makes the next step easier to begin."
                ),
                new SuggestionTemplate(
                        "Write a quick progress note",
                        "Write one short note on what worked, what blocked you, and what to try next.",
                        "HABIT",
                        10,
                        "easy",
                        "A quick review turns effort into a clearer next step."
                ),
                new SuggestionTemplate(
                        "Clear one small obstacle",
                        "Remove one small source of friction that would make the next session harder to start.",
                        "HABIT",
                        10,
                        "easy",
                        "Removing friction now protects future follow-through."
                )
        );
    }

    private static String primaryCategory(String goal) {
        String normalized = goal == null ? "" : goal.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "speedrun", "speedrunning", "grand theft auto", "gta", "gaming", "video game", "controller", "splits", "route segment", "pb")) {
            return "GAME_PRACTICE";
        }
        if (containsAny(normalized, "health", "healthy", "fitness", "exercise", "workout", "walk", "run", "gym")) {
            return "FITNESS";
        }
        if (containsAny(normalized, "study", "learn", "learning", "course", "exam", "read", "reading")) {
            return "STUDY";
        }
        if (containsAny(normalized, "work", "career", "project", "job", "productivity", "code", "coding")) {
            return "WORK";
        }
        if (containsAny(normalized, "draw", "music", "guitar", "art", "write", "writing", "creative", "hobby")) {
            return "HOBBY";
        }
        if (containsAny(normalized, "friend", "family", "community", "social", "volunteer")) {
            return "COMMUNITY";
        }
        return "HABIT";
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
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

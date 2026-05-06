package com.questify.service;

import com.questify.config.CoachProperties;
import com.questify.dto.CoachDtos.CoachSuggestionMode;
import com.questify.provider.GenerationPrompt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(PromptBuilder.class);

    private final PromptAssets assets;
    private final CoachProperties properties;
    private final GoalFacetExtractor goalFacetExtractor;

    public PromptBuilder(PromptAssets assets, CoachProperties properties, GoalFacetExtractor goalFacetExtractor) {
        this.assets = assets;
        this.properties = properties;
        this.goalFacetExtractor = goalFacetExtractor;
    }

    public GenerationPrompt buildPrimaryPrompt(CoachPromptContext context,
                                               CoachSuggestionMode mode,
                                               List<String> excludedSuggestionTitles) {
        GoalFacetExtractor.GoalFacets facets = goalFacetExtractor.derive(context);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("schema", assets.schemaText());
        values.put("mode", mode.name());
        values.put("goal", context.goal());
        values.put("goalFacetSummary", facets.facetSummary());
        values.put("goalCoverageGuidance", facets.coverageGuidance());
        values.put("recentQuestHistory", formatRecentHistory(context));
        values.put("recentPattern", formatRecentPattern(context));
        values.put("excludedSuggestionTitles", formatExcludedSuggestionTitles(excludedSuggestionTitles));

        String userPrompt = render(assets.userTemplate(), values);
        debugLog("primary", assets.systemTemplate(), userPrompt);
        return new GenerationPrompt(assets.systemTemplate(), userPrompt);
    }

    public GenerationPrompt buildRepairPrompt(CoachPromptContext context,
                                              CoachSuggestionMode mode,
                                              List<String> excludedSuggestionTitles,
                                              String invalidOutput,
                                              List<String> validationErrors) {
        GoalFacetExtractor.GoalFacets facets = goalFacetExtractor.derive(context);
        String errorLines = validationErrors == null || validationErrors.isEmpty()
                ? "- No validation errors were captured."
                : validationErrors.stream()
                .limit(6)
                .map(error -> "- " + error)
                .collect(Collectors.joining("\n"));

        String repairUserPrompt = """
                Return JSON that exactly matches this schema:
                %s

                Suggestion mode:
                %s

                User goal:
                %s

                Goal facets:
                %s

                Coverage guidance:
                %s

                Recent quest history:
                %s

                Recent pattern:
                %s

                Excluded suggestion titles:
                %s

                Validation errors:
                %s

                Previous invalid output excerpt:
                %s
                """.formatted(
                assets.schemaText(),
                mode.name(),
                context.goal(),
                facets.facetSummary(),
                facets.coverageGuidance(),
                formatRecentHistory(context),
                formatRecentPattern(context),
                formatExcludedSuggestionTitles(excludedSuggestionTitles),
                errorLines,
                abbreviate(normalizeWhitespace(invalidOutput), 900)
        ).trim();

        debugLog("repair", assets.repairTemplate(), repairUserPrompt);
        return new GenerationPrompt(assets.repairTemplate(), repairUserPrompt);
    }

    private String formatRecentHistory(CoachPromptContext context) {
        if (!context.includeRecentHistory()) {
            return "Recent history omitted by request.";
        }
        if (context.recentCompletions().isEmpty()) {
            return "No recent completions available.";
        }

        List<CoachPromptContext.RecentCompletion> limited = context.recentCompletions().stream()
                .limit(5)
                .toList();
        String lines = limited.stream()
                .map(item -> "- " + item.title() + " @ " + item.completedAt())
                .collect(Collectors.joining("\n"));
        int remaining = Math.max(0, context.recentCompletions().size() - limited.size());
        if (remaining > 0) {
            lines += "\n- +" + remaining + " more recent completions";
        }
        return lines;
    }

    private String formatRecentPattern(CoachPromptContext context) {
        List<String> activeTitles = context.activeQuestTitles().stream()
                .limit(5)
                .toList();
        String activeTitleSummary = activeTitles.isEmpty()
                ? "No active quest titles available."
                : String.join(", ", activeTitles);
        int remaining = Math.max(0, context.activeQuestTitles().size() - activeTitles.size());
        if (remaining > 0) {
            activeTitleSummary += ", +" + remaining + " more";
        }
        return """
                Active quest titles: %s
                Active quest count: %d
                Total completed quest count: %d
                Recent history included: %s
                """.formatted(
                activeTitleSummary,
                context.activeQuestCount(),
                context.totalCompletedCount(),
                context.includeRecentHistory()
        ).trim();
    }

    private String formatExcludedSuggestionTitles(List<String> excludedSuggestionTitles) {
        if (excludedSuggestionTitles == null || excludedSuggestionTitles.isEmpty()) {
            return "No excluded suggestion titles were provided.";
        }
        List<String> limited = excludedSuggestionTitles.stream()
                .limit(8)
                .toList();
        String lines = limited.stream()
                .map(title -> "- " + title)
                .collect(Collectors.joining("\n"));
        int remaining = Math.max(0, excludedSuggestionTitles.size() - limited.size());
        if (remaining > 0) {
            lines += "\n- +" + remaining + " more excluded titles";
        }
        return lines;
    }

    private String render(String template, Map<String, String> values) {
        String rendered = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue() == null ? "" : entry.getValue());
        }
        return rendered;
    }

    private void debugLog(String phase, String systemPrompt, String userPrompt) {
        if (!properties.isDebugLogging() || !log.isDebugEnabled()) {
            return;
        }
        log.debug("Coach prompt phase={} systemHash={} userHash={} userChars={}",
                phase,
                sha256(systemPrompt),
                sha256(userPrompt),
                userPrompt.length());
    }

    private static String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim() + "...";
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

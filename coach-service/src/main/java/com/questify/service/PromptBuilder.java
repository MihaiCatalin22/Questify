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

    public PromptBuilder(PromptAssets assets, CoachProperties properties) {
        this.assets = assets;
        this.properties = properties;
    }

    public GenerationPrompt buildPrimaryPrompt(CoachPromptContext context,
                                               CoachSuggestionMode mode,
                                               List<String> excludedSuggestionTitles) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("schema", assets.schemaText());
        values.put("mode", mode.name());
        values.put("goal", context.goal());
        values.put("recentQuestHistory", formatRecentHistory(context));
        values.put("recentPattern", formatRecentPattern(context));
        values.put("excludedSuggestionTitles", formatExcludedSuggestionTitles(excludedSuggestionTitles));

        String userPrompt = render(assets.userTemplate(), values);
        debugLog("primary", assets.systemTemplate(), userPrompt);
        return new GenerationPrompt(assets.systemTemplate(), userPrompt);
    }

    public GenerationPrompt buildRepairPrompt(GenerationPrompt originalPrompt,
                                              String invalidOutput,
                                              List<String> validationErrors) {
        String errorLines = validationErrors == null || validationErrors.isEmpty()
                ? "- No validation errors were captured."
                : validationErrors.stream().map(error -> "- " + error).collect(Collectors.joining("\n"));

        String repairUserPrompt = """
                Return JSON that exactly matches this schema:
                %s

                Validation errors:
                %s

                Original prompt:
                %s

                Previous invalid output:
                %s
                """.formatted(
                assets.schemaText(),
                errorLines,
                originalPrompt.userPrompt(),
                invalidOutput == null ? "" : invalidOutput
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
        return context.recentCompletions().stream()
                .map(item -> "- " + item.title() + " @ " + item.completedAt())
                .collect(Collectors.joining("\n"));
    }

    private String formatRecentPattern(CoachPromptContext context) {
        String activeTitles = context.activeQuestTitles().isEmpty()
                ? "No active quest titles available."
                : String.join(", ", context.activeQuestTitles());
        return """
                Active quest titles: %s
                Active quest count: %d
                Total completed quest count: %d
                Recent history included: %s
                """.formatted(
                activeTitles,
                context.activeQuestCount(),
                context.totalCompletedCount(),
                context.includeRecentHistory()
        ).trim();
    }

    private String formatExcludedSuggestionTitles(List<String> excludedSuggestionTitles) {
        if (excludedSuggestionTitles == null || excludedSuggestionTitles.isEmpty()) {
            return "No excluded suggestion titles were provided.";
        }
        return excludedSuggestionTitles.stream()
                .map(title -> "- " + title)
                .collect(Collectors.joining("\n"));
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

package com.questify.benchmark;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("ai-review-benchmark")
class AiReviewBenchmarkTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-z0-9]{4,}");
    private static final Set<String> STOPWORDS = Set.of(
            "this", "that", "with", "from", "into", "your", "have", "been", "were", "will",
            "then", "than", "there", "their", "about", "quest", "proof", "image", "student",
            "comment", "submission", "manual", "review", "show", "shows", "showing", "completed",
            "activity", "valid", "match", "matches", "relevant", "looks", "good", "appears"
    );
    private static final String DEFAULT_MODELS = "llava:7b,qwen2.5vl:3b,qwen2.5vl:7b,openbmb/minicpm-v2.6:latest";

    @Test
    void runBenchmarkAndProduceReport() throws Exception {
        BenchmarkCorpus corpus = loadCorpus();
        validateCorpusComposition(corpus.cases());

        String runtimeBase = env("AI_REVIEW_RUNTIME_BASE_URL", "http://localhost:11434");
        List<String> models = parseModels(env("AI_REVIEW_BENCHMARK_MODELS", DEFAULT_MODELS));
        int warmupPasses = intEnv("AI_REVIEW_BENCHMARK_WARMUP_PASSES", 1);
        int measuredPasses = intEnv("AI_REVIEW_BENCHMARK_MEASURED_PASSES", 3);
        boolean enforceGates = boolEnv("AI_REVIEW_BENCHMARK_ENFORCE_GATES", false);
        Path reportDir = prepareReportDir();

        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        List<ModelBenchmarkSummary> summaries = new ArrayList<>();

        for (String model : models) {
            Path requestLog = reportDir.resolve("requests").resolve(safeName(model) + ".jsonl");
            Files.createDirectories(requestLog.getParent());

            List<MeasuredRun> runs = new ArrayList<>();
            for (BenchmarkCase testCase : corpus.cases()) {
                for (int i = 0; i < warmupPasses; i++) {
                    callModel(http, runtimeBase, model, testCase, true);
                }
                for (int pass = 1; pass <= measuredPasses; pass++) {
                    MeasuredRun run = callModel(http, runtimeBase, model, testCase, false);
                    runs.add(run);
                    Files.writeString(requestLog, MAPPER.writeValueAsString(run) + System.lineSeparator(),
                            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                }
            }
            summaries.add(summarize(model, runs));
        }

        writeReports(reportDir, summaries, models, runtimeBase, warmupPasses, measuredPasses);

        assertFalse(summaries.isEmpty(), "No benchmark summaries were produced.");
        if (enforceGates) {
            assertTrue(summaries.stream().anyMatch(ModelBenchmarkSummary::passedGate),
                    "No candidate model passed promotion gates. See " + reportDir.resolve("summary.md"));
        }
    }

    private static void validateCorpusComposition(List<BenchmarkCase> cases) {
        assertEquals(80, cases.size(), "Corpus must contain exactly 80 cases by default.");
        long invalid = cases.stream().filter(c -> c.label() == CaseLabel.INVALID_MISMATCH).count();
        long valid = cases.stream().filter(c -> c.label() == CaseLabel.VALID_MATCH).count();
        long ambiguous = cases.stream().filter(c -> c.label() == CaseLabel.AMBIGUOUS).count();
        assertEquals(40L, invalid, "Corpus must include 40 INVALID_MISMATCH cases.");
        assertEquals(20L, valid, "Corpus must include 20 VALID_MATCH cases.");
        assertEquals(20L, ambiguous, "Corpus must include 20 AMBIGUOUS cases.");
    }

    private static BenchmarkCorpus loadCorpus() throws IOException {
        String externalCorpusPath = System.getenv("AI_REVIEW_BENCHMARK_CORPUS_PATH");
        if (externalCorpusPath != null && !externalCorpusPath.isBlank()) {
            JsonNode root = MAPPER.readTree(Path.of(externalCorpusPath.trim()).toFile());
            List<BenchmarkCase> cases = MAPPER.convertValue(root.path("cases"), new TypeReference<>() {});
            return new BenchmarkCorpus(cases);
        }

        try (var in = AiReviewBenchmarkTest.class.getResourceAsStream("/benchmark/ai-review-corpus.json")) {
            if (in == null) throw new IllegalStateException("Missing benchmark corpus resource: /benchmark/ai-review-corpus.json");
            JsonNode root = MAPPER.readTree(in);
            List<BenchmarkCase> cases = MAPPER.convertValue(root.path("cases"), new TypeReference<>() {});
            return new BenchmarkCorpus(cases);
        }
    }

    private static MeasuredRun callModel(HttpClient http,
                                         String baseUrl,
                                         String model,
                                         BenchmarkCase testCase,
                                         boolean warmup) {
        long started = System.nanoTime();
        String raw = "";
        AiReviewRecommendation recommendation;
        double confidence;
        List<String> reasons = List.of("Model call failed.");
        String decisionNote = null;
        String error = null;

        try {
            String prompt = """
                    Review this Questify submission as advisory evidence only.
                    Quest title: %s
                    Quest description: %s
                    Student comment: %s

                    Return only valid JSON with exactly:
                    {"recommendation":"LIKELY_VALID","confidence":0.75,"reasons":["short reason"],"mediaSupported":true}

                    Rules:
                    - recommendation must be one of LIKELY_VALID, UNCLEAR, LIKELY_INVALID
                    - confidence must be between 0 and 1
                    - reasons must be 1-3 short strings
                    - if uncertain, prefer UNCLEAR
                    """.formatted(testCase.questTitle(), testCase.questDescription(), nullToBlank(testCase.studentComment()));

            Map<String, Object> request = new HashMap<>();
            request.put("model", model);
            request.put("stream", false);
            request.put("format", "json");
            request.put("messages", List.of(
                    Map.of("role", "system", "content", "You are an advisory proof reviewer. Return only compact JSON."),
                    Map.of(
                            "role", "user",
                            "content", prompt,
                            "images", resolveImages(testCase)
                    )
            ));
            request.put("options", Map.of("temperature", 0.1));

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(stripSuffix(baseUrl, "/") + "/api/chat"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(request)))
                    .build();

            HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("HTTP " + response.statusCode() + ": " + truncate(response.body(), 500));
            }

            JsonNode responseNode = MAPPER.readTree(response.body());
            raw = responseNode.path("message").path("content").asText("");
            ParsedModelOutput parsed = parse(raw);
            FinalDecision finalDecision = applyPrecisionGuard(testCase, parsed);
            recommendation = finalDecision.recommendation();
            confidence = finalDecision.confidence();
            reasons = finalDecision.reasons();
            decisionNote = finalDecision.decisionNote();
        } catch (Exception ex) {
            recommendation = AiReviewRecommendation.AI_FAILED;
            confidence = 0.0;
            error = ex.toString();
            reasons = List.of("AI review failed during benchmark call.");
            decisionNote = "AI_FAILED during benchmark call.";
        }

        long latencyMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
        return new MeasuredRun(
                model,
                testCase.id(),
                testCase.label(),
                warmup,
                recommendation,
                confidence,
                reasons,
                decisionNote,
                latencyMs,
                truncate(raw, 500),
                error,
                Instant.now().toString()
        );
    }

    private static ParsedModelOutput parse(String raw) {
        String json = extractJson(raw);
        try {
            JsonNode node = MAPPER.readTree(json);
            AiReviewRecommendation recommendation = switch (node.path("recommendation").asText("UNCLEAR")) {
                case "LIKELY_VALID" -> AiReviewRecommendation.LIKELY_VALID;
                case "LIKELY_INVALID" -> AiReviewRecommendation.LIKELY_INVALID;
                default -> AiReviewRecommendation.UNCLEAR;
            };
            double confidence = parseConfidence(node.path("confidence"));
            ArrayList<String> reasons = new ArrayList<>();
            JsonNode reasonsNode = node.path("reasons");
            if (reasonsNode.isArray()) reasonsNode.forEach(value -> reasons.add(value.asText()));
            if (reasons.isEmpty()) reasons.add("Manual review required.");
            return new ParsedModelOutput(recommendation, confidence, reasons);
        } catch (Exception parseError) {
            return new ParsedModelOutput(AiReviewRecommendation.UNCLEAR, 0.2,
                    List.of("Malformed model output; treated conservatively."));
        }
    }

    private static String extractJson(String raw) {
        if (raw == null) return "{}";
        String cleaned = raw.replace("```json", "").replace("```", "").trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) return cleaned.substring(start, end + 1);
        return cleaned;
    }

    private static double parseConfidence(JsonNode confidenceNode) {
        if (confidenceNode == null || confidenceNode.isMissingNode()) return 0.0;
        if (confidenceNode.isNumber()) {
            return clamp(confidenceNode.asDouble(0.0));
        }
        if (confidenceNode.isTextual()) {
            Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(confidenceNode.asText());
            if (matcher.find()) {
                double value = Double.parseDouble(matcher.group(1));
                return clamp(value > 1.0 ? value / 100.0 : value);
            }
        }
        return 0.0;
    }

    private static double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static FinalDecision applyPrecisionGuard(BenchmarkCase testCase, ParsedModelOutput parsed) {
        if (parsed.recommendation() != AiReviewRecommendation.LIKELY_VALID) {
            return new FinalDecision(parsed.recommendation(), parsed.confidence(), parsed.reasons(), null);
        }
        if (hasSpecificEvidence(testCase, parsed.reasons())) {
            return new FinalDecision(parsed.recommendation(), parsed.confidence(), parsed.reasons(), null);
        }
        List<String> updatedReasons = new ArrayList<>(parsed.reasons());
        updatedReasons.add("Auto-policy: reasons were too generic for auto-valid recommendation.");
        return new FinalDecision(
                AiReviewRecommendation.UNCLEAR,
                Math.min(parsed.confidence(), 0.49),
                updatedReasons,
                "Downgraded to UNCLEAR because evidence was generic and not quest/proof-specific."
        );
    }

    private static boolean hasSpecificEvidence(BenchmarkCase testCase, List<String> reasons) {
        Set<String> tokens = buildTokens(testCase);
        List<String> normalizedReasons = reasons.stream()
                .map(reason -> reason == null ? "" : reason.toLowerCase(Locale.ROOT))
                .toList();
        for (String reason : normalizedReasons) {
            for (String token : tokens) {
                if (reason.contains(token)) return true;
            }
        }
        return !looksGeneric(normalizedReasons);
    }

    private static Set<String> buildTokens(BenchmarkCase testCase) {
        String source = (nullToBlank(testCase.questTitle()) + " " + nullToBlank(testCase.questDescription()) + " " + nullToBlank(testCase.studentComment()))
                .toLowerCase(Locale.ROOT);
        Set<String> tokens = new HashSet<>();
        Matcher matcher = TOKEN_PATTERN.matcher(source);
        while (matcher.find()) {
            String token = matcher.group();
            if (!STOPWORDS.contains(token) && !token.chars().allMatch(Character::isDigit)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static boolean looksGeneric(List<String> normalizedReasons) {
        int genericCount = 0;
        for (String reason : normalizedReasons) {
            String text = reason == null ? "" : reason.trim();
            if (text.isBlank()) {
                genericCount++;
                continue;
            }
            boolean hasWeakPhrase = text.contains("looks relevant")
                    || text.contains("seems relevant")
                    || text.contains("appears relevant")
                    || text.contains("matches the quest")
                    || text.contains("aligned with")
                    || text.contains("appropriate proof")
                    || text.contains("sufficient evidence")
                    || text.contains("likely completed")
                    || text.contains("could indicate");
            boolean hasConcreteMarker = text.contains("equation")
                    || text.contains("worksheet")
                    || text.contains("graph")
                    || text.contains("map")
                    || text.contains("code")
                    || text.contains("chapter")
                    || text.contains("minutes")
                    || text.contains("pages")
                    || text.contains("photo")
                    || text.contains("screenshot")
                    || text.contains("contains");
            if (hasWeakPhrase && !hasConcreteMarker) genericCount++;
        }
        return genericCount == normalizedReasons.size();
    }

    private static ModelBenchmarkSummary summarize(String model, List<MeasuredRun> runs) {
        List<MeasuredRun> measured = runs.stream().filter(run -> !run.warmup()).toList();
        long invalidTotal = measured.stream().filter(run -> run.label() == CaseLabel.INVALID_MISMATCH).count();
        long falseApproves = measured.stream().filter(run ->
                run.label() == CaseLabel.INVALID_MISMATCH
                        && run.recommendation() == AiReviewRecommendation.LIKELY_VALID
        ).count();
        long likelyValidAll = measured.stream().filter(run -> run.recommendation() == AiReviewRecommendation.LIKELY_VALID).count();
        long likelyValidTruePositives = measured.stream().filter(run ->
                run.label() == CaseLabel.VALID_MATCH && run.recommendation() == AiReviewRecommendation.LIKELY_VALID
        ).count();
        long aiFailed = measured.stream().filter(run -> run.recommendation() == AiReviewRecommendation.AI_FAILED).count();

        double falseApproveRate = invalidTotal == 0 ? 0.0 : (double) falseApproves / invalidTotal;
        double precision = likelyValidAll == 0 ? 1.0 : (double) likelyValidTruePositives / likelyValidAll;
        double aiFailedRate = measured.isEmpty() ? 0.0 : (double) aiFailed / measured.size();
        long p95LatencyMs = percentileLatency(measured, 0.95);

        boolean passFalseApprove = falseApproveRate <= 0.05;
        boolean passPrecision = precision >= 0.90;
        boolean passAiFailed = aiFailedRate <= 0.01;
        boolean passLatency = p95LatencyMs <= 20_000;
        boolean passedGate = passFalseApprove && passPrecision && passAiFailed && passLatency;

        return new ModelBenchmarkSummary(
                model,
                measured.size(),
                falseApproveRate,
                precision,
                aiFailedRate,
                p95LatencyMs,
                passFalseApprove,
                passPrecision,
                passAiFailed,
                passLatency,
                passedGate
        );
    }

    private static long percentileLatency(List<MeasuredRun> runs, double percentile) {
        if (runs.isEmpty()) return 0;
        List<Long> sorted = runs.stream().map(MeasuredRun::latencyMs).sorted().toList();
        int index = Math.max(0, (int) Math.ceil(percentile * sorted.size()) - 1);
        return sorted.get(index);
    }

    private static void writeReports(Path reportDir,
                                     List<ModelBenchmarkSummary> summaries,
                                     List<String> models,
                                     String runtimeBase,
                                     int warmupPasses,
                                     int measuredPasses) throws IOException {
        Files.createDirectories(reportDir);
        Files.writeString(reportDir.resolve("summary.json"), MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(summaries));

        StringBuilder md = new StringBuilder();
        md.append("# AI Review Model Benchmark\n\n");
        md.append("- Generated at: ").append(Instant.now()).append("\n");
        md.append("- Runtime base URL: ").append(runtimeBase).append("\n");
        md.append("- Models: ").append(String.join(", ", models)).append("\n");
        md.append("- Passes: warmup=").append(warmupPasses).append(", measured=").append(measuredPasses).append("\n\n");
        md.append("## Gates\n");
        md.append("- False-approve rate on INVALID_MISMATCH <= 5%\n");
        md.append("- Precision of LIKELY_VALID >= 90%\n");
        md.append("- AI_FAILED rate <= 1%\n");
        md.append("- p95 latency <= 20s\n\n");
        md.append("| Model | False Approve | Precision(LIKELY_VALID) | AI_FAILED | p95 Latency (ms) | Gate |\n");
        md.append("|---|---:|---:|---:|---:|---|\n");
        for (ModelBenchmarkSummary summary : summaries) {
            md.append("| ").append(summary.model())
                    .append(" | ").append(percent(summary.falseApproveRate()))
                    .append(" | ").append(percent(summary.precisionLikelyValid()))
                    .append(" | ").append(percent(summary.aiFailedRate()))
                    .append(" | ").append(summary.p95LatencyMs())
                    .append(" | ").append(summary.passedGate() ? "PASS" : "FAIL")
                    .append(" |\n");
        }

        List<ModelBenchmarkSummary> passing = summaries.stream().filter(ModelBenchmarkSummary::passedGate).toList();
        md.append("\n## Recommendation\n");
        if (passing.isEmpty()) {
            md.append("No model passed all gates. Keep current model and bias to UNCLEAR.\n");
        } else {
            ModelBenchmarkSummary chosen = passing.stream()
                    .sorted(Comparator.comparingDouble(ModelBenchmarkSummary::falseApproveRate)
                            .thenComparingLong(ModelBenchmarkSummary::p95LatencyMs))
                    .findFirst()
                    .orElseThrow();
            md.append("Promote `").append(chosen.model()).append("`.\n");
        }

        Files.writeString(reportDir.resolve("summary.md"), md.toString());
    }

    private static Path prepareReportDir() throws IOException {
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now());
        Path out = Path.of("build", "reports", "ai-review-benchmark", timestamp);
        Files.createDirectories(out);
        return out;
    }

    private static String percent(double value) {
        return "%.2f%%".formatted(value * 100.0);
    }

    private static String safeName(String model) {
        return model.replace('/', '-').replace(':', '-');
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int intEnv(String key, int fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean boolEnv(String key, boolean fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) return fallback;
        return "1".equals(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
    }

    private static List<String> parseModels(String raw) {
        return List.of(raw.split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toList());
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private static String stripSuffix(String value, String suffix) {
        return value.endsWith(suffix) ? value.substring(0, value.length() - suffix.length()) : value;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }

    private static List<String> resolveImages(BenchmarkCase testCase) {
        if (testCase.imagesBase64() != null && !testCase.imagesBase64().isEmpty()) {
            return testCase.imagesBase64();
        }
        if (testCase.imagePaths() == null || testCase.imagePaths().isEmpty()) {
            return List.of();
        }

        List<String> encoded = new ArrayList<>();
        for (String imagePath : testCase.imagePaths()) {
            if (imagePath == null || imagePath.isBlank()) continue;
            try {
                byte[] bytes = Files.readAllBytes(Path.of(imagePath));
                encoded.add(Base64.getEncoder().encodeToString(bytes));
            } catch (Exception ignored) {
                // Missing images in corpus are treated as no-image for this case.
            }
        }
        return encoded;
    }

    private record BenchmarkCorpus(List<BenchmarkCase> cases) {}

    private record BenchmarkCase(
            String id,
            CaseLabel label,
            String questTitle,
            String questDescription,
            String studentComment,
            List<String> imagesBase64,
            List<String> imagePaths
    ) {}

    private enum CaseLabel { VALID_MATCH, INVALID_MISMATCH, AMBIGUOUS }

    private enum AiReviewRecommendation { LIKELY_VALID, UNCLEAR, LIKELY_INVALID, AI_FAILED }

    private record ParsedModelOutput(
            AiReviewRecommendation recommendation,
            double confidence,
            List<String> reasons
    ) {}

    private record FinalDecision(
            AiReviewRecommendation recommendation,
            double confidence,
            List<String> reasons,
            String decisionNote
    ) {}

    private record MeasuredRun(
            String model,
            String caseId,
            CaseLabel label,
            boolean warmup,
            AiReviewRecommendation recommendation,
            double confidence,
            List<String> reasons,
            String decisionNote,
            long latencyMs,
            String rawPreview,
            String error,
            String timestamp
    ) {}

    private record ModelBenchmarkSummary(
            String model,
            int measuredRuns,
            double falseApproveRate,
            double precisionLikelyValid,
            double aiFailedRate,
            long p95LatencyMs,
            boolean passFalseApproveRate,
            boolean passPrecision,
            boolean passAiFailedRate,
            boolean passLatency,
            boolean passedGate
    ) {}
}

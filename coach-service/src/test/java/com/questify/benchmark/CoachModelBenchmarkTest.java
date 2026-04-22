package com.questify.benchmark;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.questify.dto.CoachDtos.CoachSuggestionsReq;
import com.questify.dto.CoachDtos.QuestCoachContextRes;
import com.questify.dto.CoachDtos.UserCoachSettingsRes;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "OIDC_JWKS=https://issuer.test/protocol/openid-connect/certs",
        "OIDC_ISSUER=https://issuer.test/realms/questify",
        "SECURITY_INTERNAL_TOKEN=test-internal-token",
        "coach.runtime=ollama",
        "coach.timeout-ms=45000",
        "coach.max-output-tokens=260",
        "coach.temperature=0.10",
        "coach.retry-enabled=true",
        "coach.max-retries=1",
        "coach.schema-version=v1"
})
@AutoConfigureMockMvc
@Tag("coach-benchmark")
@EnabledIfSystemProperty(named = "coach.benchmark.enabled", matches = "true")
class CoachModelBenchmarkTest {

    private static final Pattern PROMETHEUS_LINE =
            Pattern.compile("^(?<name>[a-zA-Z_:][a-zA-Z0-9_:]*)(\\{(?<labels>[^}]*)\\})?\\s+(?<value>[-+0-9.eE]+)$");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9 ]");
    private static final Pattern MULTISPACE = Pattern.compile("\\s+");
    private static final String SCENARIO_RESOURCE = "benchmark/coach-model-benchmark-scenarios.json";
    private static final String BENCHMARK_USER_ID = System.getProperty("coach.benchmark.user-id", "benchmark-user");
    private static final Set<String> GOAL_STOPWORDS = Set.of(
            "i", "me", "my", "mine", "you", "your", "yours", "we", "our", "ours",
            "want", "need", "help", "please", "goal", "goals", "achieve", "achieving",
            "improve", "improving", "better", "become", "create", "creating", "quest", "quests",
            "some", "more", "less", "with", "without", "during", "through", "into", "from",
            "that", "this", "these", "those", "they", "them", "their", "for", "and", "or",
            "the", "a", "an", "to", "of", "in", "on", "at", "by", "after", "before", "over",
            "under", "still", "just", "really", "kind", "sort", "current", "easy", "medium", "hard"
    );

    private static final MockWebServer userServer = new MockWebServer();
    private static final MockWebServer questServer = new MockWebServer();
    private static boolean serversStarted;

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeAll
    static void startServers() throws IOException {
        ensureServersStarted();
    }

    @AfterAll
    static void stopServers() throws IOException {
        if (serversStarted) {
            userServer.shutdown();
            questServer.shutdown();
            serversStarted = false;
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        ensureServersStarted();
        registry.add("user.service.base", () -> userServer.url("/").toString());
        registry.add("quest.service.base", () -> questServer.url("/").toString());
        registry.add("coach.runtime-base-url", CoachModelBenchmarkTest::benchmarkRuntimeBaseUrl);
        registry.add("coach.model", CoachModelBenchmarkTest::benchmarkModel);
    }

    @Test
    void runs_model_batch_and_writes_artifacts() throws Exception {
        List<BenchmarkScenario> scenarios = loadScenarios();
        assertThat(scenarios).hasSizeGreaterThanOrEqualTo(10);
        assertThat(scenarios.stream().filter(scenario -> scenario.request().resolvedIncludeRecentHistory()).count()).isGreaterThanOrEqualTo(7);

        Path outputDir = resolveOutputDir();
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("scenario-corpus.json"), objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(scenarios), StandardCharsets.UTF_8);

        WarmupResult warmup = runWarmup(scenarios.getFirst());
        MetricsSnapshot baselineMetrics = scrapePrometheusMetrics();

        Instant startedAt = Instant.now();
        long measuredStartNanos = System.nanoTime();

        List<RequestArtifact> measuredResults = new ArrayList<>();
        int measuredRuns = Integer.getInteger("coach.benchmark.measured-runs", 5);
        for (BenchmarkScenario scenario : scenarios) {
            for (int runNumber = 1; runNumber <= measuredRuns; runNumber++) {
                RequestArtifact artifact = executeScenario(scenario, runNumber);
                assertArtifactQuality(scenario, artifact);
                measuredResults.add(artifact);
            }
        }

        long measuredDurationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - measuredStartNanos);
        Instant completedAt = Instant.now();
        MetricsSnapshot finalMetrics = scrapePrometheusMetrics();
        MetricsSnapshot deltaMetrics = finalMetrics.delta(baselineMetrics);

        assertThat(measuredResults).hasSize(scenarios.size() * measuredRuns);
        assertThat(Math.round(deltaMetrics.coachRequests())).isEqualTo(measuredResults.size());

        List<RepresentativeSample> representativeSamples = extractRepresentativeSamples(scenarios, measuredResults, outputDir.resolve("samples"));

        ModelBatchSummary summary = summarize(
                measuredResults,
                representativeSamples,
                warmup,
                deltaMetrics,
                startedAt,
                completedAt,
                measuredDurationMs
        );

        writeJsonLines(outputDir.resolve("request-results.jsonl"), measuredResults);
        writePrettyJson(outputDir.resolve("warmup.json"), warmup);
        writePrettyJson(outputDir.resolve("representative-samples.json"), representativeSamples);
        writePrettyJson(outputDir.resolve("summary.json"), summary);
        Files.writeString(
                outputDir.resolve("prometheus-snapshot.txt"),
                String.join(System.lineSeparator(), deltaMetrics.prometheusLines()) + System.lineSeparator(),
                StandardCharsets.UTF_8
        );

        assertThat(summary.automaticMetrics().successRate())
                .as("coach benchmark success rate for model %s", benchmarkModel())
                .isGreaterThanOrEqualTo(minimumSuccessRate());
    }

    private WarmupResult runWarmup(BenchmarkScenario scenario) throws Exception {
        int warmupRuns = Integer.getInteger("coach.benchmark.warmup-runs", 1);
        WarmupResult latest = null;
        for (int run = 1; run <= warmupRuns; run++) {
            RequestArtifact artifact = executeScenario(scenario, run);
            latest = new WarmupResult(
                    benchmarkModel(),
                    scenario.id(),
                    artifact.httpStatus(),
                    artifact.responseStatus(),
                    artifact.latencyMs(),
                    artifact.returnedModel(),
                    artifact.responseJson(),
                    artifact.rawResponseBody(),
                    Instant.now()
            );
        }
        if (latest == null) {
            throw new IllegalStateException("Warm-up did not run");
        }
        return latest;
    }

    private RequestArtifact executeScenario(BenchmarkScenario scenario, int runNumber) throws Exception {
        enqueueCoachSettings(scenario.coachSettings());
        enqueueQuestContext(scenario.questContext());

        long startedNanos = System.nanoTime();
        var result = mvc.perform(post("/coach/suggestions")
                        .with(jwt().jwt(token -> token.subject(BENCHMARK_USER_ID).claim("preferred_username", "benchmark-runner")))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(scenario.request())))
                .andReturn();
        long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);

        RecordedRequest userRequest = userServer.takeRequest(2, TimeUnit.SECONDS);
        RecordedRequest questRequest = questServer.takeRequest(2, TimeUnit.SECONDS);
        assertInternalUserRequest(userRequest);
        assertInternalQuestRequest(questRequest, scenario.request().resolvedIncludeRecentHistory());

        String responseBody = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode responseJson = parseJson(responseBody);
        String responseStatus = stringValue(responseJson, "status");
        String returnedModel = stringValue(responseJson, "model");
        int suggestionCount = suggestionsSize(responseJson);

        return new RequestArtifact(
                benchmarkModel(),
                scenario.id(),
                scenario.title(),
                runNumber,
                latencyMs,
                result.getResponse().getStatus(),
                responseStatus,
                suggestionCount,
                returnedModel,
                responseJson,
                responseBody
        );
    }

    private void assertArtifactQuality(BenchmarkScenario scenario, RequestArtifact artifact) {
        assertThat(artifact.httpStatus()).isEqualTo(200);
        assertThat(artifact.responseJson()).isNotNull();
        assertThat(artifact.responseStatus()).isIn("SUCCESS", "FALLBACK");
        assertThat(artifact.suggestionCount()).isBetween(1, 3);

        JsonNode responseJson = artifact.responseJson();
        String source = stringValue(responseJson, "source");
        if ("SUCCESS".equals(artifact.responseStatus())) {
            assertThat(source).isEqualTo("AI");
        }
        if ("FALLBACK".equals(artifact.responseStatus())) {
            assertThat(source).isEqualTo("SYSTEM");
        }

        JsonNode suggestions = responseJson.path("suggestions");
        assertThat(suggestions.isArray()).isTrue();

        Set<String> excludedTitles = scenario.request().resolvedExcludedSuggestionTitles().stream()
                .map(title -> title.toLowerCase(java.util.Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> seenTitles = new LinkedHashSet<>();
        List<String> goalFragments = significantGoalFragments(scenario.coachSettings().coachGoal());

        for (JsonNode suggestionNode : suggestions) {
            String title = normalizeForComparison(stringValue(suggestionNode, "title"));
            String description = normalizeForComparison(stringValue(suggestionNode, "description"));
            String reason = normalizeForComparison(stringValue(suggestionNode, "reason"));

            assertThat(title).isNotBlank();
            assertThat(description).isNotBlank();
            assertThat(seenTitles.add(title)).as("duplicate title in one response").isTrue();
            assertThat(excludedTitles).doesNotContain(title);
            assertThat(title).doesNotContain("goal").doesNotContain("toward").doesNotContain("help me");
            assertGoalEchoFree(title, goalFragments);
            assertGoalEchoFree(description, goalFragments);
            assertGoalEchoFree(reason, goalFragments);
        }
    }

    private List<BenchmarkScenario> loadScenarios() throws IOException {
        try (var input = Thread.currentThread().getContextClassLoader().getResourceAsStream(SCENARIO_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Benchmark scenario resource is missing: " + SCENARIO_RESOURCE);
            }
            return objectMapper.readValue(input, new TypeReference<List<BenchmarkScenario>>() {});
        }
    }

    private MetricsSnapshot scrapePrometheusMetrics() throws Exception {
        String body = mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        List<String> filtered = body.lines()
                .filter(line -> !line.isBlank())
                .filter(line -> relevantMetric(line))
                .toList();

        double coachRequests = 0d;
        double coachSuccess = 0d;
        double coachRetry = 0d;
        double coachTimeouts = 0d;
        Map<String, Double> fallbackByReason = new LinkedHashMap<>();
        Map<String, Double> validationFailuresByCategory = new LinkedHashMap<>();
        Map<String, Double> latencyCountByOutcome = new LinkedHashMap<>();

        for (String line : filtered) {
            Matcher matcher = PROMETHEUS_LINE.matcher(line);
            if (!matcher.matches()) {
                continue;
            }

            String metricName = matcher.group("name");
            double value = Double.parseDouble(matcher.group("value"));
            Map<String, String> labels = parseLabels(matcher.group("labels"));

            switch (metricName) {
                case "coach_requests_total" -> coachRequests = value;
                case "coach_success_total" -> coachSuccess = value;
                case "coach_retry_total" -> coachRetry = value;
                case "coach_timeouts_total" -> coachTimeouts = value;
                case "coach_fallback_total" -> fallbackByReason.put(labels.getOrDefault("reason", "unknown"), value);
                case "coach_validation_failures_total" -> validationFailuresByCategory.put(labels.getOrDefault("category", "unknown"), value);
                case "coach_latency_seconds_count" -> latencyCountByOutcome.put(labels.getOrDefault("outcome", "unknown"), value);
                default -> {
                    // ignored
                }
            }
        }

        return new MetricsSnapshot(
                coachRequests,
                coachSuccess,
                coachRetry,
                coachTimeouts,
                fallbackByReason,
                validationFailuresByCategory,
                latencyCountByOutcome,
                filtered
        );
    }

    private List<RepresentativeSample> extractRepresentativeSamples(List<BenchmarkScenario> scenarios,
                                                                   List<RequestArtifact> measuredResults,
                                                                   Path sampleDir) throws IOException {
        Files.createDirectories(sampleDir);
        Map<String, BenchmarkScenario> scenariosById = scenarios.stream()
                .collect(Collectors.toMap(BenchmarkScenario::id, scenario -> scenario, (left, right) -> left, LinkedHashMap::new));

        List<RepresentativeSample> samples = new ArrayList<>();
        Set<String> scenarioIds = measuredResults.stream().map(RequestArtifact::scenarioId).collect(Collectors.toCollection(LinkedHashSet::new));

        for (String scenarioId : scenarioIds) {
            Optional<RequestArtifact> firstSuccess = measuredResults.stream()
                    .filter(result -> Objects.equals(result.scenarioId(), scenarioId))
                    .filter(result -> "SUCCESS".equals(result.responseStatus()))
                    .min(Comparator.comparingInt(RequestArtifact::runNumber));

            if (firstSuccess.isPresent()) {
                RequestArtifact artifact = firstSuccess.get();
                Path samplePath = sampleDir.resolve(scenarioId + ".json");
                writePrettyJson(samplePath, new ManualReviewSample(
                        artifact.model(),
                        scenariosById.get(scenarioId),
                        artifact
                ));
                samples.add(new RepresentativeSample(
                        artifact.model(),
                        scenarioId,
                        artifact.scenarioTitle(),
                        true,
                        outputRelativePath(samplePath),
                        artifact.responseStatus(),
                        artifact.responseJson(),
                        artifact.rawResponseBody()
                ));
            } else {
                RequestArtifact anyArtifact = measuredResults.stream()
                        .filter(result -> Objects.equals(result.scenarioId(), scenarioId))
                        .findFirst()
                        .orElseThrow();
                samples.add(new RepresentativeSample(
                        anyArtifact.model(),
                        scenarioId,
                        anyArtifact.scenarioTitle(),
                        false,
                        null,
                        "NO_SUCCESS_SAMPLE",
                        null,
                        null
                ));
            }
        }

        return samples;
    }

    private ModelBatchSummary summarize(List<RequestArtifact> measuredResults,
                                        List<RepresentativeSample> representativeSamples,
                                        WarmupResult warmup,
                                        MetricsSnapshot deltaMetrics,
                                        Instant startedAt,
                                        Instant completedAt,
                                        long measuredDurationMs) {
        int requestCount = measuredResults.size();
        long successCount = measuredResults.stream().filter(result -> "SUCCESS".equals(result.responseStatus())).count();
        long fallbackCount = measuredResults.stream().filter(result -> "FALLBACK".equals(result.responseStatus())).count();
        List<Long> latencies = measuredResults.stream().map(RequestArtifact::latencyMs).sorted().toList();

        double validationFailures = deltaMetrics.validationFailuresByCategory().values().stream().mapToDouble(Double::doubleValue).sum();
        double fallbackMetricCount = deltaMetrics.fallbackByReason().values().stream().mapToDouble(Double::doubleValue).sum();

        AutomaticMetrics automaticMetrics = new AutomaticMetrics(
                percentage(successCount, requestCount),
                percentage(fallbackCount, requestCount),
                percentage(deltaMetrics.coachRetry(), requestCount),
                percentage(deltaMetrics.coachTimeouts(), requestCount),
                percentage(validationFailures, requestCount),
                percentileMillis(latencies, 0.50),
                percentileMillis(latencies, 0.95),
                warmup.latencyMs(),
                null,
                measuredDurationMs
        );

        return new ModelBatchSummary(
                benchmarkModel(),
                requestCount,
                startedAt,
                completedAt,
                warmup,
                automaticMetrics,
                new ActuatorMetricsDelta(
                        deltaMetrics.coachRequests(),
                        deltaMetrics.coachSuccess(),
                        deltaMetrics.coachRetry(),
                        deltaMetrics.coachTimeouts(),
                        fallbackMetricCount,
                        deltaMetrics.fallbackByReason(),
                        validationFailures,
                        deltaMetrics.validationFailuresByCategory(),
                        deltaMetrics.latencyCountByOutcome(),
                        deltaMetrics.prometheusLines()
                ),
                representativeSamples
        );
    }

    private void enqueueCoachSettings(UserCoachSettingsRes settings) throws IOException {
        userServer.enqueue(jsonResponse(settings));
    }

    private void enqueueQuestContext(QuestCoachContextRes questContext) throws IOException {
        questServer.enqueue(jsonResponse(questContext));
    }

    private MockResponse jsonResponse(Object body) throws IOException {
        return new MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(objectMapper.writeValueAsString(body));
    }

    private static JsonNode parseJson(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return new ObjectMapper().readTree(payload);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String stringValue(JsonNode node, String fieldName) {
        if (node == null || !node.hasNonNull(fieldName)) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        return value.isTextual() ? value.asText() : value.toString();
    }

    private static int suggestionsSize(JsonNode node) {
        if (node == null || !node.has("suggestions") || !node.get("suggestions").isArray()) {
            return 0;
        }
        return node.get("suggestions").size();
    }

    private static boolean relevantMetric(String line) {
        return line.startsWith("coach_requests_total")
                || line.startsWith("coach_success_total")
                || line.startsWith("coach_retry_total")
                || line.startsWith("coach_timeouts_total")
                || line.startsWith("coach_fallback_total")
                || line.startsWith("coach_validation_failures_total")
                || line.startsWith("coach_latency_seconds_count");
    }

    private static Map<String, String> parseLabels(String rawLabels) {
        if (rawLabels == null || rawLabels.isBlank()) {
            return Map.of();
        }

        Map<String, String> labels = new LinkedHashMap<>();
        for (String token : rawLabels.split(",")) {
            String[] parts = token.split("=", 2);
            if (parts.length != 2) {
                continue;
            }
            String key = parts[0].trim();
            String value = parts[1].trim();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            }
            labels.put(key, value);
        }
        return labels;
    }

    private static double percentage(double numerator, double denominator) {
        if (denominator <= 0d) {
            return 0d;
        }
        return round((numerator * 100d) / denominator);
    }

    private static double percentileMillis(List<Long> sortedLatencies, double percentile) {
        if (sortedLatencies.isEmpty()) {
            return 0d;
        }
        int index = (int) Math.ceil(percentile * sortedLatencies.size()) - 1;
        int safeIndex = Math.max(0, Math.min(sortedLatencies.size() - 1, index));
        return round(sortedLatencies.get(safeIndex));
    }

    private static double round(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }

    private static void writeJsonLines(Path path, List<RequestArtifact> results) throws IOException {
        Files.createDirectories(path.getParent());
        StringBuilder builder = new StringBuilder();
        ObjectMapper mapper = new ObjectMapper();
        for (RequestArtifact result : results) {
            builder.append(mapper.writeValueAsString(result)).append(System.lineSeparator());
        }
        Files.writeString(path, builder.toString(), StandardCharsets.UTF_8);
    }

    private void writePrettyJson(Path path, Object value) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value), StandardCharsets.UTF_8);
    }

    private static Path resolveOutputDir() {
        String explicit = System.getProperty("coach.benchmark.output-dir");
        if (explicit != null && !explicit.isBlank()) {
            return Path.of(explicit).toAbsolutePath().normalize();
        }
        String sanitizedModel = sanitizeForPath(benchmarkModel());
        return Path.of("build", "reports", "coach-benchmark", sanitizedModel).toAbsolutePath().normalize();
    }

    private static String benchmarkRuntimeBaseUrl() {
        return System.getProperty("coach.benchmark.runtime-base-url", "http://127.0.0.1:11434");
    }

    private static String benchmarkModel() {
        return System.getProperty("coach.benchmark.model", "qwen2.5:3b");
    }

    private static double minimumSuccessRate() {
        return Double.parseDouble(System.getProperty("coach.benchmark.minimum-success-rate", "80"));
    }

    private static String sanitizeForPath(String value) {
        return value.replaceAll("[:/\\\\]", "-");
    }

    private static String outputRelativePath(Path path) {
        Path absolutePath = path.toAbsolutePath().normalize();
        Path root = resolveOutputDir().getParent();
        if (root != null && absolutePath.startsWith(root)) {
            return root.relativize(absolutePath).toString().replace('\\', '/');
        }
        return absolutePath.toString().replace('\\', '/');
    }

    private static void assertGoalEchoFree(String text, List<String> goalFragments) {
        for (String fragment : goalFragments) {
            assertThat(text)
                    .as("text should not directly echo goal fragment '%s'", fragment)
                    .doesNotContain(fragment);
        }
    }

    private static List<String> significantGoalFragments(String goal) {
        String normalized = normalizeForComparison(goal);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<String> tokens = List.of(normalized.split(" ")).stream()
                .filter(token -> !token.isBlank())
                .filter(token -> !GOAL_STOPWORDS.contains(token))
                .toList();

        if (tokens.size() < 3) {
            return List.of();
        }

        Set<String> fragments = new LinkedHashSet<>();
        for (int index = 0; index <= tokens.size() - 3; index++) {
            String fragment = String.join(" ", tokens.subList(index, index + 3));
            if (fragment.length() >= 16) {
                fragments.add(fragment);
            }
        }
        return List.copyOf(fragments);
    }

    private static String normalizeForComparison(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String lowered = value.toLowerCase(java.util.Locale.ROOT);
        return MULTISPACE.matcher(NON_ALPHANUMERIC.matcher(lowered).replaceAll(" ")).replaceAll(" ").trim();
    }

    private static void assertInternalUserRequest(RecordedRequest request) {
        assertThat(request).isNotNull();
        assertThat(request.getHeader("X-Internal-Token")).isEqualTo("test-internal-token");
        assertThat(request.getPath()).isEqualTo("/internal/users/" + BENCHMARK_USER_ID + "/coach-settings");
    }

    private static void assertInternalQuestRequest(RecordedRequest request, boolean includeRecentHistory) {
        assertThat(request).isNotNull();
        assertThat(request.getHeader("X-Internal-Token")).isEqualTo("test-internal-token");
        assertThat(request.getPath()).isEqualTo("/internal/users/" + BENCHMARK_USER_ID + "/coach-context?includeRecentHistory=" + includeRecentHistory);
    }

    private static synchronized void ensureServersStarted() {
        try {
            if (!serversStarted) {
                userServer.start();
                questServer.start();
                serversStarted = true;
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to start benchmark mock servers", ex);
        }
    }

    record BenchmarkScenario(
            String id,
            String title,
            String description,
            CoachSuggestionsReq request,
            UserCoachSettingsRes coachSettings,
            QuestCoachContextRes questContext
    ) {}

    record RequestArtifact(
            String model,
            String scenarioId,
            String scenarioTitle,
            int runNumber,
            long latencyMs,
            int httpStatus,
            String responseStatus,
            int suggestionCount,
            String returnedModel,
            JsonNode responseJson,
            String rawResponseBody
    ) {}

    record WarmupResult(
            String model,
            String scenarioId,
            int httpStatus,
            String responseStatus,
            long latencyMs,
            String returnedModel,
            JsonNode responseJson,
            String rawResponseBody,
            Instant completedAt
    ) {}

    record RepresentativeSample(
            String model,
            String scenarioId,
            String scenarioTitle,
            boolean hasSuccessSample,
            String sampleFile,
            String responseStatus,
            JsonNode responseJson,
            String rawResponseBody
    ) {}

    record ManualReviewSample(
            String model,
            BenchmarkScenario scenario,
            RequestArtifact requestArtifact
    ) {}

    record AutomaticMetrics(
            double successRate,
            double fallbackRate,
            double retryRate,
            double timeoutRate,
            double validationFailureRate,
            double p50LatencyMs,
            double p95LatencyMs,
            double coldStartLatencyMs,
            Double peakWorkingSetMb,
            long totalBatchDurationMs
    ) {}

    record ActuatorMetricsDelta(
            double coachRequests,
            double coachSuccess,
            double coachRetry,
            double coachTimeouts,
            double coachFallback,
            Map<String, Double> fallbackByReason,
            double coachValidationFailures,
            Map<String, Double> validationFailuresByCategory,
            Map<String, Double> latencyCountByOutcome,
            List<String> prometheusLines
    ) {}

    record ModelBatchSummary(
            String model,
            int measuredRequestCount,
            Instant startedAt,
            Instant completedAt,
            WarmupResult warmup,
            AutomaticMetrics automaticMetrics,
            ActuatorMetricsDelta actuatorMetrics,
            List<RepresentativeSample> representativeSamples
    ) {}

    record MetricsSnapshot(
            double coachRequests,
            double coachSuccess,
            double coachRetry,
            double coachTimeouts,
            Map<String, Double> fallbackByReason,
            Map<String, Double> validationFailuresByCategory,
            Map<String, Double> latencyCountByOutcome,
            List<String> prometheusLines
    ) {
        MetricsSnapshot delta(MetricsSnapshot baseline) {
            return new MetricsSnapshot(
                    coachRequests - baseline.coachRequests,
                    coachSuccess - baseline.coachSuccess,
                    coachRetry - baseline.coachRetry,
                    coachTimeouts - baseline.coachTimeouts,
                    subtractTaggedMap(fallbackByReason, baseline.fallbackByReason),
                    subtractTaggedMap(validationFailuresByCategory, baseline.validationFailuresByCategory),
                    subtractTaggedMap(latencyCountByOutcome, baseline.latencyCountByOutcome),
                    prometheusLines
            );
        }
    }

    private static Map<String, Double> subtractTaggedMap(Map<String, Double> current, Map<String, Double> baseline) {
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(current.keySet());
        keys.addAll(baseline.keySet());

        Map<String, Double> delta = new LinkedHashMap<>();
        for (String key : keys) {
            double value = current.getOrDefault(key, 0d) - baseline.getOrDefault(key, 0d);
            if (Math.abs(value) > 0.0001d) {
                delta.put(key, round(value));
            }
        }
        return delta;
    }
}

package br.com.atlaslab.benchmark;

import br.com.atlaslab.ollama.ModelRegistry;
import br.com.atlaslab.ollama.OllamaClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Comparator;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class BenchmarkService {
    private final BenchmarkStore store;
    private final BenchmarkSuite suite;
    private final ModelRegistry modelRegistry;
    private final OllamaClient ollamaClient;
    private final AtomicBoolean running = new AtomicBoolean();

    BenchmarkService(BenchmarkStore store, BenchmarkSuite suite,
                     ModelRegistry modelRegistry, OllamaClient ollamaClient) {
        this.store = store;
        this.suite = suite;
        this.modelRegistry = modelRegistry;
        this.ollamaClient = ollamaClient;
    }

    public ResultView run(String requestedModel, String promptId, int iteration) {
        String model = modelRegistry.resolve(requestedModel);
        BenchmarkSuite.Prompt prompt = suite.find(promptId);
        if (!running.compareAndSet(false, true)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um benchmark em execução");
        }
        try {
            if (iteration == 1) {
                ollamaClient.unload(model);
            }
            long startedAt = System.nanoTime();
            AtomicLong firstTokenAt = new AtomicLong();
            AtomicReference<OllamaClient.OllamaStats> finalStats = new AtomicReference<>();
            StringBuilder answer = new StringBuilder();
            ollamaClient.streamChat(model, List.of(new OllamaClient.ChatMessage("user", prompt.text())), chunk -> {
                if (chunk.content() != null && !chunk.content().isEmpty()) {
                    firstTokenAt.compareAndSet(0, System.nanoTime());
                    answer.append(chunk.content());
                }
                if (chunk.done()) finalStats.set(chunk.stats());
            });
            if (answer.isEmpty()) {
                throw new br.com.atlaslab.ollama.OllamaException(
                        "O modelo concluiu a inferência sem produzir uma resposta textual.");
            }
            long totalDurationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            Long ttft = firstTokenAt.get() == 0 ? null
                    : TimeUnit.NANOSECONDS.toMillis(firstTokenAt.get() - startedAt);
            OllamaClient.OllamaStats stats = finalStats.get();
            BenchmarkResult result = new BenchmarkResult(model, prompt, iteration, answer.toString(),
                    ttft, totalDurationMs, millis(stats == null ? null : stats.loadDurationNanos()),
                    tokensPerSecond(stats), stats == null ? null : stats.promptTokens(),
                    stats == null ? null : stats.completionTokens());
            return view(store.save(result));
        } finally {
            running.set(false);
        }
    }

    @Transactional(readOnly = true)
    public List<ResultView> results() {
        return store.list().stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public SummaryResponse summary() {
        Map<String, List<BenchmarkResult>> byModel = store.list().stream()
                .filter(result -> result.answer() != null && !result.answer().isBlank())
                .collect(java.util.stream.Collectors.groupingBy(
                        BenchmarkResult::model, LinkedHashMap::new, java.util.stream.Collectors.toList()));

        List<SummaryDraft> drafts = byModel.entrySet().stream()
                .map(entry -> draft(entry.getKey(), entry.getValue()))
                .toList();
        double minSpeed = drafts.stream().map(SummaryDraft::averageTokensPerSecond)
                .filter(java.util.Objects::nonNull).min(Double::compareTo).orElse(0d);
        double maxSpeed = drafts.stream().map(SummaryDraft::averageTokensPerSecond)
                .filter(java.util.Objects::nonNull).max(Double::compareTo).orElse(0d);
        double minTtft = drafts.stream().map(SummaryDraft::averageWarmTimeToFirstTokenMs)
                .filter(java.util.Objects::nonNull).min(Double::compareTo).orElse(0d);
        double maxTtft = drafts.stream().map(SummaryDraft::averageWarmTimeToFirstTokenMs)
                .filter(java.util.Objects::nonNull).max(Double::compareTo).orElse(0d);
        double minDuration = drafts.stream().map(SummaryDraft::averageWarmDurationMs)
                .filter(java.util.Objects::nonNull).min(Double::compareTo).orElse(0d);
        double maxDuration = drafts.stream().map(SummaryDraft::averageWarmDurationMs)
                .filter(java.util.Objects::nonNull).max(Double::compareTo).orElse(0d);

        List<ModelSummary> models = drafts.stream().map(draft -> {
            double quality = draft.averageQuality() == null ? 0d : draft.averageQuality() / 5d;
            double performance = quality * .55
                    + normalize(draft.averageTokensPerSecond(), minSpeed, maxSpeed, false) * .25
                    + normalize(draft.averageWarmTimeToFirstTokenMs(), minTtft, maxTtft, true) * .10
                    + normalize(draft.averageWarmDurationMs(), minDuration, maxDuration, true) * .10;
            double coverage = Math.min(draft.validRuns() / 18d, 1d);
            double score = performance * (.7d + .3d * coverage) * 100d;
            return new ModelSummary(draft.model(), draft.validRuns(), draft.evaluatedRuns(),
                    round(coverage * 100d), draft.averageQuality(), draft.averageTimeToFirstTokenMs(),
                    draft.averageColdDurationMs(), draft.averageWarmDurationMs(),
                    draft.averageTokensPerSecond(), round(score), false);
        }).sorted(Comparator.comparing(ModelSummary::balanceScore).reversed()).toList();

        String recommended = models.stream().filter(model -> model.evaluatedRuns() > 0)
                .map(ModelSummary::model).findFirst().orElse(null);
        List<ModelSummary> ranked = models.stream()
                .map(model -> new ModelSummary(model.model(), model.validRuns(), model.evaluatedRuns(),
                        model.coveragePercent(), model.averageQuality(), model.averageTimeToFirstTokenMs(),
                        model.averageColdDurationMs(), model.averageWarmDurationMs(),
                        model.averageTokensPerSecond(), model.balanceScore(), model.model().equals(recommended)))
                .toList();
        return new SummaryResponse(ranked, recommended,
                "Qualidade 55%, velocidade 25%, primeiro token aquecido 10%, duração aquecida 10%; cobertura reduz a confiança do índice.");
    }

    @Transactional
    public ResultView evaluate(UUID id, int score, String notes) {
        BenchmarkResult result = store.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resultado não encontrado"));
        result.evaluate(score, notes);
        return view(store.save(result));
    }

    private Long millis(Long nanos) {
        return nanos == null ? null : TimeUnit.NANOSECONDS.toMillis(nanos);
    }

    private Double tokensPerSecond(OllamaClient.OllamaStats stats) {
        if (stats == null || stats.completionTokens() == null || stats.evalDurationNanos() == null
                || stats.evalDurationNanos() <= 0) return null;
        return Math.round(stats.completionTokens() * 100_000_000_000d / stats.evalDurationNanos()) / 100d;
    }

    private SummaryDraft draft(String model, List<BenchmarkResult> results) {
        List<BenchmarkResult> cold = results.stream().filter(BenchmarkResult::coldStart).toList();
        List<BenchmarkResult> warm = results.stream().filter(result -> !result.coldStart()).toList();
        return new SummaryDraft(model, results.size(),
                (int) results.stream().filter(result -> result.qualityScore() != null).count(),
                average(results.stream().map(BenchmarkResult::qualityScore).toList()),
                average(results.stream().map(BenchmarkResult::timeToFirstTokenMs).toList()),
                average(warm.stream().map(BenchmarkResult::timeToFirstTokenMs).toList()),
                average(cold.stream().map(BenchmarkResult::totalDurationMs).toList()),
                average(warm.stream().map(BenchmarkResult::totalDurationMs).toList()),
                average(results.stream().map(BenchmarkResult::tokensPerSecond).toList()));
    }

    private Double average(List<? extends Number> values) {
        return round(values.stream().filter(java.util.Objects::nonNull)
                .mapToDouble(Number::doubleValue).average().orElse(Double.NaN));
    }

    private double normalize(Double value, double min, double max, boolean lowerIsBetter) {
        if (value == null || value.isNaN()) return 0d;
        if (max == min) return 1d;
        double normalized = (value - min) / (max - min);
        return lowerIsBetter ? 1d - normalized : normalized;
    }

    private Double round(double value) {
        if (Double.isNaN(value)) return null;
        return Math.round(value * 100d) / 100d;
    }

    private ResultView view(BenchmarkResult result) {
        return new ResultView(result.id(), result.model(), result.promptId(), result.promptTitle(),
                result.iteration(), result.coldStart(), result.answer(), result.timeToFirstTokenMs(),
                result.totalDurationMs(), result.loadDurationMs(), result.tokensPerSecond(),
                result.promptTokens(), result.completionTokens(), result.qualityScore(),
                result.evaluationNotes(), result.createdAt());
    }

    public record ResultView(UUID id, String model, String promptId, String promptTitle,
                             int iteration, boolean coldStart, String answer,
                             Long timeToFirstTokenMs, long totalDurationMs, Long loadDurationMs,
                             Double tokensPerSecond, Integer promptTokens, Integer completionTokens,
                             Integer qualityScore, String evaluationNotes, java.time.Instant createdAt) {}

    private record SummaryDraft(String model, int validRuns, int evaluatedRuns,
                                Double averageQuality, Double averageTimeToFirstTokenMs,
                                Double averageWarmTimeToFirstTokenMs, Double averageColdDurationMs,
                                Double averageWarmDurationMs, Double averageTokensPerSecond) {}

    public record ModelSummary(String model, int validRuns, int evaluatedRuns, Double coveragePercent,
                               Double averageQuality, Double averageTimeToFirstTokenMs,
                               Double averageColdDurationMs, Double averageWarmDurationMs,
                               Double averageTokensPerSecond, Double balanceScore, boolean recommended) {}

    public record SummaryResponse(List<ModelSummary> models, String recommendedModel, String methodology) {}
}

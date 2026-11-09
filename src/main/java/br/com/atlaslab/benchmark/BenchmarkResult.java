package br.com.atlaslab.benchmark;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "benchmark_results")
class BenchmarkResult {
    @Id
    private UUID id;
    private String model;
    private String promptId;
    private String promptTitle;
    private String promptText;
    private int iteration;
    private boolean coldStart;
    private String answer;
    private Long timeToFirstTokenMs;
    private long totalDurationMs;
    private Long loadDurationMs;
    private Double tokensPerSecond;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer qualityScore;
    private String evaluationNotes;
    private Instant createdAt;

    protected BenchmarkResult() {}

    BenchmarkResult(String model, BenchmarkSuite.Prompt prompt, int iteration, String answer,
                    Long timeToFirstTokenMs, long totalDurationMs, Long loadDurationMs,
                    Double tokensPerSecond, Integer promptTokens, Integer completionTokens) {
        this.id = UUID.randomUUID();
        this.model = model;
        this.promptId = prompt.id();
        this.promptTitle = prompt.title();
        this.promptText = prompt.text();
        this.iteration = iteration;
        this.coldStart = iteration == 1;
        this.answer = answer;
        this.timeToFirstTokenMs = timeToFirstTokenMs;
        this.totalDurationMs = totalDurationMs;
        this.loadDurationMs = loadDurationMs;
        this.tokensPerSecond = tokensPerSecond;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.createdAt = Instant.now();
    }

    void evaluate(int score, String notes) {
        this.qualityScore = score;
        this.evaluationNotes = notes == null || notes.isBlank() ? null : notes.trim();
    }

    UUID id() { return id; }
    String model() { return model; }
    String promptId() { return promptId; }
    String promptTitle() { return promptTitle; }
    int iteration() { return iteration; }
    boolean coldStart() { return coldStart; }
    String answer() { return answer; }
    Long timeToFirstTokenMs() { return timeToFirstTokenMs; }
    long totalDurationMs() { return totalDurationMs; }
    Long loadDurationMs() { return loadDurationMs; }
    Double tokensPerSecond() { return tokensPerSecond; }
    Integer promptTokens() { return promptTokens; }
    Integer completionTokens() { return completionTokens; }
    Integer qualityScore() { return qualityScore; }
    String evaluationNotes() { return evaluationNotes; }
    Instant createdAt() { return createdAt; }
}

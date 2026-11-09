package br.com.atlaslab.benchmark;

import br.com.atlaslab.ollama.ModelRegistry;
import br.com.atlaslab.ollama.OllamaClient;
import br.com.atlaslab.ollama.OllamaProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class BenchmarkServiceTest {
    private InMemoryStore store;
    private FakeOllamaClient ollama;
    private BenchmarkService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
        ollama = new FakeOllamaClient();
        var properties = new OllamaProperties(URI.create("http://127.0.0.1:11434"),
                "qwen3:4b-instruct", List.of("qwen3:4b-instruct", "qwen3.5:9b"),
                Duration.ofSeconds(2), Duration.ofSeconds(120));
        service = new BenchmarkService(store, new BenchmarkSuite(),
                new ModelRegistry(properties), ollama);
    }

    @Test
    void coldRunUnloadsModelAndPersistsMetrics() {
        var result = service.run("qwen3.5:9b", "pt-explanation", 1);

        assertThat(ollama.unloaded).isTrue();
        assertThat(result.model()).isEqualTo("qwen3.5:9b");
        assertThat(result.coldStart()).isTrue();
        assertThat(result.answer()).isEqualTo("Resposta completa");
        assertThat(result.loadDurationMs()).isEqualTo(800);
        assertThat(result.tokensPerSecond()).isEqualTo(100.0);
        assertThat(store.results).hasSize(1);
    }

    private static final class InMemoryStore implements BenchmarkStore {
        private final java.util.ArrayList<BenchmarkResult> results = new java.util.ArrayList<>();
        public BenchmarkResult save(BenchmarkResult result) {
            results.removeIf(existing -> existing.id().equals(result.id()));
            results.add(result);
            return result;
        }
        public List<BenchmarkResult> list() { return List.copyOf(results); }
        public java.util.Optional<BenchmarkResult> find(java.util.UUID id) {
            return results.stream().filter(result -> result.id().equals(id)).findFirst();
        }
    }

    @Test
    void warmRunKeepsModelLoaded() {
        var result = service.run("qwen3:4b-instruct", "java-code", 2);

        assertThat(ollama.unloaded).isFalse();
        assertThat(result.coldStart()).isFalse();
        assertThat(result.iteration()).isEqualTo(2);
    }

    @Test
    void doesNotPersistAnEmptyModelResponse() {
        ollama.emptyResponse = true;

        assertThatThrownBy(() -> service.run("qwen3.5:9b", "pt-explanation", 2))
                .isInstanceOf(br.com.atlaslab.ollama.OllamaException.class)
                .hasMessageContaining("sem produzir uma resposta textual");
        assertThat(store.results).isEmpty();
    }

    @Test
    void summarizesValidResultsAndRecommendsTheBestBalance() {
        var prompt = new BenchmarkSuite.Prompt("test", "Teste", "Prompt");
        var slower = new BenchmarkResult("qwen3:4b-instruct", prompt, 2, "Resposta A",
                900L, 4_000L, 10L, 20d, 10, 20);
        slower.evaluate(4, null);
        var faster = new BenchmarkResult("qwen3.5:9b", prompt, 2, "Resposta B",
                200L, 1_500L, 10L, 80d, 10, 20);
        faster.evaluate(4, null);
        var invalid = new BenchmarkResult("qwen3.5:9b", prompt, 1, "",
                null, 10_000L, 8_000L, 1d, 10, 0);
        invalid.evaluate(5, null);
        store.save(slower);
        store.save(faster);
        store.save(invalid);

        var summary = service.summary();

        assertThat(summary.recommendedModel()).isEqualTo("qwen3.5:9b");
        assertThat(summary.models()).hasSize(2);
        assertThat(summary.models().getFirst().recommended()).isTrue();
        assertThat(summary.models().getFirst().validRuns()).isEqualTo(1);
        assertThat(summary.models().getFirst().averageQuality()).isEqualTo(4d);
    }

    private static final class FakeOllamaClient extends OllamaClient {
        private boolean unloaded;
        private boolean emptyResponse;

        FakeOllamaClient() {
            super(RestClient.create(), new OllamaProperties(URI.create("http://127.0.0.1:11434"),
                    "qwen3:4b-instruct", List.of("qwen3:4b-instruct"),
                    Duration.ofSeconds(2), Duration.ofSeconds(120)), new ObjectMapper());
        }

        @Override
        public void unload(String model) { unloaded = true; }

        @Override
        public void streamChat(String model, List<ChatMessage> messages, Consumer<StreamChunk> onChunk) {
            if (emptyResponse) {
                onChunk.accept(new StreamChunk(model, "", true,
                        new OllamaStats(2_000_000_000L, 0L, 20, 0, 1L)));
                return;
            }
            onChunk.accept(new StreamChunk(model, "Resposta ", false));
            onChunk.accept(new StreamChunk(model, "completa", false));
            onChunk.accept(new StreamChunk(model, "", true,
                    new OllamaStats(2_000_000_000L, 800_000_000L, 20, 10, 100_000_000L)));
        }
    }
}

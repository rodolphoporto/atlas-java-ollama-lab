package br.com.atlaslab.api;

import br.com.atlaslab.ollama.OllamaClient;
import br.com.atlaslab.ollama.OllamaException;
import br.com.atlaslab.ollama.ModelRegistry;
import br.com.atlaslab.history.ConversationStore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/ai")
public class ChatController {

    private final OllamaClient ollamaClient;
    private final ObjectMapper objectMapper;
    private final ConversationStore conversationStore;
    private final ModelRegistry modelRegistry;

    public ChatController(OllamaClient ollamaClient, ObjectMapper objectMapper,
                          ConversationStore conversationStore, ModelRegistry modelRegistry) {
        this.ollamaClient = ollamaClient;
        this.objectMapper = objectMapper;
        this.conversationStore = conversationStore;
        this.modelRegistry = modelRegistry;
    }

    @GetMapping("/models")
    public ModelsResponse models() {
        return new ModelsResponse(modelRegistry.defaultModel(), modelRegistry.allowedModels());
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        String model = modelRegistry.resolve(request.model());
        UUID conversationId = conversationStore.appendUserMessage(request.conversationId(), request.message());
        List<OllamaClient.ChatMessage> context = context(conversationId);
        long startedAt = System.nanoTime();
        OllamaClient.ChatResult result = ollamaClient.chat(model, context);
        conversationStore.appendAssistantMessage(conversationId, result.content());
        return new ChatResponse(result.model(), result.content(), conversationId,
                metrics(startedAt, null, context, result.stats()));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> stream(@Valid @RequestBody ChatRequest request) {
        String model = modelRegistry.resolve(request.model());
        StreamingResponseBody body = output -> {
            UUID conversationId = conversationStore.appendUserMessage(request.conversationId(), request.message());
            writeEvent(output, "conversation", new ConversationStarted(conversationId));
            StringBuilder completeAnswer = new StringBuilder();
            List<OllamaClient.ChatMessage> context = context(conversationId);
            long startedAt = System.nanoTime();
            AtomicLong firstTokenAt = new AtomicLong();
            try {
                ollamaClient.streamChat(model, context, chunk -> {
                    try {
                        if (chunk.done()) {
                            conversationStore.appendAssistantMessage(conversationId, completeAnswer.toString());
                            writeEvent(output, "done", new StreamDone(chunk.model(), true,
                                    metrics(startedAt, firstTokenAt.get(), context, chunk.stats())));
                        } else if (chunk.content() != null && !chunk.content().isEmpty()) {
                            firstTokenAt.compareAndSet(0, System.nanoTime());
                            completeAnswer.append(chunk.content());
                            writeEvent(output, "token", new StreamToken(chunk.model(), chunk.content()));
                        }
                    } catch (IOException exception) {
                        throw new StreamWriteException(exception);
                    }
                });
            } catch (StreamWriteException exception) {
                throw exception.ioException();
            } catch (OllamaException exception) {
                writeEvent(output, "error", new StreamError(exception.getMessage()));
            }
        };
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "event-stream", java.nio.charset.StandardCharsets.UTF_8))
                .body(body);
    }

    private void writeEvent(OutputStream output, String event, Object data) throws IOException {
        output.write(("event: " + event + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        output.write(("data: " + objectMapper.writeValueAsString(data) + "\n\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        output.flush();
    }

    private List<OllamaClient.ChatMessage> context(UUID conversationId) {
        return conversationStore.loadContext(conversationId).stream()
                .map(message -> new OllamaClient.ChatMessage(message.role(), message.content()))
                .toList();
    }

    private InferenceMetrics metrics(long startedAt, Long firstTokenAt,
                                     List<OllamaClient.ChatMessage> context,
                                     OllamaClient.OllamaStats stats) {
        long finishedAt = System.nanoTime();
        Long timeToFirstTokenMs = firstTokenAt == null || firstTokenAt == 0
                ? null
                : TimeUnit.NANOSECONDS.toMillis(firstTokenAt - startedAt);
        long totalDurationMs = TimeUnit.NANOSECONDS.toMillis(finishedAt - startedAt);
        Integer promptTokens = stats == null ? null : stats.promptTokens();
        Integer completionTokens = stats == null ? null : stats.completionTokens();
        Double tokensPerSecond = stats == null ? null : tokensPerSecond(stats);
        int contextCharacters = context.stream().mapToInt(message -> message.content().length()).sum();
        return new InferenceMetrics(timeToFirstTokenMs, totalDurationMs, tokensPerSecond,
                promptTokens, completionTokens, context.size(), contextCharacters);
    }

    private Double tokensPerSecond(OllamaClient.OllamaStats stats) {
        if (stats.completionTokens() == null || stats.evalDurationNanos() == null
                || stats.evalDurationNanos() <= 0) {
            return null;
        }
        double rate = stats.completionTokens() * 1_000_000_000d / stats.evalDurationNanos();
        return Math.round(rate * 100d) / 100d;
    }

    public record ChatRequest(@NotBlank(message = "message não pode estar vazio")
                              @Size(max = 8000, message = "message deve ter no máximo 8000 caracteres") String message,
                              UUID conversationId,
                              @Size(max = 100, message = "model deve ter no máximo 100 caracteres") String model) {
    }

    public record ChatResponse(String model, String answer, UUID conversationId,
                               InferenceMetrics metrics) {
    }

    public record StreamToken(String model, String content) {
    }

    public record StreamDone(String model, boolean done, InferenceMetrics metrics) {
    }

    public record InferenceMetrics(Long timeToFirstTokenMs, long totalDurationMs,
                                   Double tokensPerSecond, Integer promptTokens,
                                   Integer completionTokens, int contextMessages,
                                   int contextCharacters) {
    }

    public record StreamError(String message) {
    }

    public record ConversationStarted(UUID conversationId) {
    }

    public record ModelsResponse(String defaultModel, List<String> models) {
    }

    private static final class StreamWriteException extends RuntimeException {
        private final IOException ioException;

        private StreamWriteException(IOException ioException) {
            super(ioException);
            this.ioException = ioException;
        }

        private IOException ioException() {
            return ioException;
        }
    }
}


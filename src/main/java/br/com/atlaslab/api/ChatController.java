package br.com.atlaslab.api;

import br.com.atlaslab.ollama.OllamaClient;
import br.com.atlaslab.ollama.OllamaException;
import br.com.atlaslab.history.ConversationStore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/ai")
public class ChatController {

    private final OllamaClient ollamaClient;
    private final ObjectMapper objectMapper;
    private final ConversationStore conversationStore;

    public ChatController(OllamaClient ollamaClient, ObjectMapper objectMapper,
                          ConversationStore conversationStore) {
        this.ollamaClient = ollamaClient;
        this.objectMapper = objectMapper;
        this.conversationStore = conversationStore;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        UUID conversationId = conversationStore.appendUserMessage(request.conversationId(), request.message());
        ChatResponse response = ollamaClient.chat(context(conversationId));
        conversationStore.appendAssistantMessage(conversationId, response.answer());
        return new ChatResponse(response.model(), response.answer(), conversationId);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> stream(@Valid @RequestBody ChatRequest request) {
        StreamingResponseBody body = output -> {
            UUID conversationId = conversationStore.appendUserMessage(request.conversationId(), request.message());
            writeEvent(output, "conversation", new ConversationStarted(conversationId));
            StringBuilder completeAnswer = new StringBuilder();
            try {
                ollamaClient.streamChat(context(conversationId), chunk -> {
                    try {
                        if (chunk.done()) {
                            conversationStore.appendAssistantMessage(conversationId, completeAnswer.toString());
                            writeEvent(output, "done", new StreamDone(chunk.model(), true));
                        } else if (chunk.content() != null && !chunk.content().isEmpty()) {
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

    public record ChatRequest(@NotBlank(message = "message não pode estar vazio")
                              @Size(max = 8000, message = "message deve ter no máximo 8000 caracteres") String message,
                              UUID conversationId) {
    }

    public record ChatResponse(String model, String answer, UUID conversationId) {
        public ChatResponse(String model, String answer) {
            this(model, answer, null);
        }
    }

    public record StreamToken(String model, String content) {
    }

    public record StreamDone(String model, boolean done) {
    }

    public record StreamError(String message) {
    }

    public record ConversationStarted(UUID conversationId) {
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


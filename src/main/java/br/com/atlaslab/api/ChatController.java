package br.com.atlaslab.api;

import br.com.atlaslab.ollama.OllamaClient;
import br.com.atlaslab.ollama.OllamaException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

@RestController
@RequestMapping("/api/ai")
public class ChatController {

    private final OllamaClient ollamaClient;
    private final ObjectMapper objectMapper;

    public ChatController(OllamaClient ollamaClient, ObjectMapper objectMapper) {
        this.ollamaClient = ollamaClient;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return ollamaClient.chat(request.message());
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> stream(@Valid @RequestBody ChatRequest request) {
        StreamingResponseBody body = output -> {
            try {
                ollamaClient.streamChat(request.message(), chunk -> {
                    try {
                        if (chunk.done()) {
                            writeEvent(output, "done", new StreamDone(chunk.model(), true));
                        } else if (chunk.content() != null && !chunk.content().isEmpty()) {
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

    public record ChatRequest(@NotBlank(message = "message não pode estar vazio") String message) {
    }

    public record ChatResponse(String model, String answer) {
    }

    public record StreamToken(String model, String content) {
    }

    public record StreamDone(String model, boolean done) {
    }

    public record StreamError(String message) {
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


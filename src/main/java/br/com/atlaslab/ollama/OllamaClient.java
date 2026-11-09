package br.com.atlaslab.ollama;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import java.util.Map;

@Service
public class OllamaClient {

    private final RestClient restClient;
    private final OllamaProperties properties;
    private final ObjectMapper objectMapper;

    public OllamaClient(RestClient ollamaRestClient, OllamaProperties properties, ObjectMapper objectMapper) {
        this.restClient = ollamaRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void streamChat(String prompt, Consumer<StreamChunk> onChunk) {
        streamChat(List.of(new ChatMessage("user", prompt)), onChunk);
    }

    public void streamChat(List<ChatMessage> messages, Consumer<StreamChunk> onChunk) {
        streamChat(properties.model(), messages, onChunk);
    }

    public void streamChat(String model, List<ChatMessage> messages, Consumer<StreamChunk> onChunk) {
        try {
            restClient.post()
                    .uri("/api/chat")
                    .body(new OllamaRequest(
                            model,
                            messages,
                            true,
                            false))
                    .exchange((request, response) -> {
                        HttpStatusCode status = response.getStatusCode();
                        if (status.isError()) {
                            throw new OllamaException("O Ollama respondeu com HTTP " + status.value() + ".");
                        }

                        try (var reader = new BufferedReader(new InputStreamReader(
                                response.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.isBlank()) {
                                    continue;
                                }
                                OllamaResponse chunk = objectMapper.readValue(line, OllamaResponse.class);
                                String content = chunk.message() == null ? null : chunk.message().content();
                                onChunk.accept(new StreamChunk(chunk.model(), content, chunk.done(), chunk.stats()));
                            }
                        } catch (IOException exception) {
                            throw new OllamaException("Falha ao ler o streaming retornado pelo Ollama.", exception);
                        }
                        return null;
                    });
        } catch (ResourceAccessException exception) {
            throw new OllamaException(
                    "Não foi possível acessar o Ollama. Verifique o túnel SSH e se o serviço está ativo.",
                    exception);
        }
    }

    public ChatResult chat(String prompt) {
        return chat(List.of(new ChatMessage("user", prompt)));
    }

    public ChatResult chat(List<ChatMessage> messages) {
        return chat(properties.model(), messages);
    }

    public ChatResult chat(String model, List<ChatMessage> messages) {
        try {
            OllamaResponse response = restClient.post()
                    .uri("/api/chat")
                    .body(new OllamaRequest(
                            model,
                            messages,
                            false,
                            false))
                    .retrieve()
                    .body(OllamaResponse.class);

            if (response == null || response.message() == null || response.message().content() == null) {
                throw new OllamaException("O Ollama retornou uma resposta vazia.");
            }

            return new ChatResult(response.model(), response.message().content(), response.stats());
        } catch (RestClientResponseException exception) {
            throw new OllamaException(
                    "O Ollama respondeu com HTTP " + exception.getStatusCode().value() + ".",
                    exception);
        } catch (ResourceAccessException exception) {
            throw new OllamaException(
                    "Não foi possível acessar o Ollama. Verifique o túnel SSH e se o serviço está ativo.",
                    exception);
        }
    }

    public void unload(String model) {
        try {
            restClient.post()
                    .uri("/api/generate")
                    .body(Map.of("model", model, "prompt", "", "stream", false, "keep_alive", 0))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw new OllamaException(
                    "O Ollama não conseguiu descarregar o modelo (HTTP "
                            + exception.getStatusCode().value() + ").", exception);
        } catch (ResourceAccessException exception) {
            throw new OllamaException(
                    "Não foi possível acessar o Ollama para descarregar o modelo.", exception);
        }
    }

    record OllamaRequest(String model, List<ChatMessage> messages, boolean stream, boolean think) {
    }

    record OllamaResponse(
            String model,
            ChatMessage message,
            boolean done,
            @JsonProperty("total_duration") Long totalDurationNanos,
            @JsonProperty("load_duration") Long loadDurationNanos,
            @JsonProperty("prompt_eval_count") Integer promptTokens,
            @JsonProperty("eval_count") Integer completionTokens,
            @JsonProperty("eval_duration") Long evalDurationNanos) {

        OllamaStats stats() {
            if (totalDurationNanos == null && loadDurationNanos == null && promptTokens == null
                    && completionTokens == null && evalDurationNanos == null) {
                return null;
            }
            return new OllamaStats(totalDurationNanos, loadDurationNanos,
                    promptTokens, completionTokens, evalDurationNanos);
        }
    }

    public record ChatMessage(String role, String content) {
    }

    public record ChatResult(String model, String content, OllamaStats stats) {
    }

    public record OllamaStats(Long totalDurationNanos, Long loadDurationNanos, Integer promptTokens,
                              Integer completionTokens, Long evalDurationNanos) {
    }

    public record StreamChunk(String model, String content, boolean done, OllamaStats stats) {
        public StreamChunk(String model, String content, boolean done) {
            this(model, content, done, null);
        }
    }
}


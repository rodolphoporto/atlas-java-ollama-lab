package br.com.atlaslab.ollama;

import br.com.atlaslab.api.ChatController.ChatResponse;
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
        try {
            restClient.post()
                    .uri("/api/chat")
                    .body(new OllamaRequest(
                            properties.model(),
                            List.of(new Message("user", prompt)),
                            true))
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
                                onChunk.accept(new StreamChunk(chunk.model(), content, chunk.done()));
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

    public ChatResponse chat(String prompt) {
        try {
            OllamaResponse response = restClient.post()
                    .uri("/api/chat")
                    .body(new OllamaRequest(
                            properties.model(),
                            List.of(new Message("user", prompt)),
                            false))
                    .retrieve()
                    .body(OllamaResponse.class);

            if (response == null || response.message() == null || response.message().content() == null) {
                throw new OllamaException("O Ollama retornou uma resposta vazia.");
            }

            return new ChatResponse(response.model(), response.message().content());
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

    record OllamaRequest(String model, List<Message> messages, boolean stream) {
    }

    record OllamaResponse(String model, Message message, boolean done) {
    }

    record Message(String role, String content) {
    }

    public record StreamChunk(String model, String content, boolean done) {
    }
}


package br.com.atlaslab.ollama;

import br.com.atlaslab.api.ChatController.ChatResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Service
public class OllamaClient {

    private final RestClient restClient;
    private final OllamaProperties properties;

    public OllamaClient(RestClient ollamaRestClient, OllamaProperties properties) {
        this.restClient = ollamaRestClient;
        this.properties = properties;
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

    record OllamaResponse(String model, Message message) {
    }

    record Message(String role, String content) {
    }
}


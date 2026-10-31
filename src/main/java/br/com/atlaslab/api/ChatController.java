package br.com.atlaslab.api;

import br.com.atlaslab.ollama.OllamaClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class ChatController {

    private final OllamaClient ollamaClient;

    public ChatController(OllamaClient ollamaClient) {
        this.ollamaClient = ollamaClient;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return ollamaClient.chat(request.message());
    }

    public record ChatRequest(@NotBlank(message = "message não pode estar vazio") String message) {
    }

    public record ChatResponse(String model, String answer) {
    }
}


package br.com.atlaslab.api;

import br.com.atlaslab.ollama.OllamaClient;
import br.com.atlaslab.ollama.OllamaException;
import br.com.atlaslab.ollama.OllamaProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatControllerTest {

    private MockMvc mockMvc;
    private FakeOllamaClient ollamaClient;

    @BeforeEach
    void setUp() {
        ollamaClient = new FakeOllamaClient();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ChatController(ollamaClient, new ObjectMapper()))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void returnsOllamaAnswer() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .contentType(APPLICATION_JSON)
                        .content("{\"message\":\"Olá\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("qwen3:4b-instruct"))
                .andExpect(jsonPath("$.answer").value("Olá!"));
    }

    @Test
    void rejectsBlankMessage() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .contentType(APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requisição inválida"));
    }

    @Test
    void mapsOllamaFailureToBadGateway() throws Exception {
        ollamaClient.fail = true;

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(APPLICATION_JSON)
                        .content("{\"message\":\"Olá\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("Falha ao consultar o Ollama"));
    }

    @Test
    void streamsTokensAndCompletionAsServerSentEvents() throws Exception {
        var pending = mockMvc.perform(post("/api/ai/chat/stream")
                        .contentType(APPLICATION_JSON)
                        .content("{\"message\":\"Olá\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "event: token\ndata: {\"model\":\"qwen3:4b-instruct\",\"content\":\"Olá\"}\n\n")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "event: done\ndata: {\"model\":\"qwen3:4b-instruct\",\"done\":true}\n\n")));
    }

    private static final class FakeOllamaClient extends OllamaClient {
        private boolean fail;

        private FakeOllamaClient() {
            super(
                    RestClient.create(),
                    new OllamaProperties(
                            URI.create("http://127.0.0.1:11434"),
                            "qwen3:4b-instruct",
                            Duration.ofSeconds(2),
                            Duration.ofSeconds(120)),
                    new ObjectMapper());
        }

        @Override
        public ChatController.ChatResponse chat(String prompt) {
            if (fail) {
                throw new OllamaException("Ollama indisponível");
            }
            return new ChatController.ChatResponse("qwen3:4b-instruct", "Olá!");
        }

        @Override
        public void streamChat(String prompt, Consumer<StreamChunk> onChunk) {
            List.of(
                    new StreamChunk("qwen3:4b-instruct", "Olá", false),
                    new StreamChunk("qwen3:4b-instruct", "!", false),
                    new StreamChunk("qwen3:4b-instruct", "", true))
                    .forEach(onChunk);
        }
    }
}

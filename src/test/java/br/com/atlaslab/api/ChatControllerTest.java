package br.com.atlaslab.api;

import br.com.atlaslab.ollama.OllamaClient;
import br.com.atlaslab.ollama.OllamaException;
import br.com.atlaslab.ollama.OllamaProperties;
import br.com.atlaslab.ollama.ModelRegistry;
import br.com.atlaslab.history.ConversationStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
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
    private FakeConversationStore conversationStore;

    @BeforeEach
    void setUp() {
        ollamaClient = new FakeOllamaClient();
        conversationStore = new FakeConversationStore();
        var properties = new OllamaProperties(
                URI.create("http://127.0.0.1:11434"),
                "qwen3:4b-instruct",
                List.of("qwen3:4b-instruct", "qwen3.5:9b"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(120));
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ChatController(ollamaClient, new ObjectMapper(), conversationStore,
                        new ModelRegistry(properties)))
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
                .andExpect(jsonPath("$.answer").value("Olá!"))
                .andExpect(jsonPath("$.conversationId").value("11111111-1111-1111-1111-111111111111"))
                .andExpect(jsonPath("$.metrics.tokensPerSecond").value(100.0))
                .andExpect(jsonPath("$.metrics.promptTokens").value(3))
                .andExpect(jsonPath("$.metrics.completionTokens").value(2))
                .andExpect(jsonPath("$.metrics.contextMessages").value(1))
                .andExpect(jsonPath("$.metrics.contextCharacters").value(3));

        org.assertj.core.api.Assertions.assertThat(conversationStore.assistantAnswer).isEqualTo("Olá!");
        org.assertj.core.api.Assertions.assertThat(ollamaClient.receivedMessages)
                .containsExactly(new OllamaClient.ChatMessage("user", "Olá"));
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
    void listsOnlyConfiguredModels() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/ai/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultModel").value("qwen3:4b-instruct"))
                .andExpect(jsonPath("$.models[0]").value("qwen3:4b-instruct"))
                .andExpect(jsonPath("$.models[1]").value("qwen3.5:9b"))
                .andExpect(jsonPath("$.models.length()").value(2));
    }

    @Test
    void usesRequestedAllowedModel() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .contentType(APPLICATION_JSON)
                        .content("{\"message\":\"Olá\",\"model\":\"qwen3.5:9b\"}"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(ollamaClient.receivedModel).isEqualTo("qwen3.5:9b");
    }

    @Test
    void rejectsModelOutsideAllowListBeforeSavingMessage() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .contentType(APPLICATION_JSON)
                        .content("{\"message\":\"Olá\",\"model\":\"modelo:nao-permitido\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Modelo não permitido"));

        org.assertj.core.api.Assertions.assertThat(conversationStore.userMessages).isZero();
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
                        "event: conversation\ndata: {\"conversationId\":\"11111111-1111-1111-1111-111111111111\"}\n\n")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "event: token\ndata: {\"model\":\"qwen3:4b-instruct\",\"content\":\"Olá\"}\n\n")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: done")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"tokensPerSecond\":100.0")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"promptTokens\":3")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"completionTokens\":2")));

        org.assertj.core.api.Assertions.assertThat(ollamaClient.receivedModel)
                .isEqualTo("qwen3:4b-instruct");
    }

    private static final class FakeOllamaClient extends OllamaClient {
        private boolean fail;

        private FakeOllamaClient() {
            super(
                    RestClient.create(),
                    new OllamaProperties(
                            URI.create("http://127.0.0.1:11434"),
                            "qwen3:4b-instruct",
                            List.of("qwen3:4b-instruct", "qwen3.5:9b"),
                            Duration.ofSeconds(2),
                            Duration.ofSeconds(120)),
                    new ObjectMapper());
        }

        @Override
        public ChatResult chat(String model, List<ChatMessage> messages) {
            if (fail) {
                throw new OllamaException("Ollama indisponível");
            }
            receivedMessages = messages;
            receivedModel = model;
            return new ChatResult(model, "Olá!",
                    new OllamaStats(30_000_000L, null, 3, 2, 20_000_000L));
        }

        @Override
        public void streamChat(String model, List<ChatMessage> messages, Consumer<StreamChunk> onChunk) {
            receivedMessages = messages;
            receivedModel = model;
            List.of(
                    new StreamChunk("qwen3:4b-instruct", "Olá", false),
                    new StreamChunk("qwen3:4b-instruct", "!", false),
                    new StreamChunk("qwen3:4b-instruct", "", true,
                            new OllamaStats(30_000_000L, null, 3, 2, 20_000_000L)))
                    .forEach(onChunk);
        }

        private List<ChatMessage> receivedMessages;
        private String receivedModel;
    }

    private static final class FakeConversationStore implements ConversationStore {
        private final UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        private String assistantAnswer;
        private int userMessages;

        @Override
        public UUID appendUserMessage(UUID conversationId, String content) {
            userMessages++;
            return conversationId == null ? id : conversationId;
        }

        @Override
        public void appendAssistantMessage(UUID conversationId, String content) {
            assistantAnswer = content;
        }

        @Override
        public List<ContextMessage> loadContext(UUID conversationId) {
            return List.of(new ContextMessage("user", "Olá"));
        }
    }
}

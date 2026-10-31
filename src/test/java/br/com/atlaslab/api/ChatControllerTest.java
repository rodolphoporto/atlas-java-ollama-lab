package br.com.atlaslab.api;

import br.com.atlaslab.ollama.OllamaClient;
import br.com.atlaslab.ollama.OllamaException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OllamaClient ollamaClient;

    @Test
    void returnsOllamaAnswer() throws Exception {
        when(ollamaClient.chat("Olá")).thenReturn(new ChatController.ChatResponse("qwen3:4b-instruct", "Olá!"));

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
        when(ollamaClient.chat(anyString())).thenThrow(new OllamaException("Ollama indisponível"));

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(APPLICATION_JSON)
                        .content("{\"message\":\"Olá\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("Falha ao consultar o Ollama"));
    }
}


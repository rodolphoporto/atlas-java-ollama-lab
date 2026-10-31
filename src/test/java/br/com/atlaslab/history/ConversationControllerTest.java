package br.com.atlaslab.history;

import br.com.atlaslab.api.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConversationControllerTest {
    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private MockMvc mockMvc;
    private FakeConversationService service;

    @BeforeEach
    void setUp() {
        service = new FakeConversationService();
        mockMvc = MockMvcBuilders.standaloneSetup(new ConversationController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void renamesConversation() throws Exception {
        mockMvc.perform(patch("/api/conversations/{id}", ID)
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"Estudos de Java 21\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.title").value("Estudos de Java 21"));
    }

    @Test
    void rejectsBlankTitle() throws Exception {
        mockMvc.perform(patch("/api/conversations/{id}", ID)
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("title não pode estar vazio"));
    }

    @Test
    void deletesConversation() throws Exception {
        mockMvc.perform(delete("/api/conversations/{id}", ID))
                .andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(service.deletedId).isEqualTo(ID);
    }

    private static final class FakeConversationService extends ConversationService {
        private UUID deletedId;

        private FakeConversationService() {
            super(null, 20, 24_000);
        }

        @Override
        public ConversationSummary rename(UUID id, String title) {
            return new ConversationSummary(id, title, Instant.parse("2026-10-31T12:00:00Z"));
        }

        @Override
        public void delete(UUID id) {
            deletedId = id;
        }
    }
}

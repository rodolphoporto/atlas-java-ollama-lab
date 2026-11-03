package br.com.atlaslab.ollama;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OllamaClientTest {

    private MockRestServiceServer server;
    private OllamaClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://127.0.0.1:11434");
        server = MockRestServiceServer.bindTo(builder).build();
        var properties = new OllamaProperties(
                URI.create("http://127.0.0.1:11434"),
                "qwen3:4b-instruct",
                List.of("qwen3:4b-instruct", "qwen3.5:9b"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(120));
        client = new OllamaClient(builder.build(), properties, new ObjectMapper());
    }

    @Test
    void sendsNonStreamingChatAndMapsResponse() {
        server.expect(requestTo("http://127.0.0.1:11434/api/chat"))
                .andExpect(content().json("""
                        {
                          "model": "qwen3:4b-instruct",
                          "messages": [{"role": "user", "content": "Explique Java em uma frase"}],
                          "stream": false
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "model": "qwen3:4b-instruct",
                          "message": {"role": "assistant", "content": "Java é uma linguagem multiplataforma."},
                          "done": true,
                          "total_duration": 1250000000,
                          "prompt_eval_count": 18,
                          "eval_count": 12,
                          "eval_duration": 120000000
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = client.chat("Explique Java em uma frase");

        assertThat(response.model()).isEqualTo("qwen3:4b-instruct");
        assertThat(response.content()).isEqualTo("Java é uma linguagem multiplataforma.");
        assertThat(response.stats()).isEqualTo(
                new OllamaClient.OllamaStats(1_250_000_000L, 18, 12, 120_000_000L));
        server.verify();
    }

    @Test
    void streamsEveryOllamaChunkUntilDone() {
        server.expect(requestTo("http://127.0.0.1:11434/api/chat"))
                .andExpect(content().json("""
                        {
                          "model": "qwen3:4b-instruct",
                          "messages": [{"role": "user", "content": "Olá"}],
                          "stream": true
                        }
                        """))
                .andRespond(withSuccess("""
                        {"model":"qwen3:4b-instruct","message":{"role":"assistant","content":"Olá"},"done":false}
                        {"model":"qwen3:4b-instruct","message":{"role":"assistant","content":"!"},"done":false}
                        {"model":"qwen3:4b-instruct","message":{"role":"assistant","content":""},"done":true,"total_duration":1250000000,"prompt_eval_count":18,"eval_count":12,"eval_duration":120000000}
                        """, MediaType.APPLICATION_NDJSON));

        var chunks = new ArrayList<OllamaClient.StreamChunk>();

        client.streamChat("Olá", chunks::add);

        assertThat(chunks).containsExactly(
                new OllamaClient.StreamChunk("qwen3:4b-instruct", "Olá", false),
                new OllamaClient.StreamChunk("qwen3:4b-instruct", "!", false),
                new OllamaClient.StreamChunk("qwen3:4b-instruct", "", true,
                        new OllamaClient.OllamaStats(1_250_000_000L, 18, 12, 120_000_000L)));
        server.verify();
    }

    @Test
    void sendsConversationHistoryInOrder() {
        server.expect(requestTo("http://127.0.0.1:11434/api/chat"))
                .andExpect(content().json("""
                        {
                          "model": "qwen3:4b-instruct",
                          "messages": [
                            {"role": "user", "content": "Meu nome é Rodolpho"},
                            {"role": "assistant", "content": "Prazer, Rodolpho!"},
                            {"role": "user", "content": "Qual é meu nome?"}
                          ],
                          "stream": false
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "model": "qwen3:4b-instruct",
                          "message": {"role": "assistant", "content": "Seu nome é Rodolpho."},
                          "done": true
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = client.chat(List.of(
                new OllamaClient.ChatMessage("user", "Meu nome é Rodolpho"),
                new OllamaClient.ChatMessage("assistant", "Prazer, Rodolpho!"),
                new OllamaClient.ChatMessage("user", "Qual é meu nome?")));

        assertThat(response.content()).isEqualTo("Seu nome é Rodolpho.");
        server.verify();
    }

    @Test
    void sendsExplicitSelectedModel() {
        server.expect(requestTo("http://127.0.0.1:11434/api/chat"))
                .andExpect(content().json("""
                        {
                          "model": "qwen3.5:9b",
                          "messages": [{"role": "user", "content": "Olá"}],
                          "stream": false
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "model": "qwen3.5:9b",
                          "message": {"role": "assistant", "content": "Olá!"},
                          "done": true
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = client.chat("qwen3.5:9b", List.of(new OllamaClient.ChatMessage("user", "Olá")));

        assertThat(response.model()).isEqualTo("qwen3.5:9b");
        server.verify();
    }
}


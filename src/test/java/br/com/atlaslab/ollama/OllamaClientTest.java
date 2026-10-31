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
                          "done": true
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = client.chat("Explique Java em uma frase");

        assertThat(response.model()).isEqualTo("qwen3:4b-instruct");
        assertThat(response.answer()).isEqualTo("Java é uma linguagem multiplataforma.");
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
                        {"model":"qwen3:4b-instruct","message":{"role":"assistant","content":""},"done":true}
                        """, MediaType.APPLICATION_NDJSON));

        var chunks = new ArrayList<OllamaClient.StreamChunk>();

        client.streamChat("Olá", chunks::add);

        assertThat(chunks).containsExactly(
                new OllamaClient.StreamChunk("qwen3:4b-instruct", "Olá", false),
                new OllamaClient.StreamChunk("qwen3:4b-instruct", "!", false),
                new OllamaClient.StreamChunk("qwen3:4b-instruct", "", true));
        server.verify();
    }
}


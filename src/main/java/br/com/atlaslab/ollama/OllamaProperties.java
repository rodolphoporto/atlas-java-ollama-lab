package br.com.atlaslab.ollama;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@ConfigurationProperties("app.ollama")
public record OllamaProperties(URI baseUrl, String model, List<String> allowedModels,
                               Duration connectTimeout, Duration readTimeout) {
}


package br.com.atlaslab.ollama;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("app.ollama")
public record OllamaProperties(URI baseUrl, String model, Duration connectTimeout, Duration readTimeout) {
}


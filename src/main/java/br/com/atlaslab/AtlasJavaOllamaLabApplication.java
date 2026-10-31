package br.com.atlaslab;

import br.com.atlaslab.ollama.OllamaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(OllamaProperties.class)
public class AtlasJavaOllamaLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(AtlasJavaOllamaLabApplication.class, args);
    }
}


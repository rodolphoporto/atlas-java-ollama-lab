package br.com.atlaslab.ollama;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;

@Component
public class ModelRegistry {

    private final String defaultModel;
    private final List<String> allowedModels;

    public ModelRegistry(OllamaProperties properties) {
        this.defaultModel = properties.model();
        LinkedHashSet<String> models = new LinkedHashSet<>();
        models.add(defaultModel);
        if (properties.allowedModels() != null) {
            properties.allowedModels().stream()
                    .filter(model -> model != null && !model.isBlank())
                    .map(String::trim)
                    .forEach(models::add);
        }
        this.allowedModels = List.copyOf(models);
    }

    public String resolve(String requestedModel) {
        if (requestedModel == null || requestedModel.isBlank()) {
            return defaultModel;
        }
        String model = requestedModel.trim();
        if (!allowedModels.contains(model)) {
            throw new ModelNotAllowedException(model);
        }
        return model;
    }

    public String defaultModel() {
        return defaultModel;
    }

    public List<String> allowedModels() {
        return allowedModels;
    }
}

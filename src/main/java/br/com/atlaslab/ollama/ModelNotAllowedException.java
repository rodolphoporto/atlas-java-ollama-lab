package br.com.atlaslab.ollama;

public class ModelNotAllowedException extends RuntimeException {

    public ModelNotAllowedException(String model) {
        super("O modelo '" + model + "' não está na lista permitida.");
    }
}

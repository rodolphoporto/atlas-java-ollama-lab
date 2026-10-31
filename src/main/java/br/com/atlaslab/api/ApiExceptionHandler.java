package br.com.atlaslab.api;

import br.com.atlaslab.ollama.OllamaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidRequest(MethodArgumentNotValidException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                exception.getBindingResult().getFieldErrors().stream()
                        .findFirst()
                        .map(error -> error.getDefaultMessage())
                        .orElse("Requisição inválida"));
        problem.setTitle("Requisição inválida");
        return problem;
    }

    @ExceptionHandler(OllamaException.class)
    ProblemDetail ollamaUnavailable(OllamaException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY,
                exception.getMessage());
        problem.setTitle("Falha ao consultar o Ollama");
        return problem;
    }
}


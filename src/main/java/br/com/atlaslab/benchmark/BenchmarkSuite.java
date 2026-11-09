package br.com.atlaslab.benchmark;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Component
public class BenchmarkSuite {
    private final List<Prompt> prompts = List.of(
            new Prompt("pt-explanation", "Explicação em português", "Explique polimorfismo em Java para uma pessoa iniciante, em no máximo 120 palavras."),
            new Prompt("java-code", "Código Java", "Crie um record Java 21 chamado Produto com validação de nome não vazio e preço positivo. Inclua um exemplo de uso."),
            new Prompt("bug-fix", "Correção de bug", "Encontre e corrija o erro neste código Java, explicando a causa: List<Integer> n = List.of(1,2,3); n.add(4);"),
            new Prompt("reasoning", "Raciocínio", "Há três caixas rotuladas Maçãs, Laranjas e Mistas, mas todos os rótulos estão errados. Explique como corrigir todos retirando apenas uma fruta de uma caixa."),
            new Prompt("format", "Aderência ao formato", "Responda somente com JSON válido, sem Markdown, contendo as chaves linguagem, versao e lts para Java 21."),
            new Prompt("hallucination", "Resistência à alucinação", "Qual foi o número exato de usuários do projeto ATLAS-JAVA-001 em 15 de março de 2025? Se não houver dados suficientes, diga claramente que não é possível determinar."));

    public List<Prompt> prompts() { return prompts; }

    public Prompt find(String id) {
        return prompts.stream().filter(prompt -> prompt.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prompt de benchmark inválido"));
    }

    public record Prompt(String id, String title, String text) {}
}

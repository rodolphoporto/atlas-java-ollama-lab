# ATLAS-JAVA-001 — Java, Spring Boot e Ollama

Projeto de estudo com Java 21, Spring Boot 3.5 e Maven. O endpoint `POST /api/ai/chat` envia uma mensagem ao Ollama pela API HTTP usando o `RestClient` do Spring.

## Pré-requisitos

- JDK 21
- Maven 3.6.3 ou superior
- Acesso SSH ao computador `atlas`
- Ollama ativo no `atlas`, com o modelo disponível

No `atlas`, confirme ou instale o modelo:

```bash
ollama pull qwen3:4b-instruct
ollama list
```

## 1. Iniciar o túnel SSH no Mac

Em um terminal separado no Mac, execute:

```bash
ssh -N -L 11434:127.0.0.1:11434 rporto@atlas
```

Mantenha esse terminal aberto. O comando encaminha `127.0.0.1:11434` do Mac para a porta local do Ollama no `atlas`; ele não publica o Ollama na rede.

Valide o túnel:

```bash
curl http://127.0.0.1:11434/api/tags
```

Se o host, usuário ou porta SSH forem diferentes, ajuste apenas o comando do túnel.

## 2. Executar a aplicação

```bash
cd /Users/esx/Documents/development/atlas-java-ollama-lab
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn spring-boot:run
```

A API inicia, por padrão, em `http://localhost:8080`.

## 3. Testar o endpoint

```bash
curl -i http://localhost:8080/api/ai/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"Explique o que é uma record class em Java em duas frases."}'
```

Resposta esperada:

```json
{
  "model": "qwen3:4b-instruct",
  "answer": "..."
}
```

Mensagem ausente ou vazia retorna HTTP 400. Falha de conexão, timeout ou erro HTTP do Ollama retorna HTTP 502 sem expor detalhes internos.

## Configuração externa

Nenhum segredo é armazenado no projeto. As opções podem ser alteradas por variáveis de ambiente:

| Variável | Padrão | Finalidade |
|---|---|---|
| `OLLAMA_BASE_URL` | `http://127.0.0.1:11434` | Endereço do Ollama/túnel |
| `OLLAMA_MODEL` | `qwen3:4b-instruct` | Modelo usado no chat |
| `OLLAMA_CONNECT_TIMEOUT` | `2s` | Timeout de conexão |
| `OLLAMA_READ_TIMEOUT` | `120s` | Timeout de resposta |

Exemplo:

```bash
OLLAMA_MODEL=qwen3:4b-instruct OLLAMA_READ_TIMEOUT=180s mvn spring-boot:run
```

## Build e testes

Os testes não dependem de túnel, rede ou Ollama real:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
mvn clean verify
```

Eles verificam o contrato HTTP, a validação, o mapeamento de erros e a requisição enviada à API do Ollama.


# ATLAS-JAVA-001 — Java, Spring Boot e Ollama

Projeto de estudo com Java 21, Spring Boot 3.5 e Maven. Os endpoints enviam mensagens ao Ollama pela API HTTP usando o `RestClient` do Spring, com resposta completa ou streaming SSE.

## Pré-requisitos

- JDK 21
- Maven 3.6.3 ou superior
- Acesso SSH ao computador `atlas`
- Ollama ativo no `atlas`, com o modelo disponível
- Docker Desktop para o PostgreSQL local

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

Inicie primeiro o PostgreSQL do projeto:

```bash
docker compose up -d
docker compose ps
```

Os dados ficam no volume `atlas-postgres-data`. Os valores locais podem ser substituídos com `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` e `POSTGRES_PORT`; a aplicação aceita `DATABASE_URL`, `DATABASE_USER` e `DATABASE_PASSWORD`.

Para encerrar apenas o contêiner, preservando o histórico:

```bash
docker compose down
```

Não use `docker compose down -v` a menos que queira apagar também o volume e todo o histórico local.

```bash
cd /Users/esx/Documents/development/atlas-java-ollama-lab
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn spring-boot:run
```

A API inicia, por padrão, em `http://localhost:8080`.

Abra [http://localhost:8080](http://localhost:8080) no navegador para usar a interface de chat. Ela consome o endpoint SSE, monta a resposta progressivamente e permite interromper a geração em andamento. O histórico de conversas fica persistido no PostgreSQL e pode ser reaberto, renomeado ou excluído pelo botão **Histórico**. A exclusão exige confirmação e remove também as mensagens da conversa. Ao continuar uma conversa, uma janela recente desse histórico é enviada ao Ollama para que o modelo mantenha o contexto. As respostas suportam um subconjunto seguro de Markdown — títulos, listas, citações, ênfase, links HTTP/HTTPS e blocos de código com botão para copiar. HTML produzido pelo modelo não é interpretado.

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
  "answer": "...",
  "conversationId": "11111111-1111-1111-1111-111111111111"
}
```

Mensagem ausente ou vazia retorna HTTP 400. Falha de conexão, timeout ou erro HTTP do Ollama retorna HTTP 502 sem expor detalhes internos.

Para continuar a mesma conversa e usar sua memória contextual, envie o `conversationId` retornado anteriormente:

```bash
curl -i http://localhost:8080/api/ai/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"Qual foi o assunto da minha pergunta anterior?","conversationId":"11111111-1111-1111-1111-111111111111"}'
```

### Streaming SSE

Use `-N` no `curl` para exibir cada evento assim que ele chegar:

```bash
curl -N http://localhost:8080/api/ai/chat/stream \
  -H 'Content-Type: application/json' \
  -d '{"message":"Explique records em Java em três frases."}'
```

O fluxo emite zero ou mais eventos `token`, seguidos de `done`:

```text
event: token
data: {"model":"qwen3:4b-instruct","content":"Um record"}

event: token
data: {"model":"qwen3:4b-instruct","content":" é..."}

event: done
data: {"model":"qwen3:4b-instruct","done":true}
```

Se o Ollama falhar depois que o HTTP 200 e o fluxo SSE já começaram, a API envia `event: error` com uma mensagem segura e encerra a conexão. O contrato completo está em [`openapi.yaml`](openapi.yaml).

### Gerenciar conversas

Renomear uma conversa:

```bash
curl -i -X PATCH http://localhost:8080/api/conversations/11111111-1111-1111-1111-111111111111 \
  -H 'Content-Type: application/json' \
  -d '{"title":"Estudos de Java 21"}'
```

Excluir uma conversa e suas mensagens:

```bash
curl -i -X DELETE http://localhost:8080/api/conversations/11111111-1111-1111-1111-111111111111
```

A exclusão retorna HTTP 204. IDs inexistentes retornam HTTP 404, e títulos vazios ou maiores que 100 caracteres retornam HTTP 400.

## Configuração externa

Nenhum segredo é armazenado no projeto. As opções podem ser alteradas por variáveis de ambiente:

| Variável | Padrão | Finalidade |
|---|---|---|
| `OLLAMA_BASE_URL` | `http://127.0.0.1:11434` | Endereço do Ollama/túnel |
| `OLLAMA_MODEL` | `qwen3:4b-instruct` | Modelo usado no chat |
| `OLLAMA_CONNECT_TIMEOUT` | `2s` | Timeout de conexão |
| `OLLAMA_READ_TIMEOUT` | `120s` | Timeout de resposta |
| `AI_CONTEXT_MAX_MESSAGES` | `20` | Máximo de mensagens recentes enviadas ao modelo |
| `AI_CONTEXT_MAX_CHARACTERS` | `24000` | Orçamento total de caracteres do contexto |

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

Eles verificam o contrato HTTP, a validação, o gerenciamento de conversas, o mapeamento de erros, a janela de memória contextual, a requisição enviada à API do Ollama e a conversão do NDJSON do Ollama em eventos SSE.
Também verificam que a interface web está disponível na raiz da aplicação.


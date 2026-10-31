package br.com.atlaslab.history;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationTest {

    @Test
    void createsTitleAndKeepsMessagesInOrder() {
        var conversation = new Conversation("Explique persistência de histórico com PostgreSQL");

        conversation.add(MessageRole.USER, "Primeira pergunta");
        conversation.add(MessageRole.ASSISTANT, "Primeira resposta");

        assertThat(conversation.title()).isEqualTo("Explique persistência de histórico com PostgreSQL");
        assertThat(conversation.messages()).extracting(Message::role)
                .containsExactly(MessageRole.USER, MessageRole.ASSISTANT);
        assertThat(conversation.messages()).extracting(Message::content)
                .containsExactly("Primeira pergunta", "Primeira resposta");
    }

    @Test
    void truncatesLongTitle() {
        var conversation = new Conversation("a".repeat(80));

        assertThat(conversation.title()).hasSize(60).endsWith("...");
    }
}

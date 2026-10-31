package br.com.atlaslab.history;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationServiceTest {

    @Test
    void returnsRecentContextInChronologicalOrder() {
        Conversation conversation = new Conversation("Meu nome é Rodolpho");
        conversation.add(MessageRole.USER, "Meu nome é Rodolpho");
        conversation.add(MessageRole.ASSISTANT, "Prazer, Rodolpho!");
        conversation.add(MessageRole.USER, "Qual é meu nome?");
        assertThat(ConversationService.selectContext(conversation.messages(), 20, 24_000)).containsExactly(
                new ConversationStore.ContextMessage("user", "Meu nome é Rodolpho"),
                new ConversationStore.ContextMessage("assistant", "Prazer, Rodolpho!"),
                new ConversationStore.ContextMessage("user", "Qual é meu nome?"));
    }

    @Test
    void doesNotStartAContextWindowWithAnOrphanAssistantMessage() {
        Conversation conversation = new Conversation("Primeira pergunta");
        conversation.add(MessageRole.USER, "Primeira pergunta");
        conversation.add(MessageRole.ASSISTANT, "Primeira resposta");
        conversation.add(MessageRole.USER, "Segunda pergunta");
        assertThat(ConversationService.selectContext(conversation.messages(), 2, 24_000)).containsExactly(
                new ConversationStore.ContextMessage("user", "Segunda pergunta"));
    }
}

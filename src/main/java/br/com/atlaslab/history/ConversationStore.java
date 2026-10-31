package br.com.atlaslab.history;

import java.util.UUID;

public interface ConversationStore {
    UUID appendUserMessage(UUID conversationId, String content);
    void appendAssistantMessage(UUID conversationId, String content);
}

package br.com.atlaslab.history;

import java.util.List;
import java.util.UUID;

public interface ConversationStore {
    UUID appendUserMessage(UUID conversationId, String content);
    void appendAssistantMessage(UUID conversationId, String content);
    List<ContextMessage> loadContext(UUID conversationId);

    record ContextMessage(String role, String content) {
    }
}

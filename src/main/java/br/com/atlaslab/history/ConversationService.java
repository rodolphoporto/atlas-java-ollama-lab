package br.com.atlaslab.history;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ConversationService implements ConversationStore {
    private final ConversationRepository repository;
    private final int maxMessages;
    private final int maxCharacters;

    ConversationService(ConversationRepository repository,
                        @Value("${app.ai.context.max-messages:20}") int maxMessages,
                        @Value("${app.ai.context.max-characters:24000}") int maxCharacters) {
        this.repository = repository;
        this.maxMessages = maxMessages;
        this.maxCharacters = maxCharacters;
    }

    @Override
    @Transactional
    public UUID appendUserMessage(UUID conversationId, String content) {
        Conversation conversation = conversationId == null
                ? new Conversation(content)
                : find(conversationId);
        conversation.add(MessageRole.USER, content);
        return repository.save(conversation).id();
    }

    @Override
    @Transactional
    public void appendAssistantMessage(UUID conversationId, String content) {
        Conversation conversation = find(conversationId);
        conversation.add(MessageRole.ASSISTANT, content);
        repository.save(conversation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContextMessage> loadContext(UUID conversationId) {
        List<Message> messages = repository.findDetailedById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversa não encontrada"))
                .messages();

        return selectContext(messages, maxMessages, maxCharacters);
    }

    static List<ContextMessage> selectContext(List<Message> messages, int maxMessages, int maxCharacters) {
        java.util.ArrayList<ContextMessage> selected = new java.util.ArrayList<>();
        int characters = 0;
        for (int index = messages.size() - 1; index >= 0 && selected.size() < maxMessages; index--) {
            Message message = messages.get(index);
            if (characters + message.content().length() > maxCharacters) {
                break;
            }
            selected.add(new ContextMessage(message.role().name().toLowerCase(), message.content()));
            characters += message.content().length();
        }
        java.util.Collections.reverse(selected);
        if (!selected.isEmpty() && "assistant".equals(selected.getFirst().role())) {
            selected.removeFirst();
        }
        return List.copyOf(selected);
    }

    @Transactional(readOnly = true)
    public List<ConversationSummary> list() {
        return repository.findAllByOrderByUpdatedAtDesc().stream()
                .map(c -> new ConversationSummary(c.id(), c.title(), c.updatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationDetail get(UUID id) {
        Conversation conversation = repository.findDetailedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversa não encontrada"));
        return new ConversationDetail(
                conversation.id(), conversation.title(), conversation.createdAt(), conversation.updatedAt(),
                conversation.messages().stream()
                        .map(m -> new MessageDetail(m.id(), m.role(), m.content(), m.createdAt()))
                        .toList());
    }

    private Conversation find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversa não encontrada"));
    }

    public record ConversationSummary(UUID id, String title, Instant updatedAt) {}
    public record ConversationDetail(UUID id, String title, Instant createdAt, Instant updatedAt,
                                     List<MessageDetail> messages) {}
    public record MessageDetail(UUID id, MessageRole role, String content, Instant createdAt) {}
}

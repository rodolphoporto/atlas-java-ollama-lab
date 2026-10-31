package br.com.atlaslab.history;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ConversationService implements ConversationStore {
    private final ConversationRepository repository;

    ConversationService(ConversationRepository repository) {
        this.repository = repository;
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

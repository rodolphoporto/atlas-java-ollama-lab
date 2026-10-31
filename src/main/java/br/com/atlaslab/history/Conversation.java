package br.com.atlaslab.history;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "conversations")
class Conversation {
    @Id
    private UUID id;
    private String title;
    private Instant createdAt;
    private Instant updatedAt;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<Message> messages = new ArrayList<>();

    protected Conversation() {
    }

    Conversation(String firstMessage) {
        this.id = UUID.randomUUID();
        this.title = firstMessage.length() > 60 ? firstMessage.substring(0, 57) + "..." : firstMessage;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    void add(MessageRole role, String content) {
        messages.add(new Message(this, role, content));
        updatedAt = Instant.now();
    }

    void rename(String title) {
        this.title = title.trim();
        this.updatedAt = Instant.now();
    }

    UUID id() { return id; }
    String title() { return title; }
    Instant createdAt() { return createdAt; }
    Instant updatedAt() { return updatedAt; }
    List<Message> messages() { return List.copyOf(messages); }
}

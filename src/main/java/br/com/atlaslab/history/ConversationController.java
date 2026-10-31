package br.com.atlaslab.history;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    private final ConversationService service;

    public ConversationController(ConversationService service) {
        this.service = service;
    }

    @GetMapping
    public List<ConversationService.ConversationSummary> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public ConversationService.ConversationDetail get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PatchMapping("/{id}")
    public ConversationService.ConversationSummary rename(@PathVariable UUID id,
                                                           @Valid @RequestBody RenameConversationRequest request) {
        return service.rename(id, request.title());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record RenameConversationRequest(
            @NotBlank(message = "title não pode estar vazio")
            @Size(max = 100, message = "title deve ter no máximo 100 caracteres") String title) {
    }
}

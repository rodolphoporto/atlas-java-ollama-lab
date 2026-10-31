package br.com.atlaslab.history;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
}

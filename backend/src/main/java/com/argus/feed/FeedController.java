package com.argus.feed;

import com.argus.event.ActionEvent;
import com.argus.event.ActionEventRepository;
import com.argus.event.EventDtos;
import com.argus.security.Permissions;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Searchable live traffic history (persisted in PostgreSQL). */
@RestController
@RequestMapping("/api/feed")
public class FeedController {

    private final ActionEventRepository repository;
    private final ObjectMapper objectMapper;

    public FeedController(ActionEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.EVENTS_READ + "')")
    public List<EventDtos.FeedEvent> list(
            @RequestParam(name = "decision", required = false) String decision,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "size", defaultValue = "60") int size) {
        String decisionValue = decision == null || decision.isBlank() || "ALL".equalsIgnoreCase(decision)
                ? null : decision.toUpperCase();
        String query = q == null || q.isBlank() ? null : q.trim();
        return repository.search(decisionValue, query, PageRequest.of(0, Math.min(Math.max(size, 1), 200)))
                .getContent()
                .stream()
                .map(e -> EventDtos.FeedEvent.from(e, objectMapper))
                .toList();
    }
}

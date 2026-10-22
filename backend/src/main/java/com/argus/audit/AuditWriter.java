package com.argus.audit;

import com.argus.stream.EventHub;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single write-path for the append-only audit ledger. All security-relevant activity
 * (auth events, agent decisions, containment, uploads, admin changes) flows through here.
 */
@Component
public class AuditWriter {

    private final AuditEntryRepository repository;
    private final EventHub eventHub;
    private final ObjectMapper objectMapper;
    private final int maxEntries;

    public AuditWriter(AuditEntryRepository repository, EventHub eventHub, ObjectMapper objectMapper,
                       @Value("${argus.feed.max-events:400}") int maxEntries) {
        this.repository = repository;
        this.eventHub = eventHub;
        this.objectMapper = objectMapper;
        this.maxEntries = Math.max(maxEntries * 2, 800);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEntry record(String kind, String action, String description, String decision,
                             Integer score, String actor, String target) {
        return record(kind, action, null, null, description, decision, score, null, null, actor, target);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEntry record(String kind, String action, String agentName, String agentRole,
                             String description, String decision, Integer score, String reason,
                             List<?> factors, String actor, String target) {
        AuditEntry entry = new AuditEntry();
        entry.setTs(Instant.now());
        entry.setKind(kind);
        entry.setAgent(agentName);
        entry.setRole(agentRole);
        entry.setDescription(abbreviate(description, 500));
        entry.setDecision(decision);
        entry.setScore(score);
        entry.setReason(abbreviate(reason, 300));
        entry.setTarget(abbreviate(target, 300));
        entry.setActor(actor != null ? actor : currentActor());
        if (factors != null && !factors.isEmpty()) {
            entry.setFactorsJson(toJson(factors));
        }
        AuditEntry saved = repository.save(entry);
        trimIfNeeded();
        eventHub.publish("audit", Map.of("entry", saved));
        return saved;
    }

    private void trimIfNeeded() {
        if (repository.count() > maxEntries) {
            repository.trim(maxEntries);
        }
    }

    private String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "system" : auth.getName();
    }

    private String toJson(Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            return json.length() > 4000 ? json.substring(0, 3997) + "..." : json;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String abbreviate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}

package com.argus.containment;

import com.argus.audit.AuditWriter;
import com.argus.stream.EventPublisher;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Records every containment action (also mirrored into the append-only audit ledger). */
@Component
public class ContainmentRecorder {

    private final ContainmentActionRepository repository;
    private final AuditWriter auditWriter;
    private final EventPublisher eventPublisher;

    public ContainmentRecorder(ContainmentActionRepository repository, AuditWriter auditWriter,
                               EventPublisher eventPublisher) {
        this.repository = repository;
        this.auditWriter = auditWriter;
        this.eventPublisher = eventPublisher;
    }

    public ContainmentAction record(ContainmentAction.Kind kind, String agentId, String agentName,
                                    String roleTitle, String detail, String trigger,
                                    String actor, Long alertId) {
        ContainmentAction action = new ContainmentAction();
        action.setTs(Instant.now());
        action.setAgentId(agentId);
        action.setAgentName(agentName);
        action.setKind(kind);
        action.setDetail(detail);
        action.setTrigger(trigger);
        action.setActor(actor);
        action.setAlertId(alertId);
        ContainmentAction saved = repository.save(action);
        auditWriter.record("containment", kind.name().toLowerCase(), agentName, roleTitle,
                "Containment [" + kind + "]", "ENFORCED", null, detail, null, actor, agentId);
        eventPublisher.publish("containment", Map.of("containment", saved));
        return saved;
    }

    public void deactivate(String agentId, ContainmentAction.Kind kind) {
        for (ContainmentAction action : repository.findByAgentIdAndActiveTrue(agentId)) {
            if (action.getKind() == kind) {
                action.setActive(false);
                repository.save(action);
            }
        }
    }
}

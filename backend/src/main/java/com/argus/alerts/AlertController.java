package com.argus.alerts;

import com.argus.agent.Agent;
import com.argus.agent.AgentRepository;
import com.argus.agent.Integrity;
import com.argus.audit.AuditWriter;
import com.argus.catalog.DataCatalog;
import com.argus.common.ApiErrorException;
import com.argus.containment.ContainmentAction;
import com.argus.containment.ContainmentRecorder;
import com.argus.event.Alert;
import com.argus.event.AlertRepository;
import com.argus.event.EventDtos;
import com.argus.security.Permissions;
import com.argus.stream.EventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Explainable alerts (= security incidents) + one-click containment playbooks. */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    public record PlaybookRequest(
            @NotBlank(message = "action is required")
            @Pattern(regexp = "BLOCK_RESOURCE|PAUSE_AGENT|RESUME_AGENT|REVOKE_TOOL|MARK_REVIEWED",
                    message = "unsupported playbook action")
            String action,
            @Size(max = 60) String value) {
    }

    private final AlertRepository alertRepository;
    private final AgentRepository agentRepository;
    private final AuditWriter auditWriter;
    private final ContainmentRecorder containmentRecorder;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public AlertController(AlertRepository alertRepository, AgentRepository agentRepository,
                           AuditWriter auditWriter, ContainmentRecorder containmentRecorder,
                           EventPublisher eventPublisher, ObjectMapper objectMapper) {
        this.alertRepository = alertRepository;
        this.agentRepository = agentRepository;
        this.auditWriter = auditWriter;
        this.containmentRecorder = containmentRecorder;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.ALERTS_READ + "')")
    public List<EventDtos.AlertView> list(
            @RequestParam(name = "status", required = false) String status) {
        List<Alert> alerts = "OPEN".equalsIgnoreCase(status)
                ? alertRepository.findTop50ByStatusOrderByTsDesc(Alert.Status.OPEN)
                : alertRepository.findTop100ByOrderByTsDesc();
        return alerts.stream().map(a -> EventDtos.AlertView.from(a, objectMapper)).toList();
    }

    @GetMapping("/incidents")
    @PreAuthorize("hasAuthority('" + Permissions.ALERTS_READ + "')")
    public List<EventDtos.AlertView> incidents() {
        return alertRepository.findTop50ByStatusOrderByTsDesc(Alert.Status.OPEN).stream()
                .map(a -> EventDtos.AlertView.from(a, objectMapper))
                .toList();
    }

    @PostMapping("/{id}/playbook")
    @PreAuthorize("hasAuthority('" + Permissions.ALERTS_ACT + "')")
    @Transactional
    public EventDtos.AlertView playbook(@PathVariable Long id,
                                        @Valid @RequestBody PlaybookRequest request) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> ApiErrorException.notFound("Alert"));
        Agent agent = agentRepository.findById(alert.getAgentId())
                .orElseThrow(() -> ApiErrorException.notFound("Agent"));
        if (agent.getStatus() == Agent.Status.ISOLATED
                && !"MARK_REVIEWED".equals(request.action()) && !"RESUME_AGENT".equals(request.action())) {
            throw ApiErrorException.badRequest("Agent is isolated - restore it before applying playbooks");
        }

        switch (request.action()) {
            case "BLOCK_RESOURCE" -> {
                String cat = request.value() == null || request.value().isBlank()
                        ? alert.getCategory() : request.value();
                if (!DataCatalog.isKnownCategory(cat)) {
                    throw ApiErrorException.badRequest("Unknown data category");
                }
                if (agent.getBlockedResources().add(cat)) {
                    appendContainment(alert, "Blocked resource: " + DataCatalog.labelOf(cat));
                    containmentRecorder.record(ContainmentAction.Kind.BLOCK_RESOURCE, agent.getId(),
                            agent.getName(), agent.getRoleTitle(),
                            "Quarantined resource: " + DataCatalog.labelOf(cat),
                            "playbook on alert #" + alert.getId(), null, alert.getId());
                }
            }
            case "PAUSE_AGENT" -> {
                if (agent.getStatus() != Agent.Status.PAUSED) {
                    agent.setStatus(Agent.Status.PAUSED);
                    appendContainment(alert, "Agent paused by analyst");
                    containmentRecorder.record(ContainmentAction.Kind.PAUSE_AGENT, agent.getId(),
                            agent.getName(), agent.getRoleTitle(), "Paused by analyst",
                            "playbook on alert #" + alert.getId(), null, alert.getId());
                }
            }
            case "RESUME_AGENT" -> {
                if (agent.getStatus() != Agent.Status.ACTIVE) {
                    agent.setStatus(Agent.Status.ACTIVE);
                    containmentRecorder.record(ContainmentAction.Kind.RESUME_AGENT, agent.getId(),
                            agent.getName(), agent.getRoleTitle(), "Resumed by analyst",
                            "playbook on alert #" + alert.getId(), null, alert.getId());
                }
            }
            case "REVOKE_TOOL" -> {
                String tool = request.value();
                if (tool == null || !agent.getTools().contains(tool)) {
                    throw ApiErrorException.badRequest("Tool is not currently granted to this agent");
                }
                agent.getTools().remove(tool);
                appendContainment(alert, "Revoked tool: " + tool);
                containmentRecorder.record(ContainmentAction.Kind.REVOKE_TOOL, agent.getId(),
                        agent.getName(), agent.getRoleTitle(), "Revoked tool " + tool,
                        "playbook on alert #" + alert.getId(), null, alert.getId());
            }
            case "MARK_REVIEWED" -> {
                alert.setStatus(Alert.Status.REVIEWED);
                auditWriter.record("alert", "reviewed", agent.getName(), agent.getRoleTitle(),
                        "Alert #" + alert.getId() + " marked reviewed", "ALLOWED", alert.getRiskScore(),
                        "analyst acknowledgement", null, null, agent.getId());
            }
            default -> throw ApiErrorException.badRequest("unsupported playbook action");
        }

        agentRepository.save(agent);
        Alert saved = alertRepository.save(alert);
        eventPublisher.publish("agent", Map.of("agent",
                EventDtos.AgentSummary.from(agent, Integrity.of(agent))));
        eventPublisher.publish("alert", Map.of("alert", EventDtos.AlertView.from(saved, objectMapper)));
        return EventDtos.AlertView.from(saved, objectMapper);
    }

    private void appendContainment(Alert alert, String note) {
        alert.setContainment(alert.getContainment() == null ? note : alert.getContainment() + " / " + note);
    }
}

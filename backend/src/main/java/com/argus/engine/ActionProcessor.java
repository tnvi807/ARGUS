package com.argus.engine;

import com.argus.agent.Agent;
import com.argus.agent.AgentRepository;
import com.argus.agent.AgentViews;
import com.argus.agent.Integrity;
import com.argus.approval.ApprovalRequest;
import com.argus.approval.ApprovalRequestRepository;
import com.argus.audit.AuditWriter;
import com.argus.catalog.DataCatalog;
import com.argus.common.ApiErrorException;
import com.argus.containment.ContainmentAction;
import com.argus.containment.ContainmentRecorder;
import com.argus.event.ActionEvent;
import com.argus.event.ActionEventRepository;
import com.argus.event.AgentComms;
import com.argus.event.AgentCommsRepository;
import com.argus.event.Alert;
import com.argus.event.AlertRepository;
import com.argus.event.EventDtos;
import com.argus.runtime.MockToolRuntime;
import com.argus.stream.EventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The enforcement pipeline: evaluate (3 engines) -&gt; decide (allow / restrict / hold / block)
 * -&gt; execute ONLY permitted actions against the protected mock runtime -&gt; audit -&gt; alert -
&gt; containment -&gt; broadcast over SSE. Blocked and held actions never reach the mock tools, so
 * their side effects are never applied.
 */
@Service
public class ActionProcessor {

    private final AgentRepository agentRepository;
    private final ActionEventRepository eventRepository;
    private final AlertRepository alertRepository;
    private final AgentCommsRepository commsRepository;
    private final ApprovalRequestRepository approvalRepository;
    private final RiskEngine riskEngine;
    private final AuditWriter auditWriter;
    private final ContainmentRecorder containmentRecorder;
    private final EventPublisher eventPublisher;
    private final MockToolRuntime runtime;
    private final ObjectMapper objectMapper;
    private final int maxFeedEvents;

    public ActionProcessor(AgentRepository agentRepository, ActionEventRepository eventRepository,
                           AlertRepository alertRepository, AgentCommsRepository commsRepository,
                           ApprovalRequestRepository approvalRepository, RiskEngine riskEngine,
                           AuditWriter auditWriter, ContainmentRecorder containmentRecorder,
                           EventPublisher eventPublisher, MockToolRuntime runtime,
                           ObjectMapper objectMapper,
                           @Value("${argus.feed.max-events:400}") int maxFeedEvents) {
        this.agentRepository = agentRepository;
        this.eventRepository = eventRepository;
        this.alertRepository = alertRepository;
        this.commsRepository = commsRepository;
        this.approvalRepository = approvalRepository;
        this.riskEngine = riskEngine;
        this.auditWriter = auditWriter;
        this.containmentRecorder = containmentRecorder;
        this.eventPublisher = eventPublisher;
        this.runtime = runtime;
        this.objectMapper = objectMapper;
        this.maxFeedEvents = maxFeedEvents;
    }

    public record Processed(ActionEvent event, RiskResult result, Alert alert, ApprovalRequest approval) {
    }

    @Transactional
    public Processed submit(ActionCommand command) {
        return submitAt(command, Instant.now());
    }

    @Transactional
    public Processed submitAt(ActionCommand command, Instant when) {
        Agent agent = agentRepository.findById(command.agentId())
                .orElseThrow(() -> ApiErrorException.notFound("Agent '" + command.agentId() + "'"));

        ActionCommand action = normalize(command);
        List<RiskEngine.CommsView> recentComms = commsRepository.findTop14ByOrderByTsDesc().stream()
                .map(c -> new RiskEngine.CommsView(c.getFromAgent(), c.getToAgent(), c.isFlagged()))
                .toList();
        Map<String, RiskEngine.AgentView> allAgents = AgentViews.index(agentRepository.findAll());
        int lastMinute = (int) eventRepository.countByAgentIdAndTsAfter(agent.getId(),
                when.minus(Duration.ofMinutes(1)));
        RiskResult result = riskEngine.evaluate(AgentViews.view(agent), action, recentComms, allAgents,
                when, RiskEngine.EngineSignals.of(lastMinute));

        int hour = when.atZone(ZoneId.systemDefault()).getHour();
        boolean offHours = hour >= 20 || hour < 6;
        agent.recordAction(action.kind(), action.category(),
                action.sensitivity() == null ? 0 : action.sensitivity(), offHours);
        agent.setLastRisk(result.score());

        ActionEvent event = new ActionEvent();
        event.setTs(when);
        event.setAgentId(agent.getId());
        event.setAgentName(agent.getName());
        event.setAgentIcon(agent.getIcon());
        event.setAgentRole(agent.getRoleTitle());
        event.setKind(action.kind());
        event.setTool(action.tool());
        event.setCategory(action.category());
        event.setTarget(action.target());
        event.setMessage(truncate(action.message(), 500));
        event.setToAgent(action.toAgent());
        event.setSensitivity(action.sensitivity() == null ? 0 : action.sensitivity());
        event.setRiskScore(result.score());
        event.setDecision(result.decision());
        event.setReason(truncate(result.reason(), 300));
        event.setFactorsJson(toJson(result.factors()));
        event.setDemo(action.demo());
        event.setExecuted(false);
        event = eventRepository.save(event);
        trimFeed();

        ApprovalRequest approval = null;
        boolean delivered = false;

        switch (result.decision()) {
            case RiskResult.ALLOWED -> {
                MockToolRuntime.ExecOutcome outcome =
                        runtime.execute(agent, action, MockToolRuntime.Mode.FULL);
                event.setExecuted(true);
                event.setExecutionDetail(truncate(outcome.detail() + " | " + outcome.output(), 2000));
                delivered = true;
            }
            case RiskResult.RESTRICTED -> {
                MockToolRuntime.ExecOutcome outcome =
                        runtime.execute(agent, action, MockToolRuntime.Mode.REDACTED);
                event.setExecuted(true);
                event.setExecutionDetail(truncate(
                        "RESTRICTED execution (output redacted): " + outcome.detail(), 2000));
                delivered = true;
            }
            case RiskResult.PENDING_APPROVAL -> {
                approval = createApproval(agent, action, result, event);
                event.setApprovalId(approval.getId());
                event.setExecutionDetail(
                        "Held for administrator approval - side effects NOT applied");
            }
            default -> event.setExecutionDetail(
                    runtime.denied(result.decision(), action.kind()).detail());
        }
        event = eventRepository.save(event);

        if ("communicate".equals(action.kind()) && action.toAgent() != null && delivered) {
            AgentComms comms = new AgentComms();
            comms.setTs(when);
            comms.setFromAgent(agent.getId());
            comms.setToAgent(action.toAgent());
            comms.setMessage(truncate(action.message() == null ? "" : action.message(), 500));
            comms.setFlagged(!RiskResult.ALLOWED.equals(result.decision()));
            commsRepository.save(comms);
        }

        auditWriter.record("action", action.kind(), agent.getName(), agent.getRoleTitle(),
                describe(agent, action), result.decision(), result.score(),
                result.reason(), result.factors(), null,
                action.target() != null ? action.target()
                        : (action.category() != null ? DataCatalog.labelOf(action.category()) : "-"));

        Alert alert = null;
        if (!RiskResult.ALLOWED.equals(result.decision())) {
            alert = createAlert(agent, action, result, event);
            if (RiskResult.BLOCKED.equals(result.decision())) {
                autoContain(agent, action, result, alert);
            }
        }

        publishAgent(agent);
        eventPublisher.publish("feed", Map.of("event", EventDtos.FeedEvent.from(event, objectMapper)));
        if (alert != null) {
            eventPublisher.publish("alert", Map.of("alert", EventDtos.AlertView.from(alert, objectMapper)));
        }
        if (approval != null) {
            eventPublisher.publish("approval", Map.of("approval", approval));
        }

        return new Processed(event, result, alert, approval);
    }

    /** Executes an administrator-approved held action (authorised restoration of intent). */
    @Transactional
    public MockToolRuntime.ExecOutcome executeApproved(Agent agent, ActionCommand action,
                                                       ActionEvent event, ApprovalRequest approval) {
        MockToolRuntime.ExecOutcome outcome = runtime.execute(agent, action, MockToolRuntime.Mode.FULL);
        event.setExecuted(true);
        event.setExecutionDetail(truncate("APPROVED by " + approval.getDecidedBy() + ": " + outcome.detail(), 2000));
        eventRepository.save(event);
        if ("communicate".equals(action.kind()) && action.toAgent() != null) {
            AgentComms comms = new AgentComms();
            comms.setTs(Instant.now());
            comms.setFromAgent(agent.getId());
            comms.setToAgent(action.toAgent());
            comms.setMessage(truncate(action.message() == null ? "" : action.message(), 500));
            comms.setFlagged(false);
            commsRepository.save(comms);
        }
        agent.setLastRisk(approval.getRiskScore());
        agentRepository.save(agent);
        publishAgent(agent);
        eventPublisher.publish("feed", Map.of("event", EventDtos.FeedEvent.from(event, objectMapper)));
        return outcome;
    }

    private ApprovalRequest createApproval(Agent agent, ActionCommand action, RiskResult result,
                                           ActionEvent event) {
        ApprovalRequest approval = new ApprovalRequest();
        approval.setTs(event.getTs());
        approval.setAgentId(agent.getId());
        approval.setAgentName(agent.getName());
        approval.setEventId(event.getId());
        approval.setKind(action.kind());
        approval.setTool(action.tool());
        approval.setCategory(action.category());
        approval.setTarget(action.target());
        approval.setMessage(truncate(action.message(), 500));
        approval.setRiskScore(result.score());
        approval.setReason(truncate(result.reason(), 300));
        approval.setFactorsJson(event.getFactorsJson());
        return approvalRepository.save(approval);
    }

    private Alert createAlert(Agent agent, ActionCommand action, RiskResult result, ActionEvent event) {
        Alert alert = new Alert();
        alert.setTs(event.getTs());
        alert.setAgentId(agent.getId());
        alert.setAgentName(agent.getName());
        alert.setAgentIcon(agent.getIcon());
        alert.setAgentRole(agent.getRoleTitle());
        alert.setKind(action.kind());
        alert.setTool(action.tool());
        alert.setCategory(action.category());
        alert.setTarget(action.target());
        alert.setMessage(truncate(action.message(), 500));
        alert.setRiskScore(result.score());
        alert.setDecision(result.decision());
        alert.setStatus(Alert.Status.OPEN);
        alert.setReason(truncate(result.reason(), 300));
        alert.setFactorsJson(event.getFactorsJson());
        alert.setDemo(action.demo());
        alert.setEventId(event.getId());
        return alertRepository.save(alert);
    }

    /** Automatic & surgical containment (MVP feature 5) - only for BLOCKED decisions. */
    private void autoContain(Agent agent, ActionCommand action, RiskResult result, Alert alert) {
        boolean changed = false;
        if (action.category() != null && !agent.getBlockedResources().contains(action.category())) {
            agent.getBlockedResources().add(action.category());
            String detail = "Quarantined resource: " + DataCatalog.labelOf(action.category());
            alert.setContainment(detail);
            containmentRecorder.record(ContainmentAction.Kind.BLOCK_RESOURCE, agent.getId(),
                    agent.getName(), agent.getRoleTitle(), detail, "auto: risk " + result.score(),
                    null, alert.getId());
            changed = true;
        }
        if (result.score() >= 90 || "config_change".equals(action.kind())) {
            if (agent.getStatus() == Agent.Status.ACTIVE) {
                agent.setStatus(Agent.Status.PAUSED);
                String pauseNote = "Agent paused pending review";
                alert.setContainment(alert.getContainment() == null
                        ? pauseNote : alert.getContainment() + " / " + pauseNote);
                containmentRecorder.record(ContainmentAction.Kind.PAUSE_AGENT, agent.getId(),
                        agent.getName(), agent.getRoleTitle(),
                        "Paused pending human review (risk " + result.score() + ")",
                        "auto: risk " + result.score(), null, alert.getId());
                changed = true;
            }
        }
        if (changed) {
            alertRepository.save(alert);
        }
    }

    private void publishAgent(Agent agent) {
        eventPublisher.publish("agent", Map.of("agent",
                EventDtos.AgentSummary.from(agent, Integrity.of(agent))));
    }

    private ActionCommand normalize(ActionCommand command) {
        double sens = command.sensitivity() != null
                ? command.sensitivity()
                : DataCatalog.sensitivityOf(command.category());
        return new ActionCommand(command.agentId(), command.kind(), command.tool(), command.category(),
                command.target(), command.message(), command.toAgent(), sens, command.demo());
    }

    private void trimFeed() {
        if (eventRepository.count() > maxFeedEvents * 1.5) {
            eventRepository.trim(maxFeedEvents);
        }
    }

    private String describe(Agent agent, ActionCommand action) {
        Map<String, String> map = Map.of("read", "Read", "api_call", "API call", "export", "Export",
                "write", "Write", "config_change", "Config change", "communicate", "Message", "delete", "Delete");
        if ("communicate".equals(action.kind()) && action.toAgent() != null) {
            String targetName = agentRepository.findById(action.toAgent())
                    .map(Agent::getName).orElse("?");
            return "→ " + targetName + ": \"" + truncate(action.message(), 60) + "\"";
        }
        StringBuilder sb = new StringBuilder(map.getOrDefault(action.kind(), action.kind()));
        if (action.category() != null) {
            sb.append(' ').append(DataCatalog.labelOf(action.category()));
        }
        if (action.target() != null && !action.target().isBlank()) {
            sb.append(" → ").append(action.target());
        }
        return sb.toString();
    }

    private String toJson(Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            return json.length() > 4000 ? json.substring(0, 3997) + "..." : json;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

    public static String killStage(String kind, String decision, String reason, int score) {
        if ("config_change".equals(kind)) {
            return "Privilege Abuse";
        }
        if ("communicate".equals(kind) && !RiskResult.ALLOWED.equals(decision)) {
            return "Lateral Movement";
        }
        if ("export".equals(kind)) {
            return "Exfiltration";
        }
        if ("delete".equals(kind)) {
            return "Collection";
        }
        if (reason != null && reason.toLowerCase(java.util.Locale.ROOT).contains("tool not in role")) {
            return "Tool Abuse";
        }
        return score >= 40 ? "Collection" : "Recon";
    }
}

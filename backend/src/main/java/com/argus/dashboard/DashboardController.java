package com.argus.dashboard;

import com.argus.agent.Agent;
import com.argus.agent.AgentRepository;
import com.argus.agent.Integrity;
import com.argus.agent.PolicyTemplates;
import com.argus.approval.ApprovalRequest;
import com.argus.approval.ApprovalRequestRepository;
import com.argus.engine.ActionProcessor;
import com.argus.event.ActionEvent;
import com.argus.event.ActionEventRepository;
import com.argus.event.Alert;
import com.argus.event.AlertRepository;
import com.argus.event.EventDtos;
import com.argus.security.Permissions;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Real-time dashboard statistics sourced exclusively from PostgreSQL. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final AgentRepository agentRepository;
    private final ActionEventRepository eventRepository;
    private final AlertRepository alertRepository;
    private final ApprovalRequestRepository approvalRepository;
    private final ObjectMapper objectMapper;

    public DashboardController(AgentRepository agentRepository, ActionEventRepository eventRepository,
                               AlertRepository alertRepository, ApprovalRequestRepository approvalRepository,
                               ObjectMapper objectMapper) {
        this.agentRepository = agentRepository;
        this.eventRepository = eventRepository;
        this.alertRepository = alertRepository;
        this.approvalRepository = approvalRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.DASHBOARD_READ + "')")
    @Transactional(readOnly = true)
    public Map<String, Object> summary() {
        Instant now = Instant.now();
        Instant dayAgo = now.minus(Duration.ofHours(24));
        Instant minuteAgo = now.minus(Duration.ofMinutes(1));
        Instant hourAgo = now.minus(Duration.ofHours(1));

        List<Agent> agents = agentRepository.findAllByOrderByIdAsc();
        long active = agents.stream().filter(a -> a.getStatus() == Agent.Status.ACTIVE).count();
        long paused = agents.stream().filter(a -> a.getStatus() == Agent.Status.PAUSED).count();
        long isolated = agents.stream().filter(a -> a.getStatus() == Agent.Status.ISOLATED).count();

        Map<String, Long> decisions = new LinkedHashMap<>();
        decisions.put("ALLOWED", eventRepository.countByDecisionAndTsAfter("ALLOWED", dayAgo));
        decisions.put("RESTRICTED", eventRepository.countByDecisionAndTsAfter("RESTRICTED", dayAgo));
        decisions.put("PENDING_APPROVAL", eventRepository.countByDecisionAndTsAfter("PENDING_APPROVAL", dayAgo));
        decisions.put("BLOCKED", eventRepository.countByDecisionAndTsAfter("BLOCKED", dayAgo));

        long blockedRecent = eventRepository.countByDecisionAndTsAfter("BLOCKED", now.minus(Duration.ofMinutes(15)));
        long openAlerts = alertRepository.countByStatus(Alert.Status.OPEN);
        long pendingApprovals = approvalRepository.countByStatus(ApprovalRequest.Status.PENDING);
        String threat = (blockedRecent >= 3 || isolated > 0) ? "HIGH"
                : (openAlerts >= 3 || decisions.get("BLOCKED") > 0 || pendingApprovals > 0) ? "ELEVATED"
                : "LOW";

        List<ActionEvent> recent = eventRepository.findTop200ByOrderByTsDesc();
        Map<String, Integer> killChain = new LinkedHashMap<>();
        for (ActionEvent e : recent.stream().filter(e -> e.getTs().isAfter(dayAgo)).toList()) {
            String stage = ActionProcessor.killStage(e.getKind(), e.getDecision(), e.getReason(), e.getRiskScore());
            killChain.merge(stage, 1, Integer::sum);
        }

        List<Map<String, Object>> riskBoard = new ArrayList<>();
        agents.stream()
                .sorted((a, b) -> Integer.compare(b.getLastRisk(), a.getLastRisk()))
                .limit(6)
                .forEach(a -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", a.getId());
                    row.put("name", a.getName());
                    row.put("icon", a.getIcon());
                    row.put("status", a.getStatus().name());
                    row.put("lastRisk", a.getLastRisk());
                    row.put("integrity", Integrity.of(a));
                    row.put("blockedResources", a.getBlockedResources().size());
                    riskBoard.add(row);
                });

        List<EventDtos.FeedEvent> timeline = recent.stream()
                .limit(12)
                .map(e -> EventDtos.FeedEvent.from(e, objectMapper))
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("generatedAt", now);
        body.put("agents", Map.of(
                "registered", agents.size(),
                "active", active,
                "paused", paused,
                "isolated", isolated));
        body.put("traffic", Map.of(
                "requestsPerMinute", eventRepository.countByTsAfter(minuteAgo),
                "requestsPerHour", eventRepository.countByTsAfter(hourAgo),
                "requests24h", eventRepository.countByTsAfter(dayAgo),
                "avgRisk24h", Math.round(eventRepository.avgRiskSince(dayAgo) * 10) / 10.0));
        body.put("decisions24h", decisions);
        body.put("alerts", Map.of(
                "open", openAlerts,
                "total", alertRepository.count(),
                "pendingApprovals", pendingApprovals));
        body.put("threatLevel", threat);
        body.put("killChain24h", killChain);
        body.put("riskBoard", riskBoard);
        body.put("timeline", timeline);
        body.put("openIncidents", openAlerts);
        return body;
    }

    @GetMapping("/meta")
    @PreAuthorize("hasAuthority('" + Permissions.DASHBOARD_READ + "')")
    public Map<String, Object> meta() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("templates", PolicyTemplates.ALL);
        meta.put("catalog", PolicyTemplates.catalog());
        meta.put("demos", com.argus.demo.DemoService.catalog());
        meta.put("decisionThresholds", Map.of(
                "0-39", "ALLOWED - executed",
                "40-59", "RESTRICTED - executed with redacted output",
                "60-69", "PENDING_APPROVAL - held for administrator approval",
                "70-100", "BLOCKED - not executed"));
        return meta;
    }
}

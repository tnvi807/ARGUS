package com.argus.event;

import com.argus.agent.Agent;
import com.argus.audit.AuditEntry;
import com.argus.engine.RiskResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;

/** Shared wire formats for the live feed, alerts and audit log (REST + SSE use the same shapes). */
public final class EventDtos {

    private EventDtos() {
    }

    public record Factor(String name, int weight, String detail) {
    }

    public record FeedEvent(
            Long id,
            Instant ts,
            String agentId,
            String agentName,
            String agentIcon,
            String agentRole,
            String kind,
            String tool,
            String category,
            String target,
            String message,
            String toAgent,
            double sensitivity,
            int riskScore,
            String decision,
            String reason,
            List<Factor> factors,
            boolean executed,
            String executionDetail,
            Long approvalId) {

        public static FeedEvent from(ActionEvent e, ObjectMapper mapper) {
            return new FeedEvent(e.getId(), e.getTs(), e.getAgentId(), e.getAgentName(), e.getAgentIcon(),
                    e.getAgentRole(), e.getKind(), e.getTool(), e.getCategory(), e.getTarget(),
                    e.getMessage(), e.getToAgent(), e.getSensitivity(), e.getRiskScore(),
                    e.getDecision(), e.getReason(), parseFactors(e.getFactorsJson(), mapper),
                    e.isExecuted(), e.getExecutionDetail(), e.getApprovalId());
        }
    }

    public record AlertView(
            Long id,
            Instant ts,
            String agentId,
            String agentName,
            String agentIcon,
            String agentRole,
            String kind,
            String tool,
            String category,
            String target,
            String message,
            int riskScore,
            String decision,
            String reason,
            List<Factor> factors,
            String status,
            String containment,
            String demo,
            Long eventId) {

        public static AlertView from(Alert a, ObjectMapper mapper) {
            return new AlertView(a.getId(), a.getTs(), a.getAgentId(), a.getAgentName(), a.getAgentIcon(),
                    a.getAgentRole(), a.getKind(), a.getTool(), a.getCategory(), a.getTarget(),
                    a.getMessage(), a.getRiskScore(), a.getDecision(), a.getReason(),
                    parseFactors(a.getFactorsJson(), mapper), a.getStatus().name(),
                    a.getContainment(), a.getDemo(), a.getEventId());
        }
    }

    public record AuditView(
            Long id,
            Instant ts,
            String actor,
            String agent,
            String role,
            String kind,
            String description,
            String target,
            String decision,
            Integer score,
            String reason,
            List<Factor> factors) {

        public static AuditView from(AuditEntry a, ObjectMapper mapper) {
            return new AuditView(a.getId(), a.getTs(), a.getActor(), a.getAgent(), a.getRole(), a.getKind(),
                    a.getDescription(), a.getTarget(), a.getDecision(), a.getScore(), a.getReason(),
                    parseFactors(a.getFactorsJson(), mapper));
        }
    }

    public record AgentSummary(
            String id,
            String name,
            String icon,
            String roleTitle,
            String description,
            List<String> tools,
            List<String> allowed,
            List<String> restricted,
            List<String> restrictActions,
            List<String> blockedResources,
            int clearance,
            String status,
            int lastRisk,
            int integrity,
            int actions,
            int fileReads,
            int apiCalls,
            int exports,
            int offHours,
            double baselineRate,
            double baselineOffHours,
            double baselineAvgSens,
            double avgSensitivity,
            List<Point> driftHistory,
            Instant updatedAt) {

        public record Point(String label, double sens) {
        }

        public static AgentSummary from(Agent a, int integrity) {
            List<Point> drift = a.getDriftHistory().stream()
                    .map(p -> new Point(p.getLabel(), p.getSens()))
                    .toList();
            double avgSens = a.getActionCount() == 0 ? a.getBaselineAvgSens() : a.getSensSum() / a.getActionCount();
            return new AgentSummary(
                    a.getId(), a.getName(), a.getIcon(), a.getRoleTitle(), a.getDescription(),
                    List.copyOf(a.getTools()), List.copyOf(a.getAllowed()), List.copyOf(a.getRestricted()),
                    List.copyOf(a.getRestrictActions()), List.copyOf(a.getBlockedResources()),
                    a.getClearance(), a.getStatus().name(), a.getLastRisk(), integrity,
                    a.getActionCount(), a.getFileReads(), a.getApiCalls(), a.getExports(),
                    a.getOffHoursActions(), a.getBaselineRate(), a.getBaselineOffHours(),
                    a.getBaselineAvgSens(), Math.round(avgSens * 100) / 100.0, drift, a.getUpdatedAt());
        }
    }

    static List<Factor> parseFactors(String json, ObjectMapper mapper) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(json, new TypeReference<List<Factor>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    public static List<Factor> factorsOf(RiskResult result, ObjectMapper mapper) {
        return result.factors().stream()
                .map(f -> new Factor(f.name(), f.weight(), f.detail()))
                .toList();
    }
}

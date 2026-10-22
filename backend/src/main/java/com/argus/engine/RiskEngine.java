package com.argus.engine;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * The unified ARGUS decision engine - a direct port of the Sentinel {@code evaluate()} pipeline:
 *
 * <ol>
 *   <li>Engine 1: role-aware action firewall (least-privilege policy checks)</li>
 *   <li>Engine 2: trust-graph / confused-deputy chain detection</li>
 *   <li>Engine 3: behavioural baseline &amp; slow-drift defence</li>
 * </ol>
 *
 * Pure and deterministic: no persistence or clock access beyond the supplied {@code now}, which
 * keeps the engine trivially unit-testable.
 */
@Component
public class RiskEngine {

    public static final int WEIGHT_TOOL_NOT_ALLOWED = 35;
    public static final int WEIGHT_SELF_MODIFY = 70;
    public static final int WEIGHT_RESTRICTED_ACTION = 45;
    public static final int WEIGHT_RESTRICTED_DATA = 45;
    public static final int WEIGHT_OUT_OF_SCOPE = 25;
    public static final int WEIGHT_CLEARANCE = 20;
    public static final int WEIGHT_CONTAINMENT = 20;
    public static final int WEIGHT_ZSCORE = 25;
    public static final int WEIGHT_NOVEL = 15;
    public static final int WEIGHT_SLOPE = 20;
    public static final int WEIGHT_OFF_HOURS = 8;
    public static final int WEIGHT_EXCESSIVE_RATE = 45;
    public static final int DEFAULT_RATE_THRESHOLD = 15;

    /** Runtime signals computed by the pipeline (call rate, thresholds). */
    public record EngineSignals(int actionsLastMinute, int rateThreshold) {
        public static EngineSignals of(int actionsLastMinute) {
            return new EngineSignals(actionsLastMinute, DEFAULT_RATE_THRESHOLD);
        }
    }

    public RiskResult evaluate(AgentView agent, ActionCommand action,
                               List<CommsView> recentComms, Map<String, AgentView> allAgents,
                               Instant now) {
        return evaluate(agent, action, recentComms, allAgents, now, EngineSignals.of(0));
    }

    public RiskResult evaluate(AgentView agent, ActionCommand action,
                               List<CommsView> recentComms, Map<String, AgentView> allAgents,
                               Instant now, EngineSignals signals) {
        if (agent == null) {
            return RiskResult.of(0, List.of(), "unknown agent");
        }

        List<RiskResult.RiskFactor> factors = new ArrayList<>();
        int score = 0;

        if (!"ACTIVE".equals(agent.status())) {
            factors.add(new RiskResult.RiskFactor("Agent contained (" + agent.status() + ")", 100,
                    "Agent was auto-paused pending human review"));
            return new RiskResult(100, RiskResult.BLOCKED, List.copyOf(factors), "agent is contained");
        }

        String tool = action.tool();
        String cat = action.category();
        String kind = action.kind() == null ? "" : action.kind();

        // ---------------- Engine 1: role-aware action firewall ----------------
        if (tool != null && !tool.isBlank() && !agent.tools().contains(tool)) {
            score += WEIGHT_TOOL_NOT_ALLOWED;
            factors.add(new RiskResult.RiskFactor("Tool not in role allowlist", WEIGHT_TOOL_NOT_ALLOWED,
                    "'" + tool + "' is not a permitted tool for " + agent.roleTitle()));
        }
        if ("config_change".equals(kind)) {
            score += WEIGHT_SELF_MODIFY;
            factors.add(new RiskResult.RiskFactor("Self-modification attempt", WEIGHT_SELF_MODIFY,
                    "Agent tried to change its own configuration/role - every role forbids this"));
        } else if (!kind.isBlank() && (agent.restrictActions().contains(kind)
                || ("export".equals(kind) && agent.restrictActions().contains("export_data"))
                || ("delete".equals(kind) && agent.restrictActions().contains("delete_records")))) {
            score += WEIGHT_RESTRICTED_ACTION;
            factors.add(new RiskResult.RiskFactor("Restricted action type", WEIGHT_RESTRICTED_ACTION,
                    kind + " is explicitly forbidden for this role"));
        }
        if (cat != null && agent.restricted().contains(cat)) {
            score += WEIGHT_RESTRICTED_DATA;
            factors.add(new RiskResult.RiskFactor("Restricted data access", WEIGHT_RESTRICTED_DATA,
                    catalogLabel(cat) + " is on the deny list for " + agent.roleTitle()));
        } else if (cat != null && !agent.allowed().contains(cat) && sensitivityOf(cat) >= 3) {
            score += WEIGHT_OUT_OF_SCOPE;
            factors.add(new RiskResult.RiskFactor("Data outside granted scope", WEIGHT_OUT_OF_SCOPE,
                    catalogLabel(cat) + " was never granted to this agent"));
        }
        if (cat != null && sensitivityOf(cat) > agent.clearance()) {
            score += WEIGHT_CLEARANCE;
            factors.add(new RiskResult.RiskFactor("Sensitivity exceeds clearance", WEIGHT_CLEARANCE,
                    "Data sensitivity " + sensitivityOf(cat) + "/5 > clearance " + agent.clearance() + "/5"));
        }
        if (cat != null && agent.blockedResources().contains(cat)) {
            score = Math.max(score, 80);
            factors.add(new RiskResult.RiskFactor("Resource under containment", WEIGHT_CONTAINMENT,
                    "Access to " + catalogLabel(cat) + " was blocked by an analyst"));
        }

        // ---------------- Engine 3: behavioural baseline & slow drift ----------------
        double sensitivity = action.sensitivity() != null
                ? action.sensitivity()
                : (cat != null ? sensitivityOf(cat) : 0);
        double z = DriftAnalyzer.zScore(sensitivity, agent.baselineAvgSens());
        if (z > 2) {
            score += WEIGHT_ZSCORE;
            factors.add(new RiskResult.RiskFactor("Unusual for this agent (z-score)", WEIGHT_ZSCORE,
                    String.format(Locale.ROOT, "Sensitivity %.0f vs baseline %.1f (z=%.1f)",
                            sensitivity, agent.baselineAvgSens(), z)));
        }
        boolean novel = cat != null
                && !Boolean.TRUE.equals(baselineKnows(agent, cat))
                && !agent.seenCategories().contains(cat);
        if (novel) {
            score += WEIGHT_NOVEL;
            factors.add(new RiskResult.RiskFactor("Never touched this data type before", WEIGHT_NOVEL,
                    "First-ever access to " + catalogLabel(cat) + " by " + agent.name()));
        }
        double slope = DriftAnalyzer.slope(agent.driftSens());
        if (slope > 0.35 && agent.driftSens().size() >= 8) {
            score += WEIGHT_SLOPE;
            factors.add(new RiskResult.RiskFactor("Slow-drift trend detected", WEIGHT_SLOPE,
                    String.format(Locale.ROOT, "Daily sensitivity climbing at +%.2f/day - classic gradual exfiltration pattern",
                            slope)));
        }
        int hour = now.atZone(ZoneId.systemDefault()).getHour();
        boolean offHours = (hour >= 20 || hour < 6) && !"communicate".equals(kind);
        if (offHours) {
            score += WEIGHT_OFF_HOURS;
            factors.add(new RiskResult.RiskFactor("Off-hours activity", WEIGHT_OFF_HOURS,
                    "Action outside the agent's normal working window"));
        }
        if (signals != null && signals.actionsLastMinute() > signals.rateThreshold()) {
            score += WEIGHT_EXCESSIVE_RATE;
            factors.add(new RiskResult.RiskFactor("Excessive API rate", WEIGHT_EXCESSIVE_RATE,
                    signals.actionsLastMinute() + " actions in the last minute exceeds the "
                            + signals.rateThreshold() + "/min budget for this role"));
        }

        // ---------------- Engine 2: trust-graph / chain detection ----------------
        if ("communicate".equals(kind) && action.message() != null) {
            String lower = action.message().toLowerCase(Locale.ROOT);
            List<String> hits = com.argus.catalog.DataCatalog.KEYWORDS.stream()
                    .filter(lower::contains)
                    .toList();
            if (!hits.isEmpty()) {
                int w = Math.min(45, 20 + hits.size() * 8);
                score += w;
                factors.add(new RiskResult.RiskFactor("Risky instruction over agent channel", w,
                        "Message contains " + hits.size() + " suspicious directive"
                                + (hits.size() > 1 ? "s" : "") + ": \"" + String.join("\", \"", hits) + "\""));
            }
        }
        RiskResult.RiskFactor chain = chainRisk(agent, recentComms, allAgents);
        if (chain != null) {
            score += chain.weight();
            factors.add(chain);
        }

        score = RiskResult.clamp(score);
        String top = factors.stream()
                .max(Comparator.comparingInt(RiskResult.RiskFactor::weight))
                .map(f -> f.name().toLowerCase(Locale.ROOT))
                .orElse(null);
        String reason = top != null ? top : "within role policy & normal behaviour";
        return RiskResult.of(score, factors, reason);
    }

    private RiskResult.RiskFactor chainRisk(AgentView agent, List<CommsView> recentComms,
                                            Map<String, AgentView> allAgents) {
        if (recentComms == null || recentComms.isEmpty()) {
            return null;
        }
        List<CommsView> recent = recentComms.size() > 14
                ? recentComms.subList(recentComms.size() - 14, recentComms.size())
                : recentComms;
        int risk = 0;
        String detail = "";
        for (CommsView c : recent) {
            if (c.flagged() && (Objects.equals(c.from(), agent.id()) || Objects.equals(c.to(), agent.id()))) {
                risk = 20;
                detail = agent.name() + " is one hop from a flagged agent-to-agent instruction";
                break;
            }
        }
        java.util.Set<String> bad = new java.util.LinkedHashSet<>();
        for (CommsView c : recent) {
            String other = Objects.equals(c.from(), agent.id()) ? c.to()
                    : (Objects.equals(c.to(), agent.id()) ? c.from() : null);
            if (other == null || Objects.equals(other, agent.id())) {
                continue;
            }
            AgentView peer = allAgents == null ? null : allAgents.get(other);
            if (peer != null && ("PAUSED".equals(peer.status()) || peer.lastRisk() >= 70)) {
                bad.add(other);
            }
        }
        if (!bad.isEmpty()) {
            risk = Math.max(risk, Math.min(25, 12 * bad.size()));
            String names = bad.stream()
                    .map(id -> allAgents.getOrDefault(id, agent).name())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            detail = (detail.isEmpty() ? "" : detail + "; ")
                    + "communicating with compromised peer" + (bad.size() > 1 ? "s" : "") + ": " + names;
        }
        if (risk <= 0) {
            return null;
        }
        return new RiskResult.RiskFactor("Suspicious chain (graph propagation)", risk, detail);
    }

    private Boolean baselineKnows(AgentView agent, String cat) {
        Double weight = agent.baselineCats().get(cat);
        if (weight == null) {
            return false;
        }
        return weight > 0.08;
    }

    private static int sensitivityOf(String cat) {
        return com.argus.catalog.DataCatalog.sensitivityOf(cat);
    }

    private static String catalogLabel(String cat) {
        return com.argus.catalog.DataCatalog.labelOf(cat);
    }

    /** Read-only projection of an agent so the engine stays persistence-free. */
    public record AgentView(
            String id,
            String name,
            String roleTitle,
            String status,
            java.util.Set<String> tools,
            java.util.Set<String> allowed,
            java.util.Set<String> restricted,
            java.util.Set<String> restrictActions,
            java.util.Set<String> blockedResources,
            int clearance,
            int lastRisk,
            double baselineAvgSens,
            Map<String, Double> baselineCats,
            java.util.Set<String> seenCategories,
            List<Double> driftSens) {
    }

    public record CommsView(String from, String to, boolean flagged) {
    }
}

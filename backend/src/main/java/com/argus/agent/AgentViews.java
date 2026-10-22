package com.argus.agent;

import com.argus.engine.RiskEngine;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Maps JPA agents to the engine's persistence-free projection. */
public final class AgentViews {

    private AgentViews() {
    }

    public static RiskEngine.AgentView view(Agent agent) {
        List<Double> drift = agent.getDriftHistory().stream()
                .map(DriftPoint::getSens)
                .toList();
        return new RiskEngine.AgentView(
                agent.getId(),
                agent.getName(),
                agent.getRoleTitle(),
                agent.getStatus().name(),
                agent.getTools(),
                agent.getAllowed(),
                agent.getRestricted(),
                agent.getRestrictActions(),
                agent.getBlockedResources(),
                agent.getClearance(),
                agent.getLastRisk(),
                agent.getBaselineAvgSens(),
                agent.getBaselineCats(),
                agent.getFingerprintCats().keySet(),
                drift);
    }

    public static Map<String, RiskEngine.AgentView> index(List<Agent> agents) {
        return agents.stream()
                .collect(Collectors.toMap(Agent::getId, AgentViews::view, (a, b) -> a));
    }
}

package com.argus.agent;

import com.argus.audit.AuditWriter;
import com.argus.common.ApiErrorException;
import com.argus.engine.DriftAnalyzer;
import com.argus.event.EventDtos;
import com.argus.stream.EventPublisher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentService {

    private final AgentRepository agentRepository;
    private final AuditWriter auditWriter;
    private final EventPublisher eventPublisher;
    private final com.argus.containment.ContainmentRecorder containmentRecorder;

    public AgentService(AgentRepository agentRepository, AuditWriter auditWriter,
                        EventPublisher eventPublisher,
                        com.argus.containment.ContainmentRecorder containmentRecorder) {
        this.agentRepository = agentRepository;
        this.auditWriter = auditWriter;
        this.eventPublisher = eventPublisher;
        this.containmentRecorder = containmentRecorder;
    }

    @Transactional(readOnly = true)
    public List<EventDtos.AgentSummary> list() {
        return agentRepository.findAllByOrderByIdAsc().stream()
                .map(a -> EventDtos.AgentSummary.from(a, integrityOf(a)))
                .toList();
    }

    @Transactional(readOnly = true)
    public EventDtos.AgentSummary get(String id) {
        Agent agent = require(id);
        return EventDtos.AgentSummary.from(agent, integrityOf(agent));
    }

    @Transactional
    public EventDtos.AgentSummary create(AgentDtos.AgentPolicyRequest req) {
        AgentDtos.validatePolicy(req);
        if (agentRepository.existsById(req.id())) {
            throw ApiErrorException.badRequest("An agent with id '" + req.id() + "' already exists");
        }
        Agent agent = new Agent();
        agent.setId(req.id());
        apply(agent, req);
        seedBaseline(agent, req);
        Agent saved = agentRepository.save(agent);
        auditWriter.record("policy", "create", saved.getName(), saved.getRoleTitle(),
                "Agent registered with least-privilege policy (" + saved.getTools().size() + " tools, clearance "
                        + saved.getClearance() + ")", "ALLOWED", 0,
                "agent created", null, null, saved.getId());
        publish(saved);
        return EventDtos.AgentSummary.from(saved, integrityOf(saved));
    }

    @Transactional
    public EventDtos.AgentSummary update(String id, AgentDtos.AgentPolicyRequest req) {
        AgentDtos.validatePolicy(req);
        Agent agent = require(id);
        Set<String> before = new LinkedHashSet<>(agent.getTools());
        apply(agent, req);
        Agent saved = agentRepository.save(agent);
        boolean toolsChanged = !before.equals(saved.getTools());
        auditWriter.record("policy", "update", saved.getName(), saved.getRoleTitle(),
                "Agent policy updated (clearance " + saved.getClearance() + ", "
                        + saved.getTools().size() + " tools)", "ALLOWED", 0,
                toolsChanged ? "tool allowlist changed" : "policy edited", null, null, saved.getId());
        publish(saved);
        return EventDtos.AgentSummary.from(saved, integrityOf(saved));
    }

    @Transactional
    public void delete(String id) {
        Agent agent = require(id);
        agentRepository.delete(agent);
        auditWriter.record("policy", "delete", agent.getName(), agent.getRoleTitle(),
                "Agent unregistered from the registry", "ALLOWED", 0,
                "agent deleted", null, null, id);
        eventPublisher.publish("agent", Map.of("deleted", id));
    }

    @Transactional
    public EventDtos.AgentSummary setStatus(String id, String status) {
        Agent agent = require(id);
        if (agent.getStatus() == Agent.Status.ISOLATED) {
            throw ApiErrorException.badRequest(
                    "Agent is isolated - an administrator must restore it before normal status changes");
        }
        Agent.Status next = "PAUSED".equals(status) ? Agent.Status.PAUSED : Agent.Status.ACTIVE;
        agent.setStatus(next);
        containmentRecorder.record(
                next == Agent.Status.PAUSED
                        ? com.argus.containment.ContainmentAction.Kind.PAUSE_AGENT
                        : com.argus.containment.ContainmentAction.Kind.RESUME_AGENT,
                agent.getId(), agent.getName(), agent.getRoleTitle(),
                "Analyst set status to " + next.name(), "manual status change", null, null);
        publish(agent);
        return EventDtos.AgentSummary.from(agent, Integrity.of(agent));
    }

    /** Agent isolation: every action from an isolated agent is refused until restored. */
    @Transactional
    public EventDtos.AgentSummary isolate(String id) {
        Agent agent = require(id);
        if (agent.getStatus() != Agent.Status.ISOLATED) {
            agent.setStatus(Agent.Status.ISOLATED);
            containmentRecorder.record(com.argus.containment.ContainmentAction.Kind.ISOLATE_AGENT,
                    agent.getId(), agent.getName(), agent.getRoleTitle(),
                    "Agent isolated - all actions refused pending investigation",
                    "analyst isolation", null, null);
            publish(agent);
        }
        return EventDtos.AgentSummary.from(agent, Integrity.of(agent));
    }

    /** Authorised restoration (administrator only): clears isolation and resource quarantine. */
    @Transactional
    public EventDtos.AgentSummary restore(String id) {
        Agent agent = require(id);
        boolean wasIsolated = agent.getStatus() == Agent.Status.ISOLATED;
        agent.setStatus(Agent.Status.ACTIVE);
        agent.getBlockedResources().clear();
        agent.setLastRisk(0);
        containmentRecorder.deactivate(agent.getId(),
                com.argus.containment.ContainmentAction.Kind.BLOCK_RESOURCE);
        containmentRecorder.record(com.argus.containment.ContainmentAction.Kind.RESTORE_AGENT,
                agent.getId(), agent.getName(), agent.getRoleTitle(),
                wasIsolated
                        ? "Agent restored by administrator - isolation and quarantine lifted"
                        : "Quarantine cleared by administrator",
                "authorised restoration", null, null);
        publish(agent);
        return EventDtos.AgentSummary.from(agent, Integrity.of(agent));
    }

    @Transactional(readOnly = true)
    public AgentDtos.DriftView drift(String id) {
        Agent agent = require(id);
        List<AgentDtos.DriftView.Point> points = agent.getDriftHistory().stream()
                .map(p -> new AgentDtos.DriftView.Point(p.getLabel(), p.getSens()))
                .toList();
        List<Double> ys = points.stream().map(AgentDtos.DriftView.Point::sens).toList();
        double slope = DriftAnalyzer.slope(ys);
        double current = ys.isEmpty() ? agent.getBaselineAvgSens() : ys.get(ys.size() - 1);
        int novel = novelCount(agent);
        int integrity = DriftAnalyzer.integrity(agent.getBaselineAvgSens(), ys, novel);
        double offHoursPct = agent.getActionCount() == 0
                ? 0
                : Math.round((agent.getOffHoursActions() * 100.0 / agent.getActionCount()) * 10) / 10.0;
        String verdict = integrity >= 85 ? "Identity Stable"
                : integrity >= 60 ? "Identity Drifting" : "Identity Compromised";
        return new AgentDtos.DriftView(agent.getId(), agent.getName(), agent.getIcon(), points,
                agent.getBaselineAvgSens(), current, slope, integrity, verdict, offHoursPct,
                novel, agent.getExports(), agent.getActionCount());
    }

    private void apply(Agent agent, AgentDtos.AgentPolicyRequest req) {
        agent.setName(req.name().trim());
        if (req.icon() != null && !req.icon().isBlank()) {
            agent.setIcon(req.icon().trim());
        } else if (agent.getIcon() == null) {
            agent.setIcon("🤖");
        }
        agent.setRoleTitle(req.roleTitle().trim());
        agent.setDescription(req.description());
        agent.setTools(new LinkedHashSet<>(req.tools()));
        agent.setAllowed(req.allowed() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(req.allowed()));
        agent.setRestricted(req.restricted() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(req.restricted()));
        agent.setRestrictActions(req.restrictActions() == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(req.restrictActions()));
        if (req.clearance() != null) {
            agent.setClearance(req.clearance());
        }
        if (req.baselineRate() != null) {
            agent.setBaselineRate(req.baselineRate());
        }
        if (req.baselineOffHours() != null) {
            agent.setBaselineOffHours(req.baselineOffHours());
        }
        if (req.baselineAvgSens() != null) {
            agent.setBaselineAvgSens(req.baselineAvgSens());
        }
        if (req.baselineCats() != null) {
            agent.setBaselineCats(new LinkedHashMap<>(req.baselineCats()));
        }
    }

    private void seedBaseline(Agent agent, AgentDtos.AgentPolicyRequest req) {
        if (agent.getBaselineCats().isEmpty()) {
            int share = Math.max(1, 100 / Math.max(1, agent.getAllowed().size()));
            for (String cat : agent.getAllowed()) {
                agent.getBaselineCats().put(cat, share / 100.0);
            }
        }
        double base = req.baselineAvgSens() != null ? req.baselineAvgSens() : agent.getBaselineAvgSens();
        for (int i = 13; i >= 0; i--) {
            double jitter = (Math.random() - 0.5) * 0.4;
            agent.addDriftPoint(i == 0 ? "Now" : "D-" + i,
                    DriftAnalyzer.clamp(base + jitter, 0.5, 5));
        }
    }

    private int novelCount(Agent agent) {
        return Integrity.novelCount(agent);
    }

    private int integrityOf(Agent agent) {
        return Integrity.of(agent);
    }

    private Agent require(String id) {
        return agentRepository.findById(id)
                .orElseThrow(() -> ApiErrorException.notFound("Agent '" + id + "'"));
    }

    private void publish(Agent agent) {
        eventPublisher.publish("agent", Map.of("agent",
                EventDtos.AgentSummary.from(agent, integrityOf(agent))));
    }

    public static List<Map<String, Object>> templatesAsMaps() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (PolicyTemplates.Template t : PolicyTemplates.ALL) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", t.key());
            m.put("name", t.name());
            m.put("roleTitle", t.roleTitle());
            m.put("description", t.description());
            m.put("icon", t.icon());
            m.put("clearance", t.clearance());
            m.put("tools", t.tools());
            m.put("allowed", t.allowed());
            m.put("restricted", t.restricted());
            m.put("restrictActions", t.restrictActions());
            out.add(m);
        }
        return out;
    }
}

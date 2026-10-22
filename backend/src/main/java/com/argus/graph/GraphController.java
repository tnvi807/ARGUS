package com.argus.graph;

import com.argus.agent.Agent;
import com.argus.agent.AgentRepository;
import com.argus.agent.Integrity;
import com.argus.catalog.DataCatalog;
import com.argus.event.AgentComms;
import com.argus.event.AgentCommsRepository;
import com.argus.event.EventDtos;
import com.argus.security.Permissions;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Agent trust graph, suspicious request chains and BFS blast-radius prediction. */
@RestController
@RequestMapping("/api/graph")
public class GraphController {

    private final AgentRepository agentRepository;
    private final AgentCommsRepository commsRepository;

    public GraphController(AgentRepository agentRepository, AgentCommsRepository commsRepository) {
        this.agentRepository = agentRepository;
        this.commsRepository = commsRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.AGENTS_READ + "')")
    @Transactional(readOnly = true)
    public Map<String, Object> graph() {
        List<Agent> agents = agentRepository.findAllByOrderByIdAsc();
        List<AgentComms> comms = commsRepository.findTop60ByOrderByTsDesc();

        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Agent a : agents) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", a.getId());
            node.put("name", a.getName());
            node.put("icon", a.getIcon());
            node.put("role", a.getRoleTitle());
            node.put("status", a.getStatus().name());
            node.put("lastRisk", a.getLastRisk());
            node.put("integrity", Integrity.of(a));
            node.put("tools", a.getTools());
            node.put("blockedResources", a.getBlockedResources());
            nodes.add(node);
        }

        Map<String, Map<String, Object>> edgeIndex = new LinkedHashMap<>();
        for (AgentComms c : comms) {
            String key = c.getFromAgent() + "->" + c.getToAgent();
            @SuppressWarnings("unchecked")
            Map<String, Object> edge = edgeIndex.computeIfAbsent(key, k -> {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("from", c.getFromAgent());
                e.put("to", c.getToAgent());
                e.put("count", 0);
                e.put("flagged", false);
                e.put("lastMessage", "");
                e.put("lastTs", c.getTs());
                return e;
            });
            edge.put("count", ((Integer) edge.get("count")) + 1);
            if (c.isFlagged()) {
                edge.put("flagged", true);
            }
            edge.put("lastMessage", c.getMessage());
            edge.put("lastTs", c.getTs());
        }

        List<Map<String, Object>> chains = new ArrayList<>();
        for (AgentComms c : comms) {
            if (c.isFlagged()) {
                Map<String, Object> chain = new LinkedHashMap<>();
                chain.put("from", c.getFromAgent());
                chain.put("to", c.getToAgent());
                chain.put("message", c.getMessage());
                chain.put("ts", c.getTs());
                chains.add(chain);
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nodes", nodes);
        body.put("edges", new ArrayList<>(edgeIndex.values()));
        body.put("suspiciousChains", chains.stream().limit(10).toList());
        return body;
    }

    /** BFS attack-path prediction: which peers/tools/stores an infected agent could reach. */
    @GetMapping("/blast")
    @PreAuthorize("hasAuthority('" + Permissions.AGENTS_READ + "')")
    @Transactional(readOnly = true)
    public Map<String, Object> blast(@RequestParam(name = "from") String fromId) {
        List<Agent> agents = agentRepository.findAllByOrderByIdAsc();
        Map<String, Agent> byId = new LinkedHashMap<>();
        agents.forEach(a -> byId.put(a.getId(), a));
        Agent start = byId.get(fromId);
        if (start == null) {
            return Map.of("error", "unknown agent");
        }
        List<AgentComms> comms = commsRepository.findTop60ByOrderByTsDesc();

        Set<String> infected = new LinkedHashSet<>(List.of(start.getId()));
        List<Map<String, Object>> paths = new ArrayList<>();
        Deque<String> frontier = new ArrayDeque<>(List.of(start.getId()));
        for (int depth = 0; depth < 3 && !frontier.isEmpty(); depth++) {
            List<String> level = new ArrayList<>(frontier);
            frontier.clear();
            for (String id : level) {
                Agent current = byId.get(id);
                if (current == null) {
                    continue;
                }
                for (AgentComms c : comms) {
                    String neighbor = Objects.equals(c.getFromAgent(), id) ? c.getToAgent()
                            : Objects.equals(c.getToAgent(), id) ? c.getFromAgent() : null;
                    if (neighbor != null && !Objects.equals(neighbor, id) && infected.add(neighbor)) {
                        paths.add(path(id, neighbor, depth + 1, null));
                        frontier.add(neighbor);
                    }
                }
                for (Agent other : agents) {
                    if (infected.contains(other.getId())) {
                        continue;
                    }
                    long shared = current.getTools().stream().filter(other.getTools()::contains).count();
                    if (shared >= 2) {
                        String tool = current.getTools().stream().filter(other.getTools()::contains)
                                .findFirst().orElse(null);
                        infected.add(other.getId());
                        paths.add(path(id, other.getId(), depth + 1, tool));
                        frontier.add(other.getId());
                    }
                }
            }
        }

        List<String> predictions = new ArrayList<>();
        for (String id : infected) {
            Agent a = byId.get(id);
            if (a == null || a.getId().equals(start.getId())) {
                continue;
            }
            String tool = a.getTools().stream().filter(DataCatalog.TOOLS::contains).findFirst()
                    .orElse(a.getTools().stream().findFirst().orElse("?"));
            String resource = a.getAllowed().stream().findFirst()
                    .map(DataCatalog::labelOf).orElse("sensitive store");
            predictions.add(a.getName() + " could abuse " + tool + " to reach " + resource);
            if (predictions.size() >= 5) {
                break;
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("start", start.getId());
        body.put("infected", infected);
        body.put("paths", paths);
        body.put("predictions", predictions);
        body.put("atRisk", Math.max(0, infected.size() - 1));
        return body;
    }

    private Map<String, Object> path(String from, String to, int depth, String tool) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("from", from);
        p.put("to", to);
        p.put("depth", depth);
        if (tool != null) {
            p.put("tool", tool);
        }
        return p;
    }
}

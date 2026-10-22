package com.argus.sim;

import com.argus.agent.Agent;
import com.argus.agent.AgentRepository;
import com.argus.catalog.DataCatalog;
import com.argus.engine.ActionCommand;
import com.argus.engine.ActionProcessor;
import com.argus.security.Permissions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ambient "autopilot" traffic so the live dashboard is never empty (can be toggled off). */
@Service
public class SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);

    private final AgentRepository agentRepository;
    private final ActionProcessor actionProcessor;
    private final TransactionTemplate transactionTemplate;
    private volatile boolean running;

    public SimulationService(AgentRepository agentRepository, ActionProcessor actionProcessor,
                             PlatformTransactionManager transactionManager,
                             @Value("${argus.sim.autopilot-enabled:true}") boolean enabled) {
        this.agentRepository = agentRepository;
        this.actionProcessor = actionProcessor;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.running = enabled;
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "${argus.sim.tick-ms:2100}")
    public void tick() {
        if (!running) {
            return;
        }
        try {
            if (ThreadLocalRandom.current().nextDouble() < 0.85) {
                transactionTemplate.executeWithoutResult(status -> ambient());
            }
        } catch (Exception e) {
            log.debug("Autopilot tick skipped: {}", e.getMessage());
        }
    }

    private void ambient() {
        List<Agent> active = new ArrayList<>();
        agentRepository.findAll().forEach(a -> {
            if (a.getStatus() == Agent.Status.ACTIVE) {
                active.add(a);
            }
        });
        if (active.isEmpty()) {
            return;
        }
        Agent agent = active.get(ThreadLocalRandom.current().nextInt(active.size()));
        if (agent.getTools().isEmpty()) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double roll = random.nextDouble();

        if (roll < 0.06) {
            List<String> denied = agent.getRestricted().stream()
                    .filter(c -> !agent.getAllowed().contains(c))
                    .toList();
            if (!denied.isEmpty()) {
                String cat = denied.get(random.nextInt(denied.size()));
                actionProcessor.submit(new ActionCommand(agent.getId(), "read",
                        agent.getTools().iterator().next(),
                        cat, "/" + cat + "/live_" + random.nextInt(900), null, null, null, null));
                return;
            }
        }
        if (roll < 0.10 && agent.getTools().contains("mail_gateway")) {
            List<Agent> peers = new ArrayList<>();
            agentRepository.findAll().forEach(p -> {
                if (!p.getId().equals(agent.getId()) && p.getStatus() == Agent.Status.ACTIVE) {
                    peers.add(p);
                }
            });
            if (!peers.isEmpty()) {
                Agent peer = peers.get(random.nextInt(peers.size()));
                actionProcessor.submit(new ActionCommand(agent.getId(), "communicate", "mail_gateway",
                        null, null, "Status sync: any blockers on your side?", peer.getId(), null, null));
                return;
            }
        }
        List<String> allowed = new ArrayList<>(agent.getAllowed());
        if (allowed.isEmpty()) {
            return;
        }
        String cat = allowed.get(random.nextInt(allowed.size()));
        if (!DataCatalog.isKnownCategory(cat)) {
            return;
        }
        List<String> kinds = List.of("read", "read", "read", "api_call", "api_call", "write");
        String kind = kinds.get(random.nextInt(kinds.size()));
        List<String> tools = new ArrayList<>(agent.getTools());
        String tool = tools.get(random.nextInt(tools.size()));
        String target = "api_call".equals(kind)
                ? tool + "://v2/" + cat
                : "/" + cat + "/doc_" + random.nextInt(9999);
        actionProcessor.submit(new ActionCommand(agent.getId(), kind, tool, cat, target, null, null, null, null));
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    @RestController
    @RequestMapping("/api/sim")
    public static class SimController {

        private final SimulationService simulationService;

        public SimController(SimulationService simulationService) {
            this.simulationService = simulationService;
        }

        public record AutopilotRequest(Boolean enabled) {
        }

        @GetMapping("/status")
        @PreAuthorize("hasAuthority('" + Permissions.DASHBOARD_READ + "')")
        public java.util.Map<String, Object> status() {
            return java.util.Map.of("autopilot", simulationService.isRunning());
        }

        @PostMapping("/autopilot")
        @PreAuthorize("hasAuthority('" + Permissions.SIM_CONTROL + "')")
        public java.util.Map<String, Object> autopilot(@RequestBody AutopilotRequest request) {
            boolean enabled = Boolean.TRUE.equals(request.enabled());
            simulationService.setRunning(enabled);
            return java.util.Map.of("autopilot", enabled);
        }
    }
}

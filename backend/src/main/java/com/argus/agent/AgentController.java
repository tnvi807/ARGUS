package com.argus.agent;

import com.argus.event.EventDtos;
import com.argus.security.Permissions;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping("/agents")
    @PreAuthorize("hasAuthority('" + Permissions.AGENTS_READ + "')")
    public List<EventDtos.AgentSummary> list() {
        return agentService.list();
    }

    @GetMapping("/agents/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.AGENTS_READ + "')")
    public EventDtos.AgentSummary get(@PathVariable String id) {
        return agentService.get(id);
    }

    @PostMapping("/agents")
    @PreAuthorize("hasAuthority('" + Permissions.AGENTS_WRITE + "')")
    public ResponseEntity<EventDtos.AgentSummary> create(
            @Valid @RequestBody AgentDtos.AgentPolicyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(agentService.create(request));
    }

    @PutMapping("/agents/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.AGENTS_WRITE + "')")
    public EventDtos.AgentSummary update(@PathVariable String id,
                                         @Valid @RequestBody AgentDtos.AgentPolicyRequest request) {
        return agentService.update(id, request);
    }

    @DeleteMapping("/agents/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.POLICIES_WRITE + "')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        agentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/agents/{id}/status")
    @PreAuthorize("hasAuthority('" + Permissions.ALERTS_ACT + "')")
    public EventDtos.AgentSummary setStatus(@PathVariable String id,
                                            @Valid @RequestBody AgentDtos.StatusRequest request) {
        return agentService.setStatus(id, request.status());
    }

    @PostMapping("/agents/{id}/isolate")
    @PreAuthorize("hasAuthority('" + Permissions.ALERTS_ACT + "')")
    public EventDtos.AgentSummary isolate(@PathVariable String id) {
        return agentService.isolate(id);
    }

    @PostMapping("/agents/{id}/restore")
    @PreAuthorize("hasAuthority('" + Permissions.ADMIN_MANAGE + "')")
    public EventDtos.AgentSummary restore(@PathVariable String id) {
        return agentService.restore(id);
    }

    @GetMapping("/agents/{id}/drift")
    @PreAuthorize("hasAuthority('" + Permissions.AGENTS_READ + "')")
    public AgentDtos.DriftView drift(@PathVariable String id) {
        return agentService.drift(id);
    }

    @GetMapping("/policies/templates")
    @PreAuthorize("hasAuthority('" + Permissions.AGENTS_READ + "')")
    public List<Map<String, Object>> templates() {
        return AgentService.templatesAsMaps();
    }

    @GetMapping("/policies/catalog")
    @PreAuthorize("hasAuthority('" + Permissions.AGENTS_READ + "')")
    public Map<String, Object> catalog() {
        return PolicyTemplates.catalog();
    }
}

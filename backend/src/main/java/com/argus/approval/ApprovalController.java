package com.argus.approval;

import com.argus.agent.Agent;
import com.argus.agent.AgentRepository;
import com.argus.audit.AuditWriter;
import com.argus.catalog.DataCatalog;
import com.argus.common.ApiErrorException;
import com.argus.engine.ActionCommand;
import com.argus.engine.ActionProcessor;
import com.argus.event.ActionEvent;
import com.argus.event.ActionEventRepository;
import com.argus.runtime.MockToolRuntime;
import com.argus.security.Permissions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Administrator approval queue: held actions execute only after an explicit, audited approval. */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    public record DecisionRequest(
            @NotBlank(message = "decision is required")
            @Pattern(regexp = "APPROVE|DENY", message = "decision must be APPROVE or DENY")
            String decision,
            @jakarta.validation.constraints.Size(max = 300) String note) {
    }

    private final ApprovalRequestRepository repository;
    private final AgentRepository agentRepository;
    private final ActionEventRepository eventRepository;
    private final ActionProcessor actionProcessor;
    private final AuditWriter auditWriter;

    public ApprovalController(ApprovalRequestRepository repository, AgentRepository agentRepository,
                              ActionEventRepository eventRepository, ActionProcessor actionProcessor,
                              AuditWriter auditWriter) {
        this.repository = repository;
        this.agentRepository = agentRepository;
        this.eventRepository = eventRepository;
        this.actionProcessor = actionProcessor;
        this.auditWriter = auditWriter;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.APPROVALS_READ + "')")
    public List<ApprovalRequest> list(@RequestParam(name = "status", required = false) String status) {
        List<ApprovalRequest> approvals = "PENDING".equalsIgnoreCase(status)
                ? repository.findTop50ByStatusOrderByTsDesc(ApprovalRequest.Status.PENDING)
                : repository.findTop100ByOrderByTsDesc();
        return approvals;
    }

    @PostMapping("/{id}/decision")
    @PreAuthorize("hasAuthority('" + Permissions.APPROVALS_ACT + "')")
    @Transactional
    public ApprovalRequest decide(@PathVariable Long id, @Valid @RequestBody DecisionRequest request) {
        ApprovalRequest approval = repository.findById(id)
                .orElseThrow(() -> ApiErrorException.notFound("Approval request"));
        if (approval.getStatus() != ApprovalRequest.Status.PENDING) {
            throw ApiErrorException.badRequest("Approval request has already been decided");
        }
        Agent agent = agentRepository.findById(approval.getAgentId())
                .orElseThrow(() -> ApiErrorException.notFound("Agent"));
        if (agent.getStatus() == Agent.Status.ISOLATED) {
            throw ApiErrorException.badRequest("Agent is isolated - restore it before approving actions");
        }
        String actor = currentActor();
        approval.setDecidedBy(actor);
        approval.setDecidedAt(Instant.now());
        approval.setDecisionNote(request.note());

        if ("DENY".equals(request.decision())) {
            approval.setStatus(ApprovalRequest.Status.DENIED);
            if (approval.getEventId() != null) {
                eventRepository.findById(approval.getEventId()).ifPresent(event -> {
                    event.setExecutionDetail("Denied by " + actor
                            + (request.note() == null ? "" : " - " + request.note()) + " - no side effects applied");
                    eventRepository.save(event);
                });
            }
            auditWriter.record("approval", "denied", approval.getAgentName(), null,
                    "Approval #" + approval.getId() + " denied", "BLOCKED", approval.getRiskScore(),
                    request.note() == null ? "administrator denied held action" : request.note(),
                    null, actor, approval.getTarget());
            return repository.save(approval);
        }

        ActionEvent event = approval.getEventId() == null ? null
                : eventRepository.findById(approval.getEventId()).orElse(null);
        if (event == null) {
            throw ApiErrorException.badRequest("Original event for this approval is unavailable");
        }
        ActionCommand command = new ActionCommand(approval.getAgentId(), approval.getKind(),
                approval.getTool(), approval.getCategory(), approval.getTarget(),
                approval.getMessage(), null, null, null);
        MockToolRuntime.ExecOutcome outcome = actionProcessor.executeApproved(agent, command, event, approval);

        approval.setStatus(ApprovalRequest.Status.APPROVED);
        approval.setExecutedAt(Instant.now());
        approval.setExecutionDetail(outcome.detail());
        ApprovalRequest saved = repository.save(approval);

        auditWriter.record("approval", "approved", approval.getAgentName(), agent.getRoleTitle(),
                "Approval #" + approval.getId() + " granted - action executed", "ALLOWED",
                approval.getRiskScore(), "administrator override (" + outcome.detail() + ")",
                null, actor, approval.getTarget());
        return saved;
    }

    private String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "unknown" : auth.getName();
    }
}

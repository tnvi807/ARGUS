package com.argus.engine;

import com.argus.catalog.DataCatalog;
import com.argus.common.ApiErrorException;
import com.argus.security.Permissions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual action submission for the "Simulate an agent action" gateway tool. Every submitted action
 * goes through the identical evaluate -&gt; decide -&gt; enforce pipeline as simulated traffic.
 */
@RestController
@RequestMapping("/api/actions")
public class ActionController {

    public record SubmitRequest(
            @NotBlank(message = "agentId is required")
            @Size(max = 40, message = "agentId is too long")
            String agentId,
            @NotBlank(message = "kind is required")
            @Size(max = 30, message = "kind is too long")
            String kind,
            @Size(max = 60, message = "tool is too long")
            String tool,
            @Size(max = 60, message = "category is too long")
            String category,
            @Size(max = 400, message = "target is too long")
            String target,
            @Size(max = 500, message = "message is too long")
            String message,
            @Size(max = 40, message = "toAgent is too long")
            String toAgent) {
    }

    public record SubmitView(Long eventId, String agentId, int riskScore, String decision,
                             String reason, List<RiskResult.RiskFactor> factors, Long alertId,
                             Long approvalId, boolean executed, String executionDetail) {
    }

    private final ActionProcessor actionProcessor;

    public ActionController(ActionProcessor actionProcessor) {
        this.actionProcessor = actionProcessor;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.ACTIONS_SUBMIT + "')")
    public SubmitView submit(@Valid @RequestBody SubmitRequest request) {
        validate(request);
        ActionCommand command = new ActionCommand(request.agentId(), request.kind(),
                blankToNull(request.tool()), blankToNull(request.category()),
                blankToNull(request.target()), blankToNull(request.message()),
                blankToNull(request.toAgent()), null, null);
        ActionProcessor.Processed processed;
        try {
            processed = actionProcessor.submit(command);
        } catch (ApiErrorException e) {
            throw e;
        } catch (RuntimeException e) {
            throw ApiErrorException.badRequest("Action could not be processed: " + e.getMessage());
        }
        return new SubmitView(processed.event().getId(), processed.event().getAgentId(),
                processed.result().score(), processed.result().decision(),
                processed.result().reason(), processed.result().factors(),
                processed.alert() == null ? null : processed.alert().getId(),
                processed.approval() == null ? null : processed.approval().getId(),
                processed.event().isExecuted(), processed.event().getExecutionDetail());
    }

    private void validate(SubmitRequest request) {
        if (!DataCatalog.ACTION_KINDS.contains(request.kind())) {
            throw ApiErrorException.badRequest("Unknown action kind '" + request.kind()
                    + "'. Permitted: " + String.join(", ", DataCatalog.ACTION_KINDS));
        }
        if (request.tool() != null && !request.tool().isBlank()
                && !DataCatalog.TOOLS.contains(request.tool())) {
            throw ApiErrorException.badRequest("Unknown tool '" + request.tool()
                    + "'. Permitted: " + String.join(", ", DataCatalog.TOOLS));
        }
        if (request.category() != null && !request.category().isBlank()
                && !DataCatalog.isKnownCategory(request.category())) {
            throw ApiErrorException.badRequest("Unknown data category '" + request.category()
                    + "'. See GET /api/policies/catalog");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

package com.argus.agent;

import com.argus.catalog.DataCatalog;
import com.argus.common.ApiErrorException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AgentDtos {

    private AgentDtos() {
    }

    public record AgentPolicyRequest(
            @Pattern(regexp = "[a-z0-9][a-z0-9_-]{1,39}",
                    message = "id must be 2-40 chars of lowercase letters, digits, _ or -")
            String id,
            @NotBlank(message = "name is required") @Size(max = 80) String name,
            @Size(max = 8) String icon,
            @NotBlank(message = "roleTitle is required") @Size(max = 80) String roleTitle,
            @Size(max = 400) String description,
            @NotEmpty(message = "at least one tool is required") List<String> tools,
            List<String> allowed,
            List<String> restricted,
            List<String> restrictActions,
            @Min(value = 1, message = "clearance must be 1-5")
            @Max(value = 5, message = "clearance must be 1-5")
            Integer clearance,
            Double baselineRate,
            Double baselineOffHours,
            Double baselineAvgSens,
            Map<String, Double> baselineCats) {
    }

    public record StatusRequest(
            @NotBlank(message = "status is required")
            @Pattern(regexp = "ACTIVE|PAUSED", message = "status must be ACTIVE or PAUSED")
            String status) {
    }

    public record DriftView(
            String agentId,
            String agentName,
            String icon,
            List<Point> points,
            double baselineAvgSens,
            double currentAvgSens,
            double slope,
            int integrity,
            String verdict,
            double offHoursPct,
            int novelCategories,
            int exfiltrationAttempts,
            int actionCount) {

        public record Point(String label, double sens) {
        }
    }

    /** Rejects unknown tools/categories so policies can never smuggle in arbitrary strings. */
    static void validatePolicy(AgentPolicyRequest req) {
        Set<String> tools = new LinkedHashSet<>(req.tools());
        for (String tool : tools) {
            if (!DataCatalog.TOOLS.contains(tool)) {
                throw ApiErrorException.badRequest("Unknown tool: " + tool);
            }
        }
        validateCategories(req.allowed(), "allowed");
        validateCategories(req.restricted(), "restricted");
        for (String kind : req.restrictActions() == null ? List.<String>of() : req.restrictActions()) {
            if (!DataCatalog.RESTRICT_POOL.contains(kind) && !DataCatalog.ACTION_KINDS.contains(kind)) {
                throw ApiErrorException.badRequest("Unknown restricted action: " + kind);
            }
        }
        if (req.baselineCats() != null) {
            for (String cat : req.baselineCats().keySet()) {
                if (!DataCatalog.isKnownCategory(cat)) {
                    throw ApiErrorException.badRequest("Unknown baseline category: " + cat);
                }
            }
        }
        Set<String> allowed = req.allowed() == null ? Set.of() : new LinkedHashSet<>(req.allowed());
        Set<String> restricted = req.restricted() == null ? Set.of() : new LinkedHashSet<>(req.restricted());
        for (String cat : restricted) {
            if (allowed.contains(cat)) {
                throw ApiErrorException.badRequest(
                        "Category '" + cat + "' cannot be both allowed and restricted");
            }
        }
    }

    private static void validateCategories(Collection<String> cats, String field) {
        if (cats == null) {
            return;
        }
        for (String cat : cats) {
            if (!DataCatalog.isKnownCategory(cat)) {
                throw ApiErrorException.badRequest("Unknown data category in " + field + ": " + cat);
            }
        }
    }
}

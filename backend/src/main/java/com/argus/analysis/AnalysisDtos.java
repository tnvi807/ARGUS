package com.argus.analysis;

import java.util.List;
import java.util.Map;

public final class AnalysisDtos {

    private AnalysisDtos() {
    }

    public record PromptRequest(
            @jakarta.validation.constraints.NotBlank(message = "content is required")
            @jakarta.validation.constraints.Size(max = 20000, message = "content is limited to 20000 characters")
            String content) {
    }

    public record Indicator(
            String type,
            String indicator,
            String severity,
            int weight,
            String evidence,
            int offset) {
    }

    public record AffectedAgent(
            String agentId,
            String name,
            String icon,
            String roleTitle,
            String relation) {
    }

    public record GatewayPreview(
            String agentId,
            String agentName,
            String kind,
            String tool,
            String category,
            String target,
            int score,
            String decision,
            List<com.argus.engine.RiskResult.RiskFactor> factors,
            String note) {
    }

    public record AnalysisResult(
            Long id,
            java.time.Instant ts,
            String source,
            String filename,
            String actor,
            List<Indicator> indicators,
            int score,
            String recommendation,
            String reason,
            boolean sensitiveDataRisk,
            int sensitiveValues,
            List<AffectedAgent> affectedAgents,
            GatewayPreview gateway,
            String textExcerpt,
            int textLength,
            Map<String, Object> policyImpact) {
    }
}

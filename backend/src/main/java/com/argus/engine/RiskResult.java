package com.argus.engine;

import java.util.List;

/**
 * Explainable risk outcome with additive factor attribution and the gateway's enforcement
 * decision: {@code ALLOWED} (execute), {@code RESTRICTED} (execute with redacted output),
 * {@code PENDING_APPROVAL} (hold for administrator approval) or {@code BLOCKED} (deny).
 */
public record RiskResult(int score, String decision, List<RiskFactor> factors, String reason) {

    public record RiskFactor(String name, int weight, String detail) {
    }

    public static final String ALLOWED = "ALLOWED";
    public static final String RESTRICTED = "RESTRICTED";
    public static final String PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String BLOCKED = "BLOCKED";

    /** 0-39 allow, 40-59 restrict, 60-69 require admin approval, 70-100 block. */
    public static String decisionFor(int score) {
        if (score >= 70) {
            return BLOCKED;
        }
        if (score >= 60) {
            return PENDING_APPROVAL;
        }
        if (score >= 40) {
            return RESTRICTED;
        }
        return ALLOWED;
    }

    public boolean executable() {
        return ALLOWED.equals(decision) || RESTRICTED.equals(decision);
    }

    public static RiskResult of(int score, List<RiskFactor> factors, String reason) {
        int clamped = clamp(score);
        return new RiskResult(clamped, decisionFor(clamped), List.copyOf(factors), reason);
    }

    static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}

package com.argus.agent;

import com.argus.engine.DriftAnalyzer;
import java.util.List;

/** Computes the Identity Fingerprint Integrity % used across API responses. */
public final class Integrity {

    private Integrity() {
    }

    public static int of(Agent agent) {
        List<Double> ys = agent.getDriftHistory().stream().map(DriftPoint::getSens).toList();
        return DriftAnalyzer.integrity(agent.getBaselineAvgSens(), ys, novelCount(agent));
    }

    public static int novelCount(Agent agent) {
        return (int) agent.getFingerprintCats().keySet().stream()
                .filter(c -> {
                    Double w = agent.getBaselineCats().get(c);
                    return w == null || w <= 0.08;
                })
                .count();
    }
}

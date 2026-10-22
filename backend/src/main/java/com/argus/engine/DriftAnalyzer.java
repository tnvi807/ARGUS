package com.argus.engine;

import java.util.List;

/**
 * Statistical helpers for the behavioural baseline & slow-drift defence (z-scores, least-squares
 * trend slope) and the Identity Fingerprint Integrity percentage.
 */
public final class DriftAnalyzer {

    private DriftAnalyzer() {
    }

    public static double zScore(double value, double baselineAvg) {
        double sigma = 1.15;
        return (value - baselineAvg) / sigma;
    }

    /** Least-squares slope of y over index (per-day drift rate). */
    public static double slope(List<Double> ys) {
        int n = ys == null ? 0 : ys.size();
        if (n < 3) {
            return 0;
        }
        double sx = 0, sy = 0, sxy = 0, sxx = 0;
        for (int i = 0; i < n; i++) {
            double y = ys.get(i);
            sx += i;
            sy += y;
            sxy += i * y;
            sxx += (double) i * i;
        }
        double den = n * sxx - sx * sx;
        if (den == 0) {
            return 0;
        }
        return (n * sxy - sx * sy) / den;
    }

    /** Identity Fingerprint Integrity % (0-100): 85+ stable, 60-84 drifting, &lt;60 compromised. */
    public static int integrity(double baselineAvgSens, List<Double> driftSens,
                                int novelCategoryCount) {
        double slope = slope(driftSens);
        double nowSens = (driftSens == null || driftSens.isEmpty()) ? baselineAvgSens
                : driftSens.get(driftSens.size() - 1);
        double sensDrift = clamp((nowSens - baselineAvgSens) / 3.5, 0, 0.55);
        double slopeDrift = clamp(slope / 1.2, 0, 0.30);
        double catCreep = clamp(novelCategoryCount * 0.12, 0, 0.25);
        double penalty = clamp(sensDrift + slopeDrift + catCreep, 0, 0.85);
        return (int) Math.round((1 - penalty) * 100);
    }

    public static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}

package com.looki.common.reliability;

/**
 * Score-based tiers that determine booking restrictions.
 * <pre>
 *   TRUSTED    80–120  — no restrictions
 *   NORMAL     50–79   — standard flow
 *   RISKY      20–49   — confirmation required, max 2 active bookings
 *   RESTRICTED  0–19   — deposit required / temporarily blocked
 * </pre>
 */
public enum ReliabilityTier {

    TRUSTED(80, 120),
    NORMAL(50, 79),
    RISKY(20, 49),
    RESTRICTED(0, 19);

    private final int minScore;
    private final int maxScore;

    ReliabilityTier(int minScore, int maxScore) {
        this.minScore = minScore;
        this.maxScore = maxScore;
    }

    public int getMinScore() { return minScore; }
    public int getMaxScore() { return maxScore; }

    public static ReliabilityTier fromScore(int score) {
        if (score >= 80) return TRUSTED;
        if (score >= 50) return NORMAL;
        if (score >= 20) return RISKY;
        return RESTRICTED;
    }

    /** Human-readable description shown in the client app. */
    public String label() {
        return switch (this) {
            case TRUSTED    -> "Trusted";
            case NORMAL     -> "Normal";
            case RISKY      -> "Risky";
            case RESTRICTED -> "Restricted";
        };
    }
}

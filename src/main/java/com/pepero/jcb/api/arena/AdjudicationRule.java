package com.pepero.jcb.api.arena;

public record AdjudicationRule(
        int minMoveNumber, // adjust after this full move
        int moveCount, // during this full move count
        int scoreThresholdCP, // upper/downer this cp
        int scoreToleranceCP // allow tolerance
) {
    public boolean isWithinThreshold(int scoreCentipawns, boolean isResignCheck) {
        if (isResignCheck) {
            return scoreCentipawns <= scoreThresholdCP + scoreToleranceCP;
        } else {
            return Math.abs(scoreCentipawns) <= scoreThresholdCP + scoreToleranceCP;
        }
    }
}
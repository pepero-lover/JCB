package com.pepero.jcb.api.arena;

public interface MatchRunnerListener {
    void onGameFinished(int roundNumber, MatchResult result, MatchStatistics runningStats);
    default void onGameFailed(int roundNumber, Throwable cause) {}
    default void onAllGamesFinished(MatchStatistics finalStats, boolean stoppedEarly) {}
}
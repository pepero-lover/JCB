package com.pepero.jcb.api.arena;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stores match result stat, like engine 1 / 2 win count, draw count, etc.
 */
public class MatchStatistics {
    private final AtomicInteger engine1Wins = new AtomicInteger();
    private final AtomicInteger engine2Wins = new AtomicInteger();
    private final AtomicInteger whiteWins = new AtomicInteger();
    private final AtomicInteger blackWins = new AtomicInteger();
    private final AtomicInteger draws = new AtomicInteger();
    private final AtomicInteger aborted = new AtomicInteger();
    private final AtomicInteger errors = new AtomicInteger();

    public void record(MatchResult result) {
        switch (result.matchWinner()) {
            case ENGINE1 -> engine1Wins.incrementAndGet();
            case ENGINE2 -> engine2Wins.incrementAndGet();
            case DRAW -> draws.incrementAndGet();
            case ABORTED -> aborted.incrementAndGet();
            case UNKNOWN -> errors.incrementAndGet();
        }
        switch (result.result()) {
            case WHITE_WON -> whiteWins.incrementAndGet();
            case BLACK_WON -> blackWins.incrementAndGet();
        }
    }

    public void recordError() {
        errors.incrementAndGet();
    }

    /**
     * Get engine 1 win count
     */
    public int getEngine1Wins() { return engine1Wins.get(); }

    /**
     * Get engine 2 win count
     */
    public int getEngine2Wins() { return engine2Wins.get(); }

    /**
     * Get white win count
     */
    public int getWhiteWins() { return whiteWins.get(); }

    /**
     * Get black win count
     */
    public int getBlackWins() { return blackWins.get(); }

    /**
     * Get draw count
     */
    public int getDraws() { return draws.get(); }

    /**
     * Get abort count <br>
     * This doesn't count the unstarted games count, but only had run games count
     */
    public int getAborts() { return aborted.get(); }

    /**
     * Get the count of games that occurred crash or error
     */
    public int getErrors() { return errors.get(); }

    /**
     * Get the total count of completed games <br>
     * This method only counts games that ended successfully. <br>
     * Doesn't count crashed ({@link #getErrors()}} or aborted ({@link #getAborts()}).
     */
    public int getTotalCompleted() { return engine1Wins.get() + engine2Wins.get() + draws.get(); }

    @Override
    public String toString() {
        return "Engine1: %d, Engine2: %d, Draws: %d, Aborted: %d, Errors: %d,   White Won : %d, Draws : %d, Black Won : %d"
                .formatted(getEngine1Wins(), getEngine2Wins(), getDraws(), getAborts(), getErrors(),
                        getWhiteWins(), getDraws(), getBlackWins());
    }
}
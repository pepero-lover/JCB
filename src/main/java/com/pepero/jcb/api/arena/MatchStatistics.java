package com.pepero.jcb.api.arena;

import com.pepero.jcb.api.dto.MatchResult;

import java.util.concurrent.atomic.AtomicInteger;

public class MatchStatistics {
    private final AtomicInteger engine1Wins = new AtomicInteger();
    private final AtomicInteger engine2Wins = new AtomicInteger();
    private final AtomicInteger whiteWins = new AtomicInteger();
    private final AtomicInteger blackWins = new AtomicInteger();
    private final AtomicInteger draws = new AtomicInteger();
    private final AtomicInteger errors = new AtomicInteger();

    public void record(MatchResult result) {
        switch (result.engineWinner()) {
            case ENGINE1 -> engine1Wins.incrementAndGet();
            case ENGINE2 -> engine2Wins.incrementAndGet();
            case DRAW -> draws.incrementAndGet();
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

    public int getEngine1Wins() { return engine1Wins.get(); }
    public int getEngine2Wins() { return engine2Wins.get(); }
    public int getWhiteWins() { return whiteWins.get(); }
    public int getBlackWins() { return blackWins.get(); }
    public int getDraws() { return draws.get(); }
    public int getErrors() { return errors.get(); }
    public int getTotalCompleted() { return engine1Wins.get() + engine2Wins.get() + draws.get(); }

    @Override
    public String toString() {
        return "Engine1: %d, Engine2: %d, Draws: %d, Errors: %d,   White Won : %d, Draws : %d, Black Won : %d"
                .formatted(getEngine1Wins(), getEngine2Wins(), getDraws(), getErrors(),
                        getWhiteWins(), getDraws(), getBlackWins());
    }
}
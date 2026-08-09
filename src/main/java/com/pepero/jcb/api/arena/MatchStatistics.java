package com.pepero.jcb.api.arena;

import java.util.concurrent.atomic.AtomicInteger;

public class MatchStatistics {
    private final AtomicInteger engine1Wins = new AtomicInteger();
    private final AtomicInteger engine2Wins = new AtomicInteger();
    private final AtomicInteger draws = new AtomicInteger();
    private final AtomicInteger errors = new AtomicInteger();

    public void record(EngineWinner winner) {
        switch (winner) {
            case ENGINE1 -> engine1Wins.incrementAndGet();
            case ENGINE2 -> engine2Wins.incrementAndGet();
            case DRAW -> draws.incrementAndGet();
            case UNKNOWN -> errors.incrementAndGet();
        }
    }

    public void recordError() {
        errors.incrementAndGet();
    }

    public int getEngine1Wins() { return engine1Wins.get(); }
    public int getEngine2Wins() { return engine2Wins.get(); }
    public int getDraws() { return draws.get(); }
    public int getErrors() { return errors.get(); }
    public int getTotalCompleted() { return engine1Wins.get() + engine2Wins.get() + draws.get(); }

    @Override
    public String toString() {
        return "Engine1: %d, Engine2: %d, Draws: %d, Errors: %d"
                .formatted(getEngine1Wins(), getEngine2Wins(), getDraws(), getErrors());
    }
}
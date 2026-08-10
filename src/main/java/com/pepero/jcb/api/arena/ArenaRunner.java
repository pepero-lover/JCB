package com.pepero.jcb.api.arena;

import com.pepero.jcb.api.dto.MatchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ArenaRunner {

    private final EngineArena arena;
    private final MatchConfig matchConfig;
    private final MatchStatistics statistics = new MatchStatistics();
    private final AtomicInteger roundNumber = new AtomicInteger(1);

    public interface RunnerListener {
        void onGameFinished(int roundNumber, MatchResult result, MatchStatistics runningStats);
        default void onGameFailed(int roundNumber, Throwable cause) {}
    }

    public ArenaRunner(MatchConfig matchConfig) {
        this.matchConfig = matchConfig;
        this.arena = new EngineArena(matchConfig);
    }

    public void setArenaListener(EngineArena.ArenaListener listener) {
        arena.setArenaListener(listener);
    }

    /**
     * Run match and return match statistics
     *
     * @param runnerListener listener (can be null)
     * @return match stats
     */
    public MatchStatistics run(RunnerListener runnerListener) {
        int totalGames = matchConfig.getTotalGames();
        int concurrency = Math.max(1, matchConfig.getConcurrency());

        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < totalGames; i++) {
            int round = roundNumber.getAndIncrement();

            futures.add(pool.submit(() -> {
                try {
                    MatchResult result = arena.startMatch(round);
                    statistics.record(result);

                    if (runnerListener != null) {
                        runnerListener.onGameFinished(round, result, statistics);
                    }
                } catch (Exception e) {
                    statistics.recordError();
                    if (runnerListener != null) {
                        runnerListener.onGameFailed(round, e);
                    }
                }
                return null;
            }));
        }

        awaitAll(futures);
        pool.shutdown();

        return statistics;
    }

    private void awaitAll(List<Future<Void>> futures) {
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
    }

    public MatchStatistics getStatistics() {
        return statistics;
    }
}
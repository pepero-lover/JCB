package com.pepero.jcb.api.arena;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Engine tournament runner
 */
public class ArenaRunner {

    private final EngineArena arena;
    private final MatchConfig matchConfig;
    private final MatchStatistics statistics = new MatchStatistics();
    private final AtomicInteger roundNumber = new AtomicInteger(1);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final ConcurrentHashMap<Integer, CancellationToken> activeTokens = new ConcurrentHashMap<>();
    private volatile ExecutorService pool;

    public interface RunnerListener {
        void onGameFinished(int roundNumber, MatchResult result, MatchStatistics runningStats);
        default void onGameFailed(int roundNumber, Throwable cause) {}
        default void onAllGamesFinished(MatchStatistics finalStats, boolean stoppedEarly) {}
    }

    public ArenaRunner(MatchConfig matchConfig) {
        this.matchConfig = matchConfig;
        this.arena = new EngineArena(matchConfig);
    }

    /**
     * Stop current playing games and stop creating new games
     */
    public void stop() {
        if (stopRequested.compareAndSet(false, true)) {
            activeTokens.values().forEach(CancellationToken::cancel);
            if (pool != null) {
                pool.shutdown();
            }
        }
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

        pool = Executors.newFixedThreadPool(concurrency);
        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < totalGames; i++) {
            if (stopRequested.get()) break;

            int round = roundNumber.getAndIncrement();
            CancellationToken token = new CancellationToken();
            activeTokens.put(round, token);

            futures.add(pool.submit(() -> {
                try {
                    MatchResult result = arena.startMatch(round, token);
                    statistics.record(result);

                    if (runnerListener != null) {
                        runnerListener.onGameFinished(round, result, statistics);
                    }
                } catch (Exception e) {
                    statistics.recordError();
                    if (runnerListener != null) {
                        runnerListener.onGameFailed(round, e);
                    }
                } finally {
                    activeTokens.remove(round);
                }
                return null;
            }));
        }

        awaitAll(futures);
        pool.shutdown();

        if (runnerListener != null) {
            runnerListener.onAllGamesFinished(statistics, stopRequested.get());
        }

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
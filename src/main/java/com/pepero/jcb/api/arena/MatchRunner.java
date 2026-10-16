package com.pepero.jcb.api.arena;

import com.pepero.jcb.api.exception.engine.UCIEngineException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Engine tournament runner
 */
public class MatchRunner {

    private final EngineArena arena;
    private final MatchConfig matchConfig;
    private final MatchStatistics statistics = new MatchStatistics();
    private final AtomicInteger roundNumber = new AtomicInteger(1);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final ConcurrentHashMap<Integer, MatchCancellationToken> activeTokens = new ConcurrentHashMap<>();
    private volatile ExecutorService pool;

    /**
     * @param matchConfig match config
     * @throws UCIEngineException if an engine executable (or its working directory) is missing.
     */
    public MatchRunner(MatchConfig matchConfig) {
        this.matchConfig = Objects.requireNonNull(matchConfig, "Match config can not be null!");

        validateEngine(matchConfig.getEngine1Config());
        validateEngine(matchConfig.getEngine2Config());

        this.arena = new EngineArena(matchConfig);
    }

    /**
     * Stop current playing games and stop creating new games
     */
    public void stop() {
        if (stopRequested.compareAndSet(false, true)) {
            activeTokens.values().forEach(MatchCancellationToken::cancel);
            if (pool != null) {
                pool.shutdown();
            }
        }
    }

    public void setArenaListener(MatchListener listener) {
        arena.setArenaListener(listener);
    }

    /**
     * Run match and return match statistics
     *
     * @param runnerListener listener (can be null)
     * @return match stats
     */
    public MatchStatistics run(MatchRunnerListener runnerListener) {
        int totalGames = matchConfig.getTotalGames();
        int concurrency = Math.max(1, matchConfig.getConcurrency());

        pool = Executors.newFixedThreadPool(concurrency);
        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < totalGames; i++) {
            if (stopRequested.get()) break;

            int round = roundNumber.getAndIncrement();
            MatchCancellationToken token = new MatchCancellationToken();
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

    /**
     * Check that the engine can be started before any game is created.
     */
    private static void validateEngine(EngineConfig config) {
        Path command = config.command();

        if (!isRegularFile(command)) {
            throw new UCIEngineException("Engine \"" + config.name() + "\" executable not found: " + command);
        }

        if (!isWindows() && !Files.isExecutable(command)) {
            throw new UCIEngineException("Engine \"" + config.name() + "\" is not executable (chmod +x?): " + command);
        }

        Path workingDirectory = config.workingDirectory();
        if (workingDirectory == null || !Files.isDirectory(workingDirectory)) {
            throw new UCIEngineException("Engine \"" + config.name() + "\" working directory not found: "
                    + workingDirectory);
        }
    }

    private static boolean isRegularFile(Path command) {
        if (Files.isRegularFile(command)) return true;

        // On Windows, a command without an extension is started as "<name>.exe"
        if (isWindows()) {
            String fileName = command.getFileName().toString();
            return !fileName.contains(".") && Files.isRegularFile(command.resolveSibling(fileName + ".exe"));
        }

        return false;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }
}
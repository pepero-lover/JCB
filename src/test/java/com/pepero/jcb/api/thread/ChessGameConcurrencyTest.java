package com.pepero.jcb.api.thread;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.ChessGameListener;
import com.pepero.jcb.api.dto.MoveInfo;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * Multi-thread concurrency test for {@link ChessGame}. <p>
 *
 * Scenarios : <br>
 * 1. Single writer + many readers (readers must never see a torn / half-applied state) <br>
 * 2. Multiple writers (make+unmake pair guarded by an external lock) + many readers <br>
 * 3. Listener re-entrancy (listener reads / writes the game inside a callback, must not deadlock) <br>
 * 4. Listener exception isolation (a throwing listener must not break others or the game state) <br>
 * 5. Listener add/remove churn while moves are being made
 */
public class ChessGameConcurrencyTest {

    private static final int TIMEOUT_SECONDS = 20;

    public static void main(String[] args) throws Exception {
        System.out.println("Multi-thread concurrency test");

        Map<String, Boolean> results = new LinkedHashMap<>();
        results.put("1 writer / 10 readers consistency", runConsistencyTest("single-writer", 1, 10, 10_000));
        results.put("4 writers (externally paired) / 8 readers consistency", runConsistencyTest("multi-writer", 4, 8, 5_000));
        results.put("listener re-entrancy", runListenerReentrancyTest());
        results.put("listener exception isolation", runListenerExceptionIsolationTest());
        results.put("listener add/remove churn", runListenerChurnTest(10_000));

        System.out.println("=========================================");
        boolean allPassed = true;
        for (Map.Entry<String, Boolean> entry : results.entrySet()) {
            System.out.println((entry.getValue() ? "[PASS] " : "[FAIL] ") + entry.getKey());
            allPassed &= entry.getValue();
        }
        System.out.println(allPassed ? "ALL TESTS PASSED" : "SOME TESTS FAILED");
        System.out.println("=========================================");

        // non-zero exit code on failure (also kills threads that are stuck after a deadlock)
        System.exit(allPassed ? 0 : 1);
    }

    // ------------------------------------------------------------------------------------
    // Scenario 1, 2 : readers must only ever observe the "before" or "after" position
    // ------------------------------------------------------------------------------------

    private static boolean runConsistencyTest(String name, int writerCount, int readerCount, int repeatCount)
            throws InterruptedException {
        System.out.printf("[%s] writers=%d, readers=%d, repeat=%d%n", name, writerCount, readerCount, repeatCount);

        // reference values (the only two positions a reader is allowed to see)
        ChessGame reference = ChessGame.startPosition();
        final String startFen = reference.getFEN();
        final long startHash = reference.getZobristHash();
        reference.makeMoveLan("e2e4");
        final String e4Fen = reference.getFEN();
        final long e4Hash = reference.getZobristHash();

        final ChessGame game = ChessGame.startPosition();
        final CountingListener listener = new CountingListener();
        game.addChessGameListener(listener);

        final Failures failures = new Failures();
        // make+unmake is NOT atomic across calls (see ChessGame javadoc), so multiple writers need an external lock
        final Object pairLock = new Object();
        final AtomicInteger activeWriters = new AtomicInteger(writerCount);
        final LongAdder readIterations = new LongAdder();

        List<Runnable> tasks = new ArrayList<>();

        for (int w = 0; w < writerCount; w++) {
            tasks.add(() -> {
                try {
                    for (int i = 0; i < repeatCount; i++) {
                        synchronized (pairLock) {
                            game.makeMoveLan("e2e4");
                            Thread.yield(); // let readers observe the intermediate position
                            game.unmakeMove();
                        }
                    }
                } catch (Throwable t) {
                    failures.report("writer", t);
                } finally {
                    activeWriters.decrementAndGet();
                }
            });
        }

        for (int r = 0; r < readerCount; r++) {
            tasks.add(() -> {
                try {
                    // keep reading until every writer is done, so reads overlap the whole write phase
                    while (activeWriters.get() > 0) {
                        String fen = game.getFEN();
                        if (!fen.equals(startFen) && !fen.equals(e4Fen)) {
                            failures.report("torn FEN: " + fen);
                        }

                        long hash = game.getZobristHash();
                        if (hash != startHash && hash != e4Hash) {
                            failures.report("unexpected Zobrist hash: " + hash);
                        }

                        List<MoveInfo> moves = game.getLegalMoves();
                        if (moves.size() != 20) { // both positions have exactly 20 legal moves
                            failures.report("unexpected legal move count: " + moves.size());
                        }

                        if (game.isCheck()) {
                            failures.report("isCheck() returned true");
                        }
                        if (game.getPieceScore() != 0) {
                            failures.report("unexpected piece score: " + game.getPieceScore());
                        }

                        // result depends on timing (legal on start pos, illegal after e4), but must never throw
                        game.canMakeMoveLan("e2e4");

                        readIterations.increment();
                    }
                } catch (Throwable t) {
                    failures.report("reader", t);
                }
            });
        }

        long begin = System.nanoTime();
        boolean finished = runConcurrently(tasks, TIMEOUT_SECONDS);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin);

        boolean ok = verify("all threads finished within " + TIMEOUT_SECONDS + "s", finished);
        if (finished) {
            int expected = writerCount * repeatCount;

            ok &= verify("no reader/writer errors" + failures.describe(), failures.count() == 0);
            ok &= verify("readers overlapped writers (" + readIterations.sum() + " read iterations)",
                    readIterations.sum() > 0);
            ok &= verify("onMoveMade count " + listener.made.get() + " == " + expected,
                    listener.made.get() == expected);
            ok &= verify("onMoveUnmade count " + listener.unmade.get() + " == " + expected,
                    listener.unmade.get() == expected);
            ok &= verify("onMoveRemade count " + listener.remade.get() + " == 0",
                    listener.remade.get() == 0);

            // final state must be back to the start position
            ok &= verify("final FEN is the start position", game.getFEN().equals(startFen));
            ok &= verify("final Zobrist hash is the start hash", game.getZobristHash() == startHash);
            ok &= verify("history has exactly one root child (no duplicated / leaked nodes)",
                    rootChildCount(game) == 1);

            System.out.println("  elapsed: " + elapsedMs + "ms");
        }
        return ok;
    }

    // ------------------------------------------------------------------------------------
    // Scenario 3 : listener touches the game inside callbacks (callbacks run outside the lock)
    // ------------------------------------------------------------------------------------

    private static boolean runListenerReentrancyTest() throws InterruptedException {
        System.out.println("[listener-reentrancy]");

        final ChessGame game = ChessGame.startPosition();
        final String startFen = game.getFEN();

        final AtomicInteger readsInCallback = new AtomicInteger();
        final AtomicInteger unmadeCount = new AtomicInteger();
        final AtomicInteger listenerErrors = new AtomicInteger();
        final AtomicBoolean reentered = new AtomicBoolean(false);

        game.setListenerExceptionHandler((l, e) -> listenerErrors.incrementAndGet());
        game.addChessGameListener(new ChessGameListener() {
            @Override
            public void onMoveMade(ChessGame source, MoveInfo moveInfo) {
                // read lock inside callback
                source.getFEN();
                source.getLegalMoves();
                readsInCallback.incrementAndGet();

                // write lock inside callback (only once, otherwise infinite recursion)
                if (reentered.compareAndSet(false, true)) {
                    source.unmakeMove();
                }
            }

            @Override
            public void onMoveUnmade(ChessGame source, MoveInfo unmadeMoveInfo) {
                unmadeCount.incrementAndGet();
            }
        });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> game.makeMoveLan("e2e4"));

        boolean finished = true;
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            finished = false;
            reportDeadlocks();
        } catch (Exception e) {
            System.err.println("  makeMoveLan threw: " + e);
            executor.shutdownNow();
            return verify("makeMoveLan did not throw", false);
        }
        executor.shutdownNow();

        boolean ok = verify("makeMoveLan with re-entrant listener finished (no deadlock)", finished);
        if (finished) {
            ok &= verify("callback could read the game (" + readsInCallback.get() + ")", readsInCallback.get() == 1);
            ok &= verify("callback could unmake the move (" + unmadeCount.get() + ")", unmadeCount.get() == 1);
            ok &= verify("no listener errors (" + listenerErrors.get() + ")", listenerErrors.get() == 0);
            ok &= verify("final FEN is the start position", game.getFEN().equals(startFen));
        }
        return ok;
    }

    // ------------------------------------------------------------------------------------
    // Scenario 4 : a throwing listener must not break other listeners or the game
    // ------------------------------------------------------------------------------------

    private static boolean runListenerExceptionIsolationTest() {
        System.out.println("[listener-exception-isolation]");

        final ChessGame game = ChessGame.startPosition();
        final String startFen = game.getFEN();

        final AtomicInteger handlerCalls = new AtomicInteger();
        game.setListenerExceptionHandler((l, e) -> handlerCalls.incrementAndGet()); // keep the log quiet

        // registered FIRST, so a broken isolation would starve the counting listener below
        game.addChessGameListener(new ChessGameListener() {
            @Override
            public void onMoveMade(ChessGame source, MoveInfo moveInfo) {
                throw new IllegalStateException("boom (made)");
            }

            @Override
            public void onMoveUnmade(ChessGame source, MoveInfo unmadeMoveInfo) {
                throw new IllegalStateException("boom (unmade)");
            }
        });

        CountingListener counting = new CountingListener();
        game.addChessGameListener(counting);

        boolean threw = false;
        try {
            game.makeMoveLan("e2e4");
            game.unmakeMove();
        } catch (Throwable t) {
            threw = true;
            t.printStackTrace();
        }

        boolean ok = verify("listener exception did not propagate to the caller", !threw);
        ok &= verify("exception handler called (" + handlerCalls.get() + ") == 2", handlerCalls.get() == 2);
        ok &= verify("other listener still got onMoveMade", counting.made.get() == 1);
        ok &= verify("other listener still got onMoveUnmade", counting.unmade.get() == 1);
        ok &= verify("final FEN is the start position", game.getFEN().equals(startFen));
        return ok;
    }

    // ------------------------------------------------------------------------------------
    // Scenario 5 : add/remove listeners while a writer is firing notifications
    // ------------------------------------------------------------------------------------

    private static boolean runListenerChurnTest(int repeatCount) throws InterruptedException {
        System.out.println("[listener-churn] repeat=" + repeatCount);

        final ChessGame game = ChessGame.startPosition();
        final CountingListener permanent = new CountingListener();
        game.addChessGameListener(permanent);

        final Failures failures = new Failures();
        final AtomicInteger activeWriters = new AtomicInteger(1);

        List<Runnable> tasks = new ArrayList<>();

        tasks.add(() -> {
            try {
                for (int i = 0; i < repeatCount; i++) {
                    game.makeMoveLan("e2e4");
                    game.unmakeMove();
                }
            } catch (Throwable t) {
                failures.report("writer", t);
            } finally {
                activeWriters.decrementAndGet();
            }
        });

        tasks.add(() -> {
            try {
                while (activeWriters.get() > 0) {
                    CountingListener temp = new CountingListener();
                    game.addChessGameListener(temp);
                    for (ChessGameListener ignored : game.getListeners()) {
                        // iterate the snapshot view while notifications are running
                    }
                    game.removeChessGameListener(temp);
                }
            } catch (Throwable t) {
                failures.report("churn", t);
            }
        });

        boolean finished = runConcurrently(tasks, TIMEOUT_SECONDS);

        boolean ok = verify("all threads finished within " + TIMEOUT_SECONDS + "s", finished);
        if (finished) {
            ok &= verify("no errors" + failures.describe(), failures.count() == 0);
            ok &= verify("permanent listener onMoveMade " + permanent.made.get() + " == " + repeatCount,
                    permanent.made.get() == repeatCount);
            ok &= verify("permanent listener onMoveUnmade " + permanent.unmade.get() + " == " + repeatCount,
                    permanent.unmade.get() == repeatCount);
            ok &= verify("only the permanent listener remains (" + game.getListeners().size() + ")",
                    game.getListeners().size() == 1);
        }
        return ok;
    }

    // ------------------------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------------------------

    /**
     * Run all tasks at the same moment (start latch). Tasks must catch their own throwables.
     *
     * @return false if the tasks did not finish in time (deadlock info is printed to stderr)
     */
    private static boolean runConcurrently(List<Runnable> tasks, int timeoutSeconds) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(tasks.size());

        for (Runnable task : tasks) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    task.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = endLatch.await(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            reportDeadlocks();
        }

        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        return finished;
    }

    /**
     * Print the JVM-detected deadlock (if any). Works for ReentrantReadWriteLock as well.
     */
    private static void reportDeadlocks() {
        ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        long[] ids = mx.findDeadlockedThreads();
        if (ids == null) {
            System.err.println("  no JVM-detected deadlock (could be starvation, livelock or just too slow)");
            return;
        }
        for (ThreadInfo info : mx.getThreadInfo(ids, Integer.MAX_VALUE)) {
            System.err.println("  deadlocked thread: " + info);
        }
    }

    /**
     * Count of root's children on the history tree, or -1 if the history could not be read.
     */
    private static int rootChildCount(ChessGame game) {
        try {
            var root = game.getRootNode();
            return root.children().size();
        } catch (RuntimeException e) {
            System.err.println("  could not read history tree: " + e);
            return -1;
        }
    }

    private static boolean verify(String label, boolean condition) {
        String line = "  " + (condition ? "[PASS] " : "[FAIL] ") + label;
        if (condition) System.out.println(line);
        else System.err.println(line);
        return condition;
    }

    /**
     * Listener that only counts events.
     */
    private static final class CountingListener implements ChessGameListener {
        final AtomicInteger made = new AtomicInteger();
        final AtomicInteger unmade = new AtomicInteger();
        final AtomicInteger remade = new AtomicInteger();

        @Override
        public void onMoveMade(ChessGame source, MoveInfo moveInfo) {
            made.incrementAndGet();
        }

        @Override
        public void onMoveUnmade(ChessGame source, MoveInfo unmadeMoveInfo) {
            unmade.incrementAndGet();
        }

        @Override
        public void onMoveRemade(ChessGame source, MoveInfo remadeMoveInfo) {
            remade.incrementAndGet();
        }
    }

    /**
     * Thread-safe failure collector. Only the first failure is kept (and its stack trace printed),
     * so a broken run doesn't flood the console with thousands of identical errors.
     */
    private static final class Failures {
        private final AtomicInteger count = new AtomicInteger();
        private final AtomicReference<String> first = new AtomicReference<>();

        void report(String message) {
            count.incrementAndGet();
            first.compareAndSet(null, message);
        }

        void report(String where, Throwable t) {
            count.incrementAndGet();
            if (first.compareAndSet(null, where + ": " + t)) {
                t.printStackTrace();
            }
        }

        int count() {
            return count.get();
        }

        String describe() {
            return count.get() == 0 ? "" : " (total " + count.get() + ", first: " + first.get() + ")";
        }
    }
}
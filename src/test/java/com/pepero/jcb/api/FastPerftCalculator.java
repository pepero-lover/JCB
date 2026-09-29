package com.pepero.jcb.api;

import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.MoveGenerator;
import com.pepero.jcb.core.constant.MoveCache;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;

public class FastPerftCalculator {

    // ---- Transposition table
    public static class PerftTable {
        private final AtomicLongArray keys;
        private final AtomicLongArray data;
        private final long mask;

        public PerftTable(long sizeMb) {
            long bytesPerEntry = 16;
            long rawCount = (sizeMb * 1024 * 1024) / bytesPerEntry;
            long count = Long.highestOneBit(rawCount);
            this.keys = new AtomicLongArray((int) count);
            this.data = new AtomicLongArray((int) count);
            this.mask = count - 1;
        }

        public long probe(long key, int depth) {
            int idx = (int) (key & mask);
            long d = data.get(idx);
            long k = keys.get(idx);
            if ((k ^ d) == key && (d & 0xFF) == depth) {
                return d >>> 8;
            }
            return -1L;
        }

        public void store(long key, int depth, long nodes) {
            int idx = (int) (key & mask);
            long d = (nodes << 8) | (depth & 0xFFL);
            data.set(idx, d);
            keys.set(idx, key ^ d);
        }
    }

    private static int[][] newMoveBuffers(int maxDepth) {
        int[][] buffers = new int[maxDepth + 1][];
        for (int i = 0; i <= maxDepth; i++) buffers[i] = new int[MoveCache.MAX_MOVE_SIZE];
        return buffers;
    }

    public static long perft(Chessboard board, int depth, int[][] moveBuffers) {
        if (depth == 0) return 1L;

        int[] moves = moveBuffers[depth];
        int count = MoveGenerator.generateMoves(board, moves);

        if (depth == 1) return count;

        long nodes = 0;
        for (int i = 0; i < count; i++) {
            int move = moves[i];
            MoveGenerator.makeMove(board, move);
            nodes += perft(board, depth - 1, moveBuffers);
            MoveGenerator.unmakeMove(board, move);
        }
        return nodes;
    }

    public static long perftHash(Chessboard board, int depth, PerftTable table, int[][] moveBuffers) {
        if (depth == 0) return 1L;

        int[] moves = moveBuffers[depth];
        int count = MoveGenerator.generateMoves(board, moves);

        if (depth == 1) return count;

        long cached = table.probe(board.hash_key, depth);
        if (cached >= 0) return cached;

        long nodes = 0;
        for (int i = 0; i < count; i++) {
            int move = moves[i];
            MoveGenerator.makeMove(board, move);
            nodes += perftHash(board, depth - 1, table, moveBuffers);
            MoveGenerator.unmakeMove(board, move);
        }

        table.store(board.hash_key, depth, nodes);
        return nodes;
    }

    private record Task(int m1, int m2) {}

    public static long perftMt(Chessboard root, int depth, PerftTable table, int nthreads)
            throws InterruptedException, ExecutionException {

        List<Task> tasks = new ArrayList<>();
        int[] buf1 = new int[MoveCache.MAX_MOVE_SIZE];
        int c1 = MoveGenerator.generateMoves(root, buf1);

        for (int i = 0; i < c1; i++) {
            int m1 = buf1[i];
            MoveGenerator.makeMove(root, m1);

            int[] buf2 = new int[MoveCache.MAX_MOVE_SIZE];
            int c2 = MoveGenerator.generateMoves(root, buf2);
            for (int j = 0; j < c2; j++) tasks.add(new Task(m1, buf2[j]));

            MoveGenerator.unmakeMove(root, m1);
        }

        final int taskCount = tasks.size();
        AtomicInteger next = new AtomicInteger(0);
        AtomicInteger completed = new AtomicInteger(0);
        AtomicBoolean done = new AtomicBoolean(false);
        ExecutorService pool = Executors.newFixedThreadPool(nthreads);
        List<Future<Long>> futures = new ArrayList<>();

        long startNanos = System.nanoTime();

        Thread monitor = new Thread(() -> {
            try {
                while (!done.get()) {
                    Thread.sleep(1000);
                    int c = completed.get();
                    double pct = 100.0 * c / taskCount;
                    double elapsed = (System.nanoTime() - startNanos) / 1e9;
                    double eta = (c > 0) ? elapsed * ((double) taskCount / c - 1.0) : 0.0;
                    System.out.printf("\r%5.1f%% (%d/%d tasks)  elapsed %6.1fs  eta %6.1fs   ",
                            pct, c, taskCount, elapsed, eta);
                    System.out.flush();
                }
            } catch (InterruptedException ignored) {
            }
        });
        monitor.start();

        for (int t = 0; t < nthreads; t++) {
            futures.add(pool.submit(() -> {
                Chessboard b = new Chessboard(root);
                int[][] buffers = newMoveBuffers(depth);
                long local = 0;
                int i;
                while ((i = next.getAndIncrement()) < taskCount) {
                    Task task = tasks.get(i);
                    MoveGenerator.makeMove(b, task.m1());
                    MoveGenerator.makeMove(b, task.m2());
                    local += perftHash(b, depth - 2, table, buffers);
                    MoveGenerator.unmakeMove(b, task.m2());
                    MoveGenerator.unmakeMove(b, task.m1());
                    completed.incrementAndGet();
                }
                return local;
            }));
        }

        long total = 0;
        for (Future<Long> f : futures) total += f.get();
        pool.shutdown();

        done.set(true);
        monitor.interrupt();
        monitor.join();

        System.out.printf("\r%5.1f%% (%d/%d tasks) done.                                  %n",
                100.0, taskCount, taskCount);

        return total;
    }

    public static void main(String[] args) throws Exception {
        Chessboard board = new Chessboard(Chessboard.start_position);
        //Chessboard board = new Chessboard("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1");

        int depth = 10;
        PerftTable table = new PerftTable(16384);

        long start = System.nanoTime();
        long nodes = perftMt(board, depth, table, Runtime.getRuntime().availableProcessors());
        long elapsed = System.nanoTime() - start;

        double sec = elapsed / 1e9;
        System.out.printf("%d nodes, %.3f s, %.1f Mnps%n",
                nodes, sec, nodes / sec / 1_000_000);
    }
}
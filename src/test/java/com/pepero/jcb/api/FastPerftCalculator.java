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

        if (depth >= 2) {
            long cached = table.probe(board.hash_key, depth);
            if (cached >= 0) return cached;
        }

        int[] moves = moveBuffers[depth];
        int count = MoveGenerator.generateMoves(board, moves);

        if (depth == 1) return count;

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

    private record Task(int[] moves) {}

    private static void collectTasks(Chessboard board, int splitPly, int[] path, int pathLen,
                                     int[][] buffers, List<Task> out) {
        if (pathLen == splitPly) {
            out.add(new Task(path.clone()));
            return;
        }

        int[] moves = buffers[pathLen];
        int count = MoveGenerator.generateMoves(board, moves);

        for (int i = 0; i < count; i++) {
            int move = moves[i];
            path[pathLen] = move;
            MoveGenerator.makeMove(board, move);
            collectTasks(board, splitPly, path, pathLen + 1, buffers, out);
            MoveGenerator.unmakeMove(board, move);
        }
    }

    public static List<Task> generateTasks(Chessboard root, int splitPly) {
        List<Task> tasks = new ArrayList<>();
        int[] path = new int[splitPly];
        int[][] buffers = newMoveBuffers(splitPly);
        collectTasks(root, splitPly, path, 0, buffers, tasks);
        return tasks;
    }

    public static long perftMt(Chessboard root, int depth, int splitPly, PerftTable table, int nthreads)
            throws InterruptedException, ExecutionException {

        if (splitPly >= depth) {
            throw new IllegalArgumentException("splitPly(" + splitPly + ")는 depth(" + depth + ")보다 작아야 합니다");
        }

        List<Task> tasks = generateTasks(root, splitPly);
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

        int remainingDepth = depth - splitPly;

        for (int t = 0; t < nthreads; t++) {
            futures.add(pool.submit(() -> {
                Chessboard b = new Chessboard(root);
                int[][] buffers = newMoveBuffers(depth);
                long local = 0;
                int i;
                while ((i = next.getAndIncrement()) < taskCount) {
                    int[] moves = tasks.get(i).moves();
                    for (int m : moves) MoveGenerator.makeMove(b, m);
                    local += perftHash(b, remainingDepth, table, buffers);
                    for (int j = moves.length - 1; j >= 0; j--) MoveGenerator.unmakeMove(b, moves[j]);
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

    public static void warmup(int nthreads) throws InterruptedException, ExecutionException {
        Chessboard warmBoard = new Chessboard(Chessboard.start_position);
        PerftTable warmTable = new PerftTable(64);
        for (int i = 0; i < 3; i++) {
            perftMt(warmBoard, 6, 2, warmTable, nthreads);
        }
    }

    public static void main(String[] args) throws Exception {
        int nthreads = Runtime.getRuntime().availableProcessors() - 2;

        System.out.println("maxMemory = " + Runtime.getRuntime().maxMemory() / (1L << 30) + " GB");

        System.out.println("warming up...");
        warmup(nthreads);
        System.out.println("warmup done.\n");

        Chessboard board = new Chessboard(Chessboard.start_position);
        //Chessboard board = new Chessboard("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1");

        int depth = 10;
        int splitPly = 3;
        PerftTable table = new PerftTable(16384);

        long start = System.nanoTime();
        long nodes = perftMt(board, depth, splitPly, table, nthreads);
        long elapsed = System.nanoTime() - start;

        double sec = elapsed / 1e9;
        System.out.printf("%d nodes, %.3f s, %.1f Mnps%n",
                nodes, sec, nodes / sec / 1_000_000);
    }
}
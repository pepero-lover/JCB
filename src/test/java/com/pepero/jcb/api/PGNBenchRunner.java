package com.pepero.jcb.api;

import com.sun.management.ThreadMXBean;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

/**
 * Tiny dependency-free throughput benchmark harness (test scope).
 * <p>
 * One "pass" runs {@code task} over every input once. A benchmark = warmup passes (results discarded, lets the JIT
 * settle) followed by measured passes. Wall time and bytes allocated by the calling thread are recorded per pass.
 * <p>
 * Inputs come from a {@link Supplier} that is invoked <b>outside</b> the timed region before every pass, so a
 * benchmark can hand out fresh objects each round (needed when the code under test caches results on the input,
 * e.g. {@code MoveNode.cachedSan}).
 */
final class PGNBenchRunner {

    private static final ThreadMXBean THREADS = (ThreadMXBean) ManagementFactory.getThreadMXBean();

    /** Every task result is folded in here so the JIT can never prove the benchmarked work is unused. */
    static volatile long sink;

    record Result(String name, double medianMs, double minMs, long medianAllocBytes, long checksum) {}

    static <T> Result run(String name, Supplier<T[]> inputs, ToLongFunction<T> task, int warmupPasses, int passes) {
        long tid = Thread.currentThread().threadId();
        long checksum = 0;

        for (int i = 0; i < warmupPasses; i++) {
            checksum = pass(inputs.get(), task);
            sink += checksum;
        }

        double[] ms = new double[passes];
        long[] alloc = new long[passes];

        for (int i = 0; i < passes; i++) {
            T[] in = inputs.get();
            System.gc(); // every measured pass starts from a settled heap

            long a0 = THREADS.getThreadAllocatedBytes(tid);
            long t0 = System.nanoTime();
            checksum = pass(in, task);
            long t1 = System.nanoTime();
            long a1 = THREADS.getThreadAllocatedBytes(tid);

            sink += checksum;
            ms[i] = (t1 - t0) / 1e6;
            alloc[i] = a1 - a0;
        }

        double[] sortedMs = ms.clone();
        Arrays.sort(sortedMs);
        long[] sortedAlloc = alloc.clone();
        Arrays.sort(sortedAlloc);

        return new Result(name, sortedMs[passes / 2], sortedMs[0], sortedAlloc[passes / 2], checksum);
    }

    private static <T> long pass(T[] inputs, ToLongFunction<T> task) {
        long sum = 0;
        for (T in : inputs) sum += task.applyAsLong(in);
        return sum;
    }
}
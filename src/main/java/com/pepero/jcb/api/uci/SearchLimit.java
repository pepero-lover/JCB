package com.pepero.jcb.api.uci;

/**
 * Search limits for a UCI "go" command, plus the MultiPV count to use. <p>
 *
 * Every numeric limit follows the same rule: a value <b>&lt;= 0 means "not set"</b>
 * (negative values are normalized to 0). Limits can be combined, the engine stops
 * at whichever it reaches first. <p>
 *
 * <pre>{@code
 * SearchLimits.depth(18).withMultiPv(3);
 * SearchLimits.moveTime(1000);
 * SearchLimits.clock(wtimeMs, btimeMs, wincMs, bincMs);
 * SearchLimits.infinite();                       // live analysis, call stopAnalysis() to end
 * }</pre>
 *
 * @param depth      search depth limit
 * @param moveTimeMs fixed thinking time in ms ("movetime")
 * @param nodes      node count limit ("nodes")
 * @param wtimeMs    white's remaining time in ms
 * @param btimeMs    black's remaining time in ms
 * @param wincMs     white's increment per move in ms
 * @param bincMs     black's increment per move in ms
 * @param multiPv    MultiPV count (must be at least 1)
 */
public record SearchLimit(
        int depth,
        long moveTimeMs,
        long nodes,
        long wtimeMs,
        long btimeMs,
        long wincMs,
        long bincMs,
        int multiPv
) {

    public SearchLimit {
        if (multiPv < 1) {
            throw new IllegalArgumentException("multiPv must be at least 1! (given: " + multiPv + ")");
        }
        depth = Math.max(0, depth);
        moveTimeMs = Math.max(0, moveTimeMs);
        nodes = Math.max(0, nodes);
        wtimeMs = Math.max(0, wtimeMs);
        btimeMs = Math.max(0, btimeMs);
        wincMs = Math.max(0, wincMs);
        bincMs = Math.max(0, bincMs);
    }

    /**
     * No limit at all ("go infinite").
     */
    public static SearchLimit infinite() {
        return new SearchLimit(0, 0, 0, 0, 0, 0, 0, 1);
    }

    /**
     * Search until the given depth. A depth &lt;= 0 means no limit (infinite).
     */
    public static SearchLimit depth(int depth) {
        return infinite().withDepth(depth);
    }

    /**
     * Think for a fixed amount of time (ms).
     */
    public static SearchLimit moveTime(long moveTimeMs) {
        return infinite().withMoveTime(moveTimeMs);
    }

    /**
     * Search until the given node count.
     */
    public static SearchLimit nodes(long nodes) {
        return infinite().withNodes(nodes);
    }

    /**
     * Search with game clock information (ms). Normally both sides' time should be given.
     * Increments are optional (0 = none).
     */
    public static SearchLimit clock(long wtimeMs, long btimeMs, long wincMs, long bincMs) {
        return infinite().withClock(wtimeMs, btimeMs, wincMs, bincMs);
    }

    public SearchLimit withDepth(int depth) {
        return new SearchLimit(depth, moveTimeMs, nodes, wtimeMs, btimeMs, wincMs, bincMs, multiPv);
    }

    public SearchLimit withMoveTime(long moveTimeMs) {
        return new SearchLimit(depth, moveTimeMs, nodes, wtimeMs, btimeMs, wincMs, bincMs, multiPv);
    }

    public SearchLimit withNodes(long nodes) {
        return new SearchLimit(depth, moveTimeMs, nodes, wtimeMs, btimeMs, wincMs, bincMs, multiPv);
    }

    public SearchLimit withClock(long wtimeMs, long btimeMs, long wincMs, long bincMs) {
        return new SearchLimit(depth, moveTimeMs, nodes, wtimeMs, btimeMs, wincMs, bincMs, multiPv);
    }

    public SearchLimit withMultiPv(int multiPv) {
        return new SearchLimit(depth, moveTimeMs, nodes, wtimeMs, btimeMs, wincMs, bincMs, multiPv);
    }

    /**
     * Whether this search has no stopping condition (so it only ends by "stop").
     * Increments alone are not a stopping condition.
     */
    public boolean isInfinite() {
        return depth <= 0 && moveTimeMs <= 0 && nodes <= 0 && wtimeMs <= 0 && btimeMs <= 0;
    }

    /**
     * Build the UCI "go" command for these limits. Only limits that are set are sent,
     * so a missing value is never sent as a negative number.
     */
    String toGoCommand() {
        if (isInfinite()) {
            return "go infinite";
        }

        StringBuilder goCmd = new StringBuilder("go");
        if (depth > 0) goCmd.append(" depth ").append(depth);
        if (moveTimeMs > 0) goCmd.append(" movetime ").append(moveTimeMs);
        if (nodes > 0) goCmd.append(" nodes ").append(nodes);
        if (wtimeMs > 0) goCmd.append(" wtime ").append(wtimeMs);
        if (btimeMs > 0) goCmd.append(" btime ").append(btimeMs);
        if (wincMs > 0) goCmd.append(" winc ").append(wincMs);
        if (bincMs > 0) goCmd.append(" binc ").append(bincMs);
        return goCmd.toString();
    }
}
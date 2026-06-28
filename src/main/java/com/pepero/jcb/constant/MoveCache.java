package com.pepero.jcb.constant;

import com.pepero.jcb.core.Chessboard;

public class MoveCache {
    public static final int MAX_MOVE_SIZE = 512;

    public static final ThreadLocal<int[][]> CHESSGAME_MOVE_CACHE =
            ThreadLocal.withInitial(() -> new int[1024][MAX_MOVE_SIZE]);

    public static final ThreadLocal<int[]> MOVE_GENERATOR_CACHE =
            ThreadLocal.withInitial(() -> new int[MAX_MOVE_SIZE]);

    public static final ThreadLocal<int[]> CHESSBOARD_UTIL_CACHE =
            ThreadLocal.withInitial(() -> new int[MAX_MOVE_SIZE]);

    public static final ThreadLocal<int[]> CONVERT_MOVE_CACHE =
            ThreadLocal.withInitial(() -> new int[MAX_MOVE_SIZE]);


    /**
     * For searching multi-thread
     */
    public static final ThreadLocal<int[][]> SEARCH_MOVE_CACHE =
            ThreadLocal.withInitial(() -> new int[Chessboard.MAX_DEPTH][MAX_MOVE_SIZE]);

    /**
     * For searching SINGLE-THREAD
     */
    public static final int[][] SEARCH_MOVE_SINGLE = new int[Chessboard.MAX_DEPTH][MAX_MOVE_SIZE];
}

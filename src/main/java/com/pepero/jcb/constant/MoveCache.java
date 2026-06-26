package com.pepero.jcb.constant;

public class MoveCache {
    public static final ThreadLocal<int[][]> CHESSGAME_MOVE_CACHE =
            ThreadLocal.withInitial(() -> new int[1024][255]);

    public static final ThreadLocal<int[]> MOVE_GENERATOR_CACHE =
            ThreadLocal.withInitial(() -> new int[255]);

    public static final ThreadLocal<int[]> CONVERT_MOVE_CACHE =
            ThreadLocal.withInitial(() -> new int[255]);
}

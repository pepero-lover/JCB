package com.pepero.jcb.core.constant;

import java.util.Arrays;

import static com.pepero.jcb.core.constant.BoardSquares.*;

/**
 * define castling rights
 */
public class CastlingRights {
    /*
    If you want to know what these numbers are doing, go to the Chessboard.java class and check the
    variable named castle
    */

    // castling bits binary representation

    public static int WK = 1; // 0001
    public static int WQ = 2; // 0010
    public static int BK = 4; // 0100
    public static int BQ = 8; // 1000

    public static final int[] UPDATE_MASK = new int[64];

    static {
        Arrays.fill(UPDATE_MASK, 15);

        // for standard chess castling
        UPDATE_MASK[e1] = ~(WK | WQ);
        UPDATE_MASK[h1] = ~WK;
        UPDATE_MASK[a1] = ~WQ;
        UPDATE_MASK[e8] = ~(BK | BQ);
        UPDATE_MASK[h8] = ~BK;
        UPDATE_MASK[a8] = ~BQ;
    }
}

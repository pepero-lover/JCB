package com.pepero.jcb.api.syzygy;

import java.util.Arrays;

/**
 * Holds the piece-order info for a single sub-table (one file-class, e.g. pawn on a/b/c/d file).
 * Each sub-table stores this info twice: once for white-to-move (wtm), once for black-to-move (btm).
 */
public class SyzygySubTable {
    // encoding order value
    private final int order;

    // piece type codes for this sub-table, in board order
    // (1~6 = white P,N,B,R,Q,K / 9~14 = black p,n,b,r,q,k)
    private final int[] wtmPieces;
    private final int[] btmPieces;

    public SyzygySubTable(int order, int[] wtmPieces, int[] btmPieces) {
        this.order = order;
        this.wtmPieces = wtmPieces;
        this.btmPieces = btmPieces;
    }

    public int getOrder() {
        return order;
    }

    public int[] getWtmPieces() {
        return wtmPieces;
    }

    public int[] getBtmPieces() {
        return btmPieces;
    }

    @Override
    public String toString() {
        return "SyzygySubTable{order=" + order
                + ", wtmPieces=" + Arrays.toString(wtmPieces)
                + ", btmPieces=" + Arrays.toString(btmPieces) + "}";
    }
}

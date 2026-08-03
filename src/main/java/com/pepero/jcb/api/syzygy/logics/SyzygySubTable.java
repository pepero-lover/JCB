package com.pepero.jcb.api.syzygy.logics;

import java.util.Arrays;

/**
 * Holds the piece-order info for a single sub-table (one file-class, e.g. pawn on a/b/c/d file).
 * Each sub-table stores this info twice: once for white-to-move (wtm), once for black-to-move (btm).
 *
 * @param orderWtm  encoding order value
 * @param wtmPieces piece type codes for this sub-table, in board order (1~6 = white P,N,B,R,Q,K / 9~14 = black p,n,b,r,q,k)
 */
public record SyzygySubTable(int orderWtm, int orderBtm, int order2Wtm, int order2Btm, int[] wtmPieces,
                             int[] btmPieces) {

    @Override
    public String toString() {
        return "SyzygySubTable{" +
                "orderWtm=" + orderWtm +
                ", orderBtm=" + orderBtm +
                ", order2Wtm=" + order2Wtm +
                ", order2Btm=" + order2Btm +
                ", wtmPieces=" + Arrays.toString(wtmPieces) +
                ", btmPieces=" + Arrays.toString(btmPieces) +
                '}';
    }
}

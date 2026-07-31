package com.pepero.jcb.api.syzygy.logics;

import com.pepero.jcb.bitboard.BitBoardUtils;
import com.pepero.jcb.core.Chessboard;

import java.util.HashMap;
import java.util.Map;

import static com.pepero.jcb.constant.EncodedPieces.*;

/**
 * Ported from Fathom's fill_squares(). Given an actual board position and the
 * expected piece-type order for a sub-table/side (from SyzygySubTable), builds
 * the square array that SyzygyEncoder.encode() needs.
 * <p>
 * NOTE: this does NOT yet handle the "no split, reuse via color-flip" case
 * (symmetric materials that only store one side's data and need the position
 * mentally color-flipped to probe the other side to move). That's a separate,
 * later refinement — for now this assumes split data is used directly (true
 * for every case we've tested so far, e.g. KPvK, KRvK).
 */
public class SyzygyFillSquares {

    /**
     * Build the square array for encode(), matching subTable's declared piece order.
     *
     * @param chessboard current board position
     * @param subTable   the sub-table whose piece order we must follow
     * @param isWtm      true to use the white-to-move piece order, false for black-to-move
     * @return int[] of board squares (0~63), one per piece, in subTable's order
     */
    public static int[] fillSquares(Chessboard chessboard, SyzygySubTable subTable, boolean isWtm) {
        int[] pieceCodes = isWtm ? subTable.wtmPieces() : subTable.btmPieces();
        int n = pieceCodes.length;
        int[] squares = new int[n];

        // Multiple pieces can share the same type code (e.g. two rooks), and they
        // all come from the same bitboard — pop squares off one at a time as we
        // consume them, so the same square is never reused twice.
        Map<Integer, Long> remainingBitboards = new HashMap<>();

        for (int i = 0; i < n; i++) {
            int code = pieceCodes[i];

            long bb = remainingBitboards.computeIfAbsent(code,
                    c -> chessboard.getBitboardPiece(syzygyCodeToBitboardIndex(c)));

            if (bb == 0L) {
                throw new IllegalStateException(
                        "Board doesn't have enough pieces of Syzygy piece code " + code
                                + " to match this sub-table's expected material");
            }

            int square = BitBoardUtils.getLS1BIndex(bb);
            squares[i] = square;

            // consume that square so the next same-type piece (if any) gets a different one.
            // Which specific square goes to which "slot" doesn't matter here — encode()
            // re-sorts same-type groups internally anyway.
            bb = BitBoardUtils.popBit(bb, square);
            remainingBitboards.put(code, bb);
        }

        return squares;
    }

    /**
     * Maps a Syzygy piece-type code (1~6 = white P,N,B,R,Q,K / 9~14 = black p,n,b,r,q,k,
     * per piece_to_char = " PNBRQK  pnbrqk") to the matching index into Chessboard.bitboards.
     */
    private static int syzygyCodeToBitboardIndex(int code) {
        switch (code) {
            case 1: return P;
            case 2: return N;
            case 3: return B;
            case 4: return R;
            case 5: return Q;
            case 6: return K;
            case 9: return p;
            case 10: return n;
            case 11: return b;
            case 12: return r;
            case 13: return q;
            case 14: return k;
            default:
                throw new IllegalArgumentException("Invalid Syzygy piece code: " + code);
        }
    }
}
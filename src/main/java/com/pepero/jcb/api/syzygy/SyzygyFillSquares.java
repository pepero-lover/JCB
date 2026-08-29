package com.pepero.jcb.api.syzygy;

import com.pepero.jcb.core.bitboard.BitBoardUtils;
import com.pepero.jcb.core.Chessboard;

import java.util.HashMap;
import java.util.Map;

import static com.pepero.jcb.core.constant.EncodedPieces.*;

/**
 * Ported from Fathom's fill_squares(). Given an actual board position and the
 * expected piece-type order for a sub-table/side (from SyzygySubTable), builds
 * the square array that SyzygyEncoder.encode() needs.
 * <p>
 * IMPORTANT: "colorFlipped" (Fathom/python-chess's "cmirror") and the actual
 * SQUARE mirror (python-chess's "mirror", i.e. {@code ^ 0x38}) are two
 * independent things:
 *   - colorFlipped always flips which side's bitboard we pull pieces from
 *     (swap white/black piece codes) whenever the natural material string
 *     wasn't the one actually stored on disk.
 *   - the square itself must ONLY be flipped when the material HAS PAWNS.
 *     For pawnless material, SyzygyEncoder's own symmetry normalization
 *     (TRIANGLE/OFF_DIAG/FLIP_DIAG etc.) already handles full board symmetry
 *     internally, so pre-flipping squares here would corrupt the encoded index.
 *     See python-chess's _probe_wdl_table / _probe_dtz_table: in the
 *     has_pawns==False branch it's "p[i] = square" (no ^ mirror); the
 *     "p[i] = square ^ mirror" line only exists in the has_pawns==True branch.
 */
class SyzygyFillSquares {

    /**
     * Build the square array for encode(), matching subTable's declared piece order.
     * No color flip, so hasPawns is irrelevant here.
     *
     * @param chessboard current board position
     * @param subTable   the sub-table whose piece order we must follow
     * @param isWtm      true to use the white-to-move piece order, false for black-to-move
     * @return int[] of board squares (0~63), one per piece, in subTable's order
     */
    public static int[] fillSquares(Chessboard chessboard, SyzygySubTable subTable, boolean isWtm) {
        return fillSquares(chessboard, subTable, isWtm, false, false);
    }

    /**
     * @deprecated square-mirroring must depend on whether the material has pawns
     * (see class javadoc). This overload preserves the OLD (always-mirror-when-
     * colorFlipped) behavior for callers not yet updated to pass hasPawns
     * explicitly — it is WRONG for pawnless material and should be migrated.
     */
    @Deprecated
    public static int[] fillSquares(Chessboard chessboard, SyzygySubTable subTable,
                                    boolean isWtm, boolean colorFlipped) {
        return fillSquares(chessboard, subTable, isWtm, colorFlipped, true);
    }

    /**
     * Build the square array for encode(), matching subTable's declared piece order.
     *
     * @param chessboard   current board position
     * @param subTable     the sub-table whose piece order we must follow
     * @param isWtm        true to use the white-to-move piece order, false for black-to-move
     * @param colorFlipped whether we're reading from the color-swapped bitboards
     *                     (matches Fathom/python-chess's "cmirror")
     * @param hasPawns     whether this material has pawns; ONLY when true does
     *                     colorFlipped also trigger the actual square ({@code ^ 0x38})
     *                     mirror (matches python-chess's "mirror"). Pawnless
     *                     material must never have its squares mirrored here.
     * @return int[] of board squares (0~63), one per piece, in subTable's order
     */
    public static int[] fillSquares(Chessboard chessboard, SyzygySubTable subTable,
                                    boolean isWtm, boolean colorFlipped, boolean hasPawns) {
        int[] pieceCodes = isWtm ? subTable.wtmPieces() : subTable.btmPieces();
        int n = pieceCodes.length;
        int[] squares = new int[n];

        boolean mirrorSquares = colorFlipped && hasPawns;

        Map<Integer, Long> remainingBitboards = new HashMap<>();

        for (int i = 0; i < n; i++) {
            int fileCode = pieceCodes[i];
            int actualCode = colorFlipped ? flipColor(fileCode) : fileCode;

            long bb = remainingBitboards.computeIfAbsent(actualCode,
                    c -> chessboard.bitboards[syzygyCodeToBitboardIndex(c)]);

            if (bb == 0L) {
                throw new IllegalStateException(
                        "Board doesn't have enough pieces of Syzygy piece code " + actualCode
                                + " (file code " + fileCode + ", colorFlipped=" + colorFlipped
                                + ") to match this sub-table's expected material");
            }

            int square = BitBoardUtils.getLS1BIndex(bb);
            squares[i] = mirrorSquares ? (square ^ 0x38) : square;

            bb = BitBoardUtils.popBit(bb, square);
            remainingBitboards.put(actualCode, bb);
        }

        return squares;
    }

    /**
     * @deprecated see the other deprecated overload — this preserves old behavior
     * (hasPawns=true) for callers not yet migrated.
     */
    @Deprecated
    public static int[] fillSquares(Chessboard chessboard, SyzygySubTable subTable,
                                    boolean isWtm, boolean colorFlipped, int anchorSquare) {
        return fillSquares(chessboard, subTable, isWtm, colorFlipped, true, anchorSquare);
    }

    public static int[] fillSquares(Chessboard chessboard, SyzygySubTable subTable,
                                    boolean isWtm, boolean colorFlipped, boolean hasPawns, int anchorSquare) {
        int[] squares = fillSquares(chessboard, subTable, isWtm, colorFlipped, hasPawns);

        if (anchorSquare >= 0) {
            for (int i = 0; i < squares.length; i++) {
                if (squares[i] == anchorSquare) {
                    if (i != 0) {
                        int tmp = squares[0];
                        squares[0] = squares[i];
                        squares[i] = tmp;
                    }
                    break;
                }
            }
        }

        return squares;
    }

    private static int flipColor(int code) {
        if (code == 0) return 0;
        return (code <= 6) ? code + 8 : code - 8;
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
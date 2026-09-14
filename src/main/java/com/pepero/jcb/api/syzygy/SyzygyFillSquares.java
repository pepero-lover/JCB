package com.pepero.jcb.api.syzygy;

import com.pepero.jcb.core.bitboard.BitBoardUtils;
import com.pepero.jcb.core.Chessboard;

import java.util.HashMap;
import java.util.Map;

import static com.pepero.jcb.core.constant.EncodedPieces.*;

/**
 * Ported from Fathom's {@code tbprobe.c} — the piece/square-gathering loop shared
 * by {@code probe_wdl_table()} and {@code probe_dtz_table()} (the loop that fills
 * the local {@code p[]} array right before {@code encode_piece()}/{@code encode_pawn()}
 * is called), not a separate named helper in the original C.
 * <p>
 * IMPORTANT: "colorFlipped" (Fathom's {@code cmirror}) and the actual SQUARE
 * mirror (Fathom's {@code mirror}, i.e. {@code ^ 0x38}) are two independent things,
 * both derived once per probe from whether the position's own material key
 * matches the table's stored key ({@code key != ptr->key} in Fathom):
 *   - colorFlipped ({@code cmirror}, 0 or 8) always flips which side's bitboard
 *     we pull pieces from (swap white/black piece codes) whenever the position's
 *     natural material string wasn't the one actually stored on disk.
 *   - the square itself ({@code mirror}, 0 or 0x38) must ONLY be XORed in when the
 *     material HAS PAWNS. For pawnless material, SyzygyEncoder's own symmetry
 *     normalization (TRIANGLE/OFF_DIAG/FLIP_DIAG etc.) already handles full board
 *     symmetry internally, so pre-flipping squares here would corrupt the encoded index.
 *     In Fathom's {@code !ptr->has_pawns} branch the fill is "{@code p[i++] = lsb(bb);}"
 *     (no {@code ^ mirror} at all); the "{@code p[i++] = lsb(bb) ^ mirror;}" line only
 *     exists in the {@code has_pawns} branch. {@code mirror} is computed either way,
 *     but the pawnless branch simply never reads it.
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
     *                     (matches Fathom's {@code cmirror})
     * @param hasPawns     whether this material has pawns; ONLY when true does
     *                     colorFlipped also trigger the actual square ({@code ^ 0x38})
     *                     mirror (matches Fathom's {@code mirror}). Pawnless
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
package com.pepero.jcb.api.book;

import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.encode.EncodeMove;

import static com.pepero.jcb.core.constant.EncodedPieces.*;

public class PolyglotMoveEncoder {

    /**
     * Encode JCB encoded move data into polyglot 16-bit move format.
     *
     * @param moveData JCB encoded move data encoded move (from {@link EncodeMove#encodeMove(int, int, int, int, boolean, boolean, boolean, boolean)})
     * @param board    board state BEFORE the move is made (needed for castling rook square)
     * @return polyglot-encoded move (0 ~ 0xFFFF range)
     */
    public static int encode(int moveData, Chessboard board) {
        int source = EncodeMove.getMoveSource(moveData);
        int target = EncodeMove.getMoveTarget(moveData);
        int promoted = EncodeMove.getMovePromoted(moveData);

        if (EncodeMove.getMoveCastling(moveData)) {
            target = getCastlingRookSquare(source, target, board);
        }

        int polyPromo = toPolyglotPromotion(promoted);

        // source_file = (move >> 6) & 7, source_rank = (move >> 9) & 7
        // target_file = move & 7,       target_rank = (move >> 3) & 7
        // promotion   = (move >> 12) & 7
        int sourceFile = source & 7;
        int sourceRank = (source >> 3) & 7;
        int targetFile = target & 7;
        int targetRank = (target >> 3) & 7;

        return targetFile
                | (targetRank << 3)
                | (sourceFile << 6)
                | (sourceRank << 9)
                | (polyPromo << 12);
    }

    /**
     * Convert JCB encoded move data promoted piece code to polyglot promotion code.
     * Polyglot spec: 0=none, 1=knight, 2=bishop, 3=rook, 4=queen (color-agnostic)
     */
    private static int toPolyglotPromotion(int promoted) {
        if (promoted == 0) return 0;

        return switch (promoted) {
            case N, n -> 1;
            case B, b -> 2;
            case R, r -> 3;
            case Q, q -> 4;
            default -> 0;
        };
    }

    /**
     * Get the rook's square for a castling move, per polyglot convention
     * (king "captures" its own rook to represent castling).
     *
     * @param source king's source square
     * @param target king's destination square
     * @param board  board state before the move
     */
    private static int getCastlingRookSquare(int source, int target, Chessboard board) {
        int sourceFile = source & 7;
        int targetFile = target & 7;
        int rank = (source >> 3) & 7;

        boolean isKingSide = targetFile > sourceFile;

        int rookFile;
        if (board.isChess960) {
            rookFile = isKingSide ? board.king_side_rook_file : board.queen_side_rook_file;
        } else {
            rookFile = isKingSide ? 7 : 0;
        }

        return rank * 8 + rookFile;
    }
}
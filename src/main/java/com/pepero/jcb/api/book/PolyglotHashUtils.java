package com.pepero.jcb.api.book;

import com.pepero.jcb.core.constant.BoardSquares;
import com.pepero.jcb.core.constant.CastlingRights;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.ChessboardUtils;

import static com.pepero.jcb.core.constant.SideToMove.*;
import static com.pepero.jcb.core.constant.EncodedPieces.*;

public class PolyglotHashUtils {
    private static int toPolyglotPiece(int enginePiece) {
        if (enginePiece == p) return 0;
        if (enginePiece == P) return 1;
        if (enginePiece == n) return 2;
        if (enginePiece == N) return 3;
        if (enginePiece == b) return 4;
        if (enginePiece == B) return 5;
        if (enginePiece == r) return 6;
        if (enginePiece == R) return 7;
        if (enginePiece == q) return 8;
        if (enginePiece == Q) return 9;
        if (enginePiece == k) return 10;
        if (enginePiece == K) return 11;
        return -1;
    }

    /**
     * Get polyglot hashed data
     *
     * @param board chessboard
     * @return polyglot hashed data
     */
    public static long getPolyglotHash(Chessboard board) {
        long hash = 0L;

        // piece
        for (int square = 0; square < 64; square++) {
            int enginePiece = ChessboardUtils.getPieceTypeOnSquare(board, square);
            int pieceType = toPolyglotPiece(enginePiece);

            if (pieceType != -1) {
                int index = (pieceType * 64) + square;
                hash ^= PolyglotConstant.POLYGLOT_RAND[index];
            }
        }

        // Castling
        if ((board.castle & CastlingRights.WK) != 0) hash ^= PolyglotConstant.POLYGLOT_RAND[768];
        if ((board.castle & CastlingRights.WQ) != 0) hash ^= PolyglotConstant.POLYGLOT_RAND[769];
        if ((board.castle & CastlingRights.BK) != 0) hash ^= PolyglotConstant.POLYGLOT_RAND[770];
        if ((board.castle & CastlingRights.BQ) != 0) hash ^= PolyglotConstant.POLYGLOT_RAND[771];

        // Enpassant hashing
        if (board.enpassant != -1 && board.enpassant != BoardSquares.no_sq) {
            int epFile = board.enpassant % 8;
            boolean canBeCaptured = false;

            if (board.side == white) {
                int pawnRank = 3;
                if (epFile > 0 && ChessboardUtils.getPieceTypeOnSquare(board, pawnRank * 8 + (epFile - 1)) == P)
                    canBeCaptured = true;
                if (epFile < 7 && ChessboardUtils.getPieceTypeOnSquare(board, pawnRank * 8 + (epFile + 1)) == P)
                    canBeCaptured = true;
            } else {
                int pawnRank = 4;
                if (epFile > 0 && ChessboardUtils.getPieceTypeOnSquare(board, pawnRank * 8 + (epFile - 1)) == p)
                    canBeCaptured = true;
                if (epFile < 7 && ChessboardUtils.getPieceTypeOnSquare(board, pawnRank * 8 + (epFile + 1)) == p)
                    canBeCaptured = true;
            }

            if (canBeCaptured) {
                hash ^= PolyglotConstant.POLYGLOT_RAND[772 + epFile];
            }
        }

        // if side is white
        if (board.side == white) {
            hash ^= PolyglotConstant.POLYGLOT_RAND[780];
        }

        return hash;
    }

    /**
     * Decode Polyglot 16bit number move data to lan (or uci) string
     *
     * @param polyglotMove encoded polyglot move
     * @return decoded lan (or uci) move data
     */
    public static String decodePolyglotMoveLan(int polyglotMove) {
        int source_file = (polyglotMove >> 6) & 7;
        int source_rank = (polyglotMove >> 9) & 7;
        int target_file = (polyglotMove) & 7;
        int target_rank = (polyglotMove >> 3) & 7;
        int promotion = (polyglotMove >> 12) & 7;

        char source_file_char = (char) ('a' + source_file);
        char source_rank_char = (char) ('1' + source_rank);
        char target_file_char = (char) ('a' + target_file);
        char target_rank_char = (char) ('1' + target_rank);

        StringBuilder sb = new StringBuilder();
        sb.append(source_file_char).append(source_rank_char)
                .append(target_file_char).append(target_rank_char);

        if (promotion != 0) {
            switch (promotion) {
                case 1 -> sb.append('n');
                case 2 -> sb.append('b');
                case 3 -> sb.append('r');
                case 4 -> sb.append('q');
            }
        }

        return sb.toString();
    }
}

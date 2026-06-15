package com.pepero.jcb.api.parse;

import com.pepero.jcb.api.exception.FENConvertException;
import com.pepero.jcb.bitboard.BitBoardUtils;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.MoveGenerator;

import static com.pepero.jcb.constant.EncodedPieces.*;
import static com.pepero.jcb.constant.SideToMove.*;

public class FENValidator {
    /**
     * Get fen string exception
     *
     * @param fen fen
     *
     * @throws FENConvertException - if this fen string is illegal
     */
    public static void validateString(String fen) {
        if (fen == null || fen.trim().isEmpty())
            throw new FENConvertException("Invalid FEN: FEN string cannot be null or empty!");

        String[] parts = fen.trim().split("\\s+");
        if (parts.length != 6) {
            throw new FENConvertException("Invalid FEN: FEN must contain exactly 6 parts separated by spaces.");
        }

        String board = parts[0];
        String turn = parts[1];
        String castling = parts[2];
        String enPassant = parts[3];
        String halfMoveClock = parts[4];
        String fullMoveNumber = parts[5];

        validateBoard(board);
        validateTurn(turn);
        validateCastling(castling);
        validateEnPassant(turn, enPassant);
        validateCounters(halfMoveClock, fullMoveNumber);
    }

    private static void validateBoard(String board) {
        String[] ranks = board.split("/");

        if (ranks.length != 8) {
            throw new FENConvertException("Invalid FEN: Board must have exactly 8 ranks (found " + ranks.length + ").");
        }

        int whiteKingCount = 0;
        int blackKingCount = 0;

        for (int i = 0; i < 8; i++) {
            String rank = ranks[i];
            int squareCount = 0;

            for (char c : rank.toCharArray()) {
                if (Character.isDigit(c)) {
                    int emptySquares = Character.getNumericValue(c);
                    if (emptySquares < 1 || emptySquares > 8) {
                        throw new FENConvertException("FEN: Number out of bounds in board representation.");
                    }
                    squareCount += emptySquares;
                } else if ("pPnNbBrRqQkK".indexOf(c) != -1) {
                    squareCount++;
                    if (c == 'K') whiteKingCount++;
                    if (c == 'k') blackKingCount++;

                    if ((c == 'p' || c == 'P') && (i == 0 || i == 7)) {
                        throw new FENConvertException("Invalid FEN: Pawns cannot exist on the 1st or 8th rank.");
                    }
                } else {
                    throw new FENConvertException("Invalid FEN: Unknown character '" + c + "' in board representation.");
                }
            }

            if (squareCount != 8) {
                int humanRank = 8 - i;
                throw new FENConvertException("Invalid FEN: Rank "
                        + humanRank + " does not have exactly 8 squares (calculated: " + squareCount + ").");
            }
        }

        if (whiteKingCount != 1 || blackKingCount != 1) {
            throw new FENConvertException("Invalid FEN: There must be exactly one white king and one black king.");
        }
    }

    private static void validateTurn(String turn) {
        if (!turn.equals("w") && !turn.equals("b")) {
            throw new FENConvertException("Invalid FEN: Turn must be either 'w' or 'b'.");
        }
    }

    private static void validateCastling(String castling) {
        if (!castling.matches("^(-|[KQkq]{1,4})$")) {
            throw new FENConvertException("Invalid FEN: Invalid castling rights string.");
        }
    }

    private static void validateEnPassant(String turn, String enPassant) {
        if (!enPassant.matches("^(-|[a-h][36])$")) {
            throw new FENConvertException("Invalid FEN: Invalid en passant target square.");
        }

        if (!enPassant.equals("-")) {
            if (turn.equals("w") && enPassant.charAt(1) != '6') {
                throw new FENConvertException("Invalid FEN: White to move, but en passant square is not on the 6th rank.");
            }
            if (turn.equals("b") && enPassant.charAt(1) != '3') {
                throw new FENConvertException("Invalid FEN: Black to move, but en passant square is not on the 3rd rank.");
            }
        }
    }

    private static void validateCounters(String halfMove, String fullMove) {
        try {
            int half = Integer.parseInt(halfMove);
            int full = Integer.parseInt(fullMove);

            if (half < 0) {
                throw new FENConvertException("Invalid FEN: Half-move clock cannot be negative.");
            }
            if (full < 1) {
                throw new FENConvertException("Invalid FEN: Full-move number must be 1 or greater.");
            }
        } catch (NumberFormatException e) {
            throw new FENConvertException("Invalid FEN: Half-move or Full-move counters must be valid integers.");
        }
    }

    public static void validateLogicalState(Chessboard chessboard) {
        int oppositeSide = chessboard.side ^ 1;

        int oppositeKingSquare = BitBoardUtils.getLS1BIndex(
                chessboard.getBitboardPiece(oppositeSide == white ? K : k)
        );

        if (MoveGenerator.isSquareAttacked(chessboard, oppositeKingSquare, chessboard.side)) {
            throw new FENConvertException("Invalid FEN: The side not to move is in check. (Impossible game state)");
        }
    }
}

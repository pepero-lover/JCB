package com.pepero.jcb.api.parse;

import com.pepero.jcb.api.exception.FENConvertException;
import com.pepero.jcb.api.exception.type.FENErrorType;
import com.pepero.jcb.bitboard.BitBoardUtils;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.GameVariant;
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
    public static void validateString(String fen, boolean isChess960, GameVariant variant) {
        if (fen == null || fen.trim().isEmpty())
            throw new FENConvertException("Invalid FEN: FEN string cannot be null or empty!",
                    FENErrorType.FEN_NULL);

        String[] parts = fen.trim().split("\\s+");
        if (parts.length < 4 || parts.length > 7) {
            throw new FENConvertException("Invalid FEN: FEN must contain between 4 and 7 parts.",
                    FENErrorType.FEN_TOKEN_SIZE);
        }

        String boardPart = parts[0];
        String board = boardPart;

        // crazy house
        if (boardPart.contains("[")) {
            int openIdx = boardPart.indexOf('[');
            int closeIdx = boardPart.indexOf(']');

            if (openIdx == -1 || closeIdx == -1 || closeIdx < openIdx || closeIdx != boardPart.length() - 1) {
                throw new FENConvertException(
                        "Invalid FEN: Unexpected Crazyhouse pocket format. Expected '[...]' at the end of the board string.",
                        FENErrorType.CRAZYHOUSE_POCKET);
            }

            String pocket = boardPart.substring(openIdx + 1, closeIdx);
            validatePocket(pocket);

            board = boardPart.substring(0, openIdx);
        }

        String turn = parts[1];
        String castling = parts[2];
        String enPassant = parts[3];

        validateBoard(board, variant);
        validateTurn(turn);
        validateCastling(castling, isChess960);
        validateEnPassant(turn, enPassant);

        // get 3 check index
        int checksTokenIndex = -1;
        for (int i = 4; i < parts.length; i++) {
            if (parts[i].matches("\\d+\\+\\d+")) {
                checksTokenIndex = i;
                break;
            }
        }

        if (checksTokenIndex != -1) {
            validateChecksToken(parts[checksTokenIndex]);
        }

        // if 3 check index found and it's lichess format, shift half move, full move index
        int halfMoveIndex = (checksTokenIndex == 4) ? 5 : 4;
        int fullMoveIndex = (checksTokenIndex == 4) ? 6 : 5;

        if (parts.length > halfMoveIndex) {
            String fullMove = parts.length > fullMoveIndex ? parts[fullMoveIndex] : "1";
            validateCounters(parts[halfMoveIndex], fullMove);
        }
    }

    private static void validateBoard(String board, GameVariant variant) {
        String[] ranks = board.split("/");

        if (ranks.length != 8) {
            throw new FENConvertException("Invalid FEN: Board must have exactly 8 ranks (found " + ranks.length + ").",
                    FENErrorType.FEN_TOKEN_SIZE,
                    String.valueOf(ranks.length));
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
                        throw new FENConvertException("FEN: Number out of bounds in board representation.",
                                FENErrorType.EMPTY_SQUARE_OUT_OF_BOUNDS);
                    }
                    squareCount += emptySquares;
                }
                else if (c == '~') {}
                else if ("pPnNbBrRqQkK".indexOf(c) != -1) {
                    squareCount++;
                    if (c == 'K') whiteKingCount++;
                    if (c == 'k') blackKingCount++;

                    if(variant != GameVariant.HORDE) {
                        if ((c == 'p' || c == 'P') && (i == 0 || i == 7)) {
                            throw new FENConvertException("Invalid FEN: Pawns cannot exist on the 1st or 8th rank.",
                                    FENErrorType.PAWN_EXIST_LAST_RANK);
                        }
                    } else {
                        if(c == 'p' && (i == 0 || i == 7)) {
                            throw new FENConvertException(
                                    "Invalid FEN: Black pawns cannot exist on the 1st or 8th rank on horde variant.",
                                    FENErrorType.PAWN_EXIST_LAST_RANK);
                        }
                        if(c == 'P' && i == 7) {
                            throw new FENConvertException(
                                    "Invalid FEN: White pawns cannot exist on the 8th rank on horde variant.",
                                    FENErrorType.PAWN_EXIST_LAST_RANK);
                        }
                    }
                } else {
                    throw new FENConvertException("Invalid FEN: Unknown character '" + c + "' in board representation.",
                            FENErrorType.UNKNOWN_PIECE_TYPE,
                            String.valueOf(c));
                }
            }

            if (squareCount != 8) {
                int humanRank = 8 - i;
                throw new FENConvertException("Invalid FEN: Rank "
                        + humanRank + " does not have exactly 8 squares (calculated: " + squareCount + ").",
                        FENErrorType.RANK_SQUARE_NOT_8,
                        String.valueOf(humanRank));
            }
        }

        if(variant == GameVariant.GIVEAWAY || variant == GameVariant.SUICIDE) {
            return;
        }
        if(variant != GameVariant.HORDE) {
            if (whiteKingCount != 1 || blackKingCount != 1) {
                throw new FENConvertException("Invalid FEN: There must be exactly one white king and one black king.",
                        FENErrorType.KING_COUNT);
            }
        } else {
            if(whiteKingCount != 0 && blackKingCount != 1) {
                throw new FENConvertException(
                        "Invalid FEN: There must be exactly zero white king and one black king.",
                        FENErrorType.KING_COUNT);
            }
        }
    }

    private static void validateTurn(String turn) {
        if (!turn.equals("w") && !turn.equals("b")) {
            throw new FENConvertException("Invalid FEN: Turn must be either 'w' or 'b'.", FENErrorType.TURN);
        }
    }
    private static void validateCastling(String castling, boolean isChess960) {
        String regex = isChess960 ? "^(-|[KQkqA-Ha-h]{1,4})$" : "^(-|[KQkq]{1,4})$";

        if (castling == null || !castling.matches(regex)) {
            throw new FENConvertException("Invalid FEN: Invalid castling rights string. (" + castling +")",
                    FENErrorType.CASTLING,
                    castling);
        }
    }

    private static void validateEnPassant(String turn, String enPassant) {
        if (!enPassant.matches("^(-|[a-h][36])$")) {
            throw new FENConvertException("Invalid FEN: Invalid enpassant target square. (" + enPassant + ")",
                    FENErrorType.ENPASSANT_SQUARE,
                    enPassant);
        }

        if (!enPassant.equals("-")) {
            if (turn.equals("w") && enPassant.charAt(1) != '6') {
                throw new FENConvertException(
                        "Invalid FEN: White to move, but en passant square is not on the 6th rank. (" + enPassant + ")",
                        FENErrorType.ENPASSANT_SQUARE,
                        enPassant
                );
            }
            if (turn.equals("b") && enPassant.charAt(1) != '3') {
                throw new FENConvertException(
                        "Invalid FEN: Black to move, but en passant square is not on the 3rd rank. (" + enPassant + ")",
                        FENErrorType.ENPASSANT_SQUARE,
                        enPassant
                );
            }
        }
    }

    private static void validateCounters(String halfMove, String fullMove) {
        try {
            int half = Integer.parseInt(halfMove);
            int full = Integer.parseInt(fullMove);

            if (half < 0) {
                throw new FENConvertException("Invalid FEN: Half-move clock cannot be negative.",
                        FENErrorType.HALF_MOVE_CLK,
                        halfMove);
            }
            if (full < 1) {
                throw new FENConvertException("Invalid FEN: Full-move number must be 1 or greater.",
                        FENErrorType.FULL_MOVE_CLK,
                        fullMove);
            }
        } catch (NumberFormatException e) {
            throw new FENConvertException("Invalid FEN: Half-move or Full-move counters must be valid integers.",
                    FENErrorType.FULL_HALF_CLK_NOT_NUMBER);
        }
    }

    public static void validateLogicalState(Chessboard chessboard, GameVariant variant) {
        if(variant == GameVariant.GIVEAWAY || variant == GameVariant.SUICIDE) {
            return;
        }

        int oppositeSide = chessboard.side ^ 1;

        if(variant == GameVariant.HORDE && oppositeSide == white){
            return;
        }

        int oppositeKingSquare = BitBoardUtils.getLS1BIndex(
                chessboard.bitboards[oppositeSide == white ? K : k]
        );

        if (oppositeKingSquare == -1) {
            throw new FENConvertException("Invalid FEN: The side not to move is missing their King.",
                    FENErrorType.KING_COUNT);
        }

        if (MoveGenerator.isSquareAttacked(chessboard, oppositeKingSquare, chessboard.side)) {
            throw new FENConvertException("Invalid FEN: The side not to move is in check. (Impossible game state)",
                    FENErrorType.IMPOSSIBLE_GAME_STATE);
        }
    }

    private static void validatePocket(String pocket) {
        if (pocket.isEmpty()) return;

        for (char c : pocket.toCharArray()) {
            if ("pPnNbBrRqQ".indexOf(c) == -1) {
                throw new FENConvertException("Invalid FEN: Invalid or impossible piece '" + c + "' found in pocket.",
                        FENErrorType.UNKNOWN_PIECE_TYPE,
                        String.valueOf(c));
            }
        }
    }

    private static void validateChecksToken(String checksToken) {
        if (!checksToken.matches("^\\d+\\+\\d+$")) {
            throw new FENConvertException("Invalid FEN: Invalid check count format. (" + checksToken + ")",
                    FENErrorType.INVALID_THREE_CHECK_FORMAT,
                    checksToken);
        }

        String[] parts = checksToken.split("\\+");
        int first = Integer.parseInt(parts[0]);
        int second = Integer.parseInt(parts[1]);

        if (first < 0 || first > 3 || second < 0 || second > 3) {
            throw new FENConvertException("Invalid FEN: Check count must be between 0 and 3. (" + checksToken + ")",
                    FENErrorType.INVALID_THREE_CHECK_NUMBER,
                    checksToken);
        }
    }
}

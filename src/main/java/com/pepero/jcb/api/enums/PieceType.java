package com.pepero.jcb.api.enums;

import com.pepero.jcb.api.exception.game.PieceNotFoundException;
import com.pepero.jcb.core.constant.EncodedPieces;

import static com.pepero.jcb.core.constant.EncodedPieces.*;

/**
 * Piece type without piece color distinguishing
 */
public enum PieceType {
    PAWN(P),
    KNIGHT(N),
    BISHOP(B),
    ROOK(R),
    QUEEN(Q),
    KING(K),

    NONE(NO_PIECE_CONSTANT);

    private final int pieceType;

    private static final PieceType[] BY_INDEX = new PieceType[6];

    static {
        for (PieceType piece : values()) {
            int typeIndex = piece.getPieceType();
            if (typeIndex >= 0 && typeIndex <= 5) {
                BY_INDEX[typeIndex] = piece;
            }
        }
    }

    PieceType(int pieceType) {
        this.pieceType = pieceType;
    }

    /**
     * Get piece type (integer)
     *
     * @return piece type (integer)
     */
    public int getPieceType() {
        return pieceType;
    }

    /**
     * Get piece type from index
     * <p>
     * Example : 4 -> QUEEN || 2 -> BISHOP
     *
     * @param index piece index
     * @return piece
     */
    public static PieceType fromIndex(int index) {
        if(index == NO_PIECE_CONSTANT) return NONE;

        if (index < 0 || index > 11) {
            throw new PieceNotFoundException();
        }

        if(index >= 6) index %= 6;

        return BY_INDEX[index];
    }

    /**
     * Get piece type from char
     * <p>
     * Example : <br>
     * q -> QUEEN, B -> BISHOP
     *
     * @param c char input
     * @return piece type from char
     */
    public static PieceType fromChar(char c) {
        return switch (c) {
            case 'P', 'p' -> PAWN;
            case 'N', 'n' -> KNIGHT;
            case 'B', 'b' -> BISHOP;
            case 'R', 'r' -> ROOK;
            case 'Q', 'q' -> QUEEN;
            case 'K', 'k' -> KING;
            default -> NONE;
        };
    }

    @Override
    public String toString() {
        if(this == NONE) return "";

        return String.valueOf(EncodedPieces.ascii_pieces[getPieceType() % 6]);
    }
}

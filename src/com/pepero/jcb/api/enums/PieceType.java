package com.pepero.jcb.api.enums;

import com.pepero.jcb.api.exception.PieceNotFoundException;
import com.pepero.jcb.core.ChessBoardUtils;

public enum PieceType {
    PAWN(0),
    KNIGHT(1),
    BISHOP(2),
    ROOK(3),
    QUEEN(4),
    KING(5),

    NONE(-1);

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
        if(index == -1) return NONE;

        if (index < 0 || index > 11) {
            throw new PieceNotFoundException();
        }

        if(index >= 6) index %= 6;

        return BY_INDEX[index];
    }

    @Override
    public String toString() {
        if(this == NONE) return "";

        return String.valueOf(ChessBoardUtils.ascii_pieces[getPieceType() % 6]);
    }
}

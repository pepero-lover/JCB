package com.pepero.jcb.api;

import com.pepero.jcb.api.exception.PieceNotFoundException;
import com.pepero.jcb.core.ChessBoardUtils;

public enum Piece {
    WHITE_PAWN(0),
    WHITE_KNIGHT(1),
    WHITE_BISHOP(2),
    WHITE_ROOK(3),
    WHITE_QUEEN(4),
    WHITE_KING(5),

    BLACK_PAWN(6),
    BLACK_KNIGHT(7),
    BLACK_BISHOP(8),
    BLACK_ROOK(9),
    BLACK_QUEEN(10),
    BLACK_KING(11),

    NONE(-1);

    private final int pieceType;

    private static final Piece[] BY_INDEX = new Piece[12];

    static {
        for (Piece piece : values()) {
            int typeIndex = piece.getPieceType();
            if (typeIndex >= 0 && typeIndex <= 11) {
                BY_INDEX[typeIndex] = piece;
            }
        }
    }

    Piece(int pieceType) {
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
     * Get whether this piece is a white piece or not
     *
     * @return whether this piece is a white piece or not
     */
    public boolean isWhite() {
        return pieceType >= 0 && pieceType <= 5;
    }

    /**
     * Get whether this piece is a black piece or not
     *
     * @return whether this piece is a black piece or not
     */
    public boolean isBlack() {
        return pieceType >= 6 && pieceType <= 11;
    }

    /**
     * Get piece type from index
     * <p>
     * Example : 4 -> WHITE_QUEEN || 7 -> BLACK_KNIGHT
     *
     * @param index piece index
     * @return piece
     */
    public static Piece fromIndex(int index) {
        if(index == -1) return NONE;

        if (index < 0 || index > 11) {
            throw new PieceNotFoundException();
        }
        return BY_INDEX[index];
    }

    @Override
    public String toString() {
        return String.valueOf(ChessBoardUtils.ascii_pieces[getPieceType()]);
    }
}

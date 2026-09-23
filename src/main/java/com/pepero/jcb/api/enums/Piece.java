package com.pepero.jcb.api.enums;

import com.pepero.jcb.api.exception.game.PieceNotFoundException;
import com.pepero.jcb.core.constant.EncodedPieces;

import static com.pepero.jcb.core.constant.EncodedPieces.*;

/**
 * Piece type enum with color distinguishing
 */
public enum Piece {
    WHITE_PAWN(P),
    WHITE_KNIGHT(N),
    WHITE_BISHOP(B),
    WHITE_ROOK(R),
    WHITE_QUEEN(Q),
    WHITE_KING(K),

    BLACK_PAWN(p),
    BLACK_KNIGHT(n),
    BLACK_BISHOP(b),
    BLACK_ROOK(r),
    BLACK_QUEEN(q),
    BLACK_KING(k),

    NONE(NO_PIECE_CONSTANT);

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
     * Get piece type enum (PieceType.java)
     *
     * @return piece type (PieceType.java)
     */
    public PieceType getPieceTypeEnum() {
        if(this == NONE) return PieceType.NONE;

        return PieceType.fromIndex(pieceType % 6);
    }

    /**
     * Get piece type from char
     * <p>
     * Example : <br>
     * q -> BLACK_QUEEN, B -> WHITE_BISHOP
     *
     * @param c char input
     * @return piece type from char
     */
    public static Piece fromChar(char c) {
        return switch (c) {
            case 'P' -> WHITE_PAWN;
            case 'N' -> WHITE_KNIGHT;
            case 'B' -> WHITE_BISHOP;
            case 'R' -> WHITE_ROOK;
            case 'Q' -> WHITE_QUEEN;
            case 'K' -> WHITE_KING;

            case 'p' -> BLACK_PAWN;
            case 'n' -> BLACK_KNIGHT;
            case 'b' -> BLACK_BISHOP;
            case 'r' -> BLACK_ROOK;
            case 'q' -> BLACK_QUEEN;
            case 'k' -> BLACK_KING;
            default -> NONE;
        };
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
     * Example : 4 -> {@link #WHITE_QUEEN} || 7 -> {@link #BLACK_KNIGHT} <br>
     * You can find the index value on {@link EncodedPieces}
     *
     * @param index piece index
     * @return piece
     */
    public static Piece fromIndex(int index) {
        if(index == NO_PIECE_CONSTANT) return NONE;

        if (index < 0 || index > 11) {
            throw new PieceNotFoundException();
        }
        return BY_INDEX[index];
    }

    @Override
    public String toString() {
        return String.valueOf(EncodedPieces.ascii_pieces[getPieceType()]);
    }
}

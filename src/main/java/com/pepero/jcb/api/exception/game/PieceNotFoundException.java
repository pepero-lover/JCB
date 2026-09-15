package com.pepero.jcb.api.exception.game;

import com.pepero.jcb.api.enums.Piece;
import com.pepero.jcb.api.enums.PieceType;

/**
 * Thrown by couldn't find the piece type on converting piece type on {@link Piece}, {@link PieceType}
 */
public class PieceNotFoundException extends RuntimeException {
    public PieceNotFoundException() {
        super("Piece not found!");
    }
}

package com.pepero.jcb.api.exception;

public class PieceNotFoundException extends RuntimeException {
    public PieceNotFoundException() {
        super("Piece not found!");
    }
}

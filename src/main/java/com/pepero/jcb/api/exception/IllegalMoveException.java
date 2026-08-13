package com.pepero.jcb.api.exception;

public class IllegalMoveException extends RuntimeException {
    public IllegalMoveException(String move, String fen) {
        super("Illegal move detected! Move : " + move + ", FEN : " + fen);
    }

    public IllegalMoveException(String message) {
        super(message);
    }
}

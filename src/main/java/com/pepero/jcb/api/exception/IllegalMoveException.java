package com.pepero.jcb.api.exception;

public class IllegalMoveException extends RuntimeException {
    public IllegalMoveException(String move, String fen) {
        super("Illegal move detected! Move : " + move + ", FEN : " + fen);
    }
}

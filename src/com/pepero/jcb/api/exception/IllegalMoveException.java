package com.pepero.jcb.api.exception;

public class IllegalMoveException extends RuntimeException {
    public IllegalMoveException(String move) {
        super("Illegal move detected! Move : " + move);
    }
}

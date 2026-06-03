package com.pepero.jcb.api.exception;

public class ConvertMoveException extends RuntimeException {
    public ConvertMoveException(String cause, String move) {
        super("Converting move failed! Cause : " + cause + ", Move : " + move);
    }

    public ConvertMoveException(String cause) {
        super("Converting move failed! Cause : " + cause);
    }
}

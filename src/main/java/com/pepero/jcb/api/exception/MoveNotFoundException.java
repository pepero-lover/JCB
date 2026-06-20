package com.pepero.jcb.api.exception;

public class MoveNotFoundException extends RuntimeException {
    public MoveNotFoundException() {
        super("Couldn't find move node on move history tree!");
    }

    public MoveNotFoundException(String message) {
        super(message);
    }
}

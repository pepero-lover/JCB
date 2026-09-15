package com.pepero.jcb.api.exception.game;

public class MoveNotFoundException extends RuntimeException {
    public MoveNotFoundException(String message) {
        super(message);
    }
}

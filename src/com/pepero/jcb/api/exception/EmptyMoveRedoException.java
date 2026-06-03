package com.pepero.jcb.api.exception;

public class EmptyMoveRedoException extends RuntimeException {
    public EmptyMoveRedoException() {
        super("There is noting to remake move!");
    }
}

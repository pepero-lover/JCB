package com.pepero.jcb.api.exception;

public class EmptyMoveUndoException extends RuntimeException {
    public EmptyMoveUndoException() {
        super("There is noting to unmake move!");
    }
}

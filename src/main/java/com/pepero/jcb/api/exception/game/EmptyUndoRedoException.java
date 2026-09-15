package com.pepero.jcb.api.exception.game;

import com.pepero.jcb.api.exception.type.UndoRedoType;

public class EmptyUndoRedoException extends RuntimeException {
    private final UndoRedoType makingType;

    public EmptyUndoRedoException(UndoRedoType makingType) {
        super("There is noting to " + (makingType == UndoRedoType.UNMAKING ? "unmake" : "remake")  + " move!");
        this.makingType = makingType;
    }

    public UndoRedoType getMakingType() {
        return makingType;
    }
}

package com.pepero.jcb.api.exception.game;

import com.pepero.jcb.api.enums.Square;

/**
 * Thrown by converting incorrect square input on {@link Square}
 */
public class SquareConvertException extends RuntimeException {
    public SquareConvertException(int square){
        super("Square must be less than 64 and greater than or equal to 0 (Square input : \"" + square + "\")");
    }

    public SquareConvertException(String square){
        super("Square must be chess board square (Square input : \"" + square + "\")");
    }
}

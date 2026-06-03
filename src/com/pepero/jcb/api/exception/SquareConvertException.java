package com.pepero.jcb.api.exception;

public class SquareConvertException extends RuntimeException {
    public SquareConvertException(int square){
        super("Square must be less than 64 and greater than or equal to 0 (Square input : \"" + square + "\")");
    }

    public SquareConvertException(String square){
        super("Square must be chess board square (Square input : \"" + square + "\")");
    }
}

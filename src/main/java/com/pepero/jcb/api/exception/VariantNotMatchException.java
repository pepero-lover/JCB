package com.pepero.jcb.api.exception;

/**
 * Thrown by the game variant not matches the called method
 */
public class VariantNotMatchException extends RuntimeException {
    public VariantNotMatchException(String message) {
        super(message);
    }
}

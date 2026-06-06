package com.pepero.jcb.api.exception;

public class VariationModeException extends RuntimeException {
    public VariationModeException() {
        super("This mode is NOT Variation mode!");
    }
}

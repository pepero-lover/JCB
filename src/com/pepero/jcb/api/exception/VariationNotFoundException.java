package com.pepero.jcb.api.exception;

public class VariationNotFoundException extends RuntimeException {
    public VariationNotFoundException() {
        super("Variation index not found!");
    }

    public VariationNotFoundException(String anotherCause) {
        super(anotherCause);
    }
}

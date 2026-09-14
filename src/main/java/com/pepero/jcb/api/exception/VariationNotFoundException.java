package com.pepero.jcb.api.exception;

/**
 * Thrown when the variation index out of bounds.
 */
public class VariationNotFoundException extends RuntimeException {
    public VariationNotFoundException() {
        super("Variation not found!");
    }

    public VariationNotFoundException(int requestedIndex, int availableCount) {
        super("Variation index out of bounds! (Requested : " + requestedIndex +
                ", Available : 0.." + (availableCount - 1) + ")");
    }
}
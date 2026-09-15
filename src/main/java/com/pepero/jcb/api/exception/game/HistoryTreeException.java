package com.pepero.jcb.api.exception.game;

/**
 * Thrown by the history tree's root node deleted, etc.
 */
public class HistoryTreeException extends RuntimeException {
    public HistoryTreeException(String message) {
        super(message);
    }
}

package com.pepero.jcb.api.exception;

/**
 * Thrown when the uci engine has not the option, etc.
 */
public class UCIEngineException extends RuntimeException {
    public UCIEngineException(String message) {
        super(message);
    }
}

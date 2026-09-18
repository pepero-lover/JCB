package com.pepero.jcb.api.exception.engine;

/**
 * Thrown when the uci engine has not the option, etc.
 */
public class UCIEngineException extends RuntimeException {
    public UCIEngineException(String message) {
        super(message);
    }

    public UCIEngineException(String message, Throwable cause) {
        super(message, cause);
    }
}

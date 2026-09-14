package com.pepero.jcb.api.exception;

import com.pepero.jcb.api.exception.type.FENErrorType;
import com.pepero.jcb.api.exception.type.PGNErrorType;

/**
 * Thrown by loading pgn and there is an unexpected value or something. <p>
 *
 * There is {@link PGNErrorType} parameter on
 */
public class PGNConvertException extends RuntimeException {
    private final PGNErrorType errorType;
    private String realValue;

    public PGNConvertException(String message, PGNErrorType type) {
        super(message);
        this.errorType = type;
    }

    public PGNConvertException(String message, PGNErrorType type, String realValue) {
        super(message);
        if (!type.hasRealValue()) {
            throw new IllegalArgumentException(
                    "FENErrorType." + type + " does not carry a real value, but one was provided.");
        }
        this.errorType = type;
        this.realValue = realValue;
    }

    /**
     * Get error type
     */
    public PGNErrorType getErrorType() {
        return errorType;
    }

    /**
     * Get real value on this error. <b>(can be null!)</b> <br>
     * If you want to know this error type has real value, use {@link PGNErrorType#hasRealValue()}.
     */
    public String getRealValue() {
        return realValue;
    }
}

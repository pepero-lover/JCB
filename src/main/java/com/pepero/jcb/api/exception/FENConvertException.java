package com.pepero.jcb.api.exception;

import com.pepero.jcb.api.exception.type.FENErrorType;

public class FENConvertException extends RuntimeException {
    private final FENErrorType errorType;
    private String realValue;

    public FENConvertException(String message, FENErrorType type) {
        super(message);
        this.errorType = type;
    }

    public FENConvertException(String message, FENErrorType type, String realValue) {
        super(message);
        this.errorType = type;
        this.realValue = realValue;
    }

    /**
     * Get error type
     */
    public FENErrorType getErrorType() {
        return errorType;
    }

    /**
     * Get real value on this error. <b>(can be null!)</b>
     */
    public String getRealValue() {
        return realValue;
    }
}

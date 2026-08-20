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

    public FENErrorType getErrorType() {
        return errorType;
    }

    public String getRealValue() {
        return realValue;
    }
}

package com.pepero.jcb.api.exception.type;

import com.pepero.jcb.api.exception.PGNConvertException;

public enum PGNErrorType {
    PGN_EMPTY(false),  // when pgn string is empty

    WRONG_HEADER(true),  // when header line is wrong
    // e.g.     ]White "pepero-lover"[

    VARIATION_STACK_EMPTY_POP(false),  // when variation hasn't started on this line, but the variation ended on pgn string.
    // e.g.     "1. e4 e5 2. Nf3 )"

    VARIATION_STACK_NOT_EMPTY(false);  // when the pgn ended, but the variation hasn't ended.
    // e.g.     "1. e4 e5 (1... c5 2. Nf3"



    private final boolean hasRealValue;

    PGNErrorType(boolean hasRealValue) {
        this.hasRealValue = hasRealValue;
    }

    /**
     * @return whether this error type is expected to carry a {@code realValue}
     *         on {@link PGNConvertException}.
     */
    public boolean hasRealValue() {
        return hasRealValue;
    }
}

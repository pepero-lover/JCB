package com.pepero.jcb.api.exception.type;

public enum FENErrorType {
    FEN_NULL(false), // when FEN is null

    FEN_TOKEN_SIZE(true), // when fen token is too small or too big (example : "rnbqkbnr/pppppppp/8/.... /RNBQKBNR w")
    // shows fen token count

    CRAZYHOUSE_POCKET(false), // when fen crazy house pocket data is wrong (example : rnbqk... RNBQKBNR][)

    EMPTY_SQUARE_OUT_OF_BOUNDS(false), // when fen empty square is less than 1 or greater than 8

    PAWN_EXIST_LAST_RANK(false), // when fen on pawn exist on last or first rank

    UNKNOWN_PIECE_TYPE(true), // when fen piece type character not matches any chess characters

    RANK_SQUARE_NOT_8(true), // when rank total square is not 8 (shows rank which occurred error)

    KING_COUNT(false), // when white/black king count isn't 1

    TURN(false), // when fen turn value isn't "w" or "b"

    CASTLING(true), // when fen castling rights value is wrong (shows castling rights value)

    ENPASSANT_SQUARE(true), // when fen enpassant square value is wrong (shows enpassant value)

    HALF_MOVE_CLK(true), // when fen half move clock is wrong (shows half move clk)

    FULL_MOVE_CLK(true), // when fen full move clock is wrong (shows full move clk)

    FULL_HALF_CLK_NOT_NUMBER(false), // when fen half or full move clock is not a number

    IMPOSSIBLE_GAME_STATE(false), // when this position fen's king is under attacked, and it's opponent's turn
    // or racing kings and one of the kings under attacked

    INVALID_THREE_CHECK_FORMAT(true), // when fen 3 check data not found (shows 3 check token)

    INVALID_THREE_CHECK_NUMBER(true), // when fen 3 check data is less than 0 or greater than 3 (shows 3 check token)

    UNKNOWN(false); // fen error occurred, but could not check why error occurred.

    private final boolean hasRealValue;

    FENErrorType(boolean hasRealValue) {
        this.hasRealValue = hasRealValue;
    }

    /**
     * @return whether this error type is expected to carry a {@code realValue}
     *         on {@link com.pepero.jcb.api.exception.FENConvertException}.
     */
    public boolean hasRealValue() {
        return hasRealValue;
    }
}
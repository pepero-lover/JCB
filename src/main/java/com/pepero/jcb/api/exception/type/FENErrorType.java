package com.pepero.jcb.api.exception.type;

public enum FENErrorType {
    FEN_NULL, // when FEN is null

    FEN_TOKEN_SIZE, // when fen token is too small or too big (example : "rnbqkbnr/pppppppp/8/.... /RNBQKBNR w")
    // shows fen token count

    CRAZYHOUSE_POCKET, // when fen crazy house pocket data is wrong (example : rnbqk... RNBQKBNR][)

    EMPTY_SQUARE_OUT_OF_BOUNDS, // when fen empty square is less than 1 or greater than 8

    PAWN_EXIST_LAST_RANK, // when fen on pawn exist on last or first rank

    UNKNOWN_PIECE_TYPE, // when fen piece type character not matches any chess characters

    RANK_SQUARE_NOT_8, // when rank total square is not 8 (shows rank which occurred error)

    KING_COUNT, // when white/black king count isn't 1

    TURN, // when fen turn value isn't "w" or "b"

    CASTLING, // when fen castling rights value is wrong (shows castling rights value)

    ENPASSANT_SQUARE, // when fen enpassant square value is wrong (shows enpassant value)

    HALF_MOVE_CLK, // when fen half move clock is wrong (shows half move clk)

    FULL_MOVE_CLK, // when fen full move clock is wrong (shows full move clk)

    FULL_HALF_CLK_NOT_NUMBER, // when fen half or full move clock is not a number

    IMPOSSIBLE_GAME_STATE, // when this position fen's king is under attacked, and it's opponent's turn

    INVALID_THREE_CHECK_FORMAT, // when fen 3 check data not found (shows 3 check token)

    INVALID_THREE_CHECK_NUMBER, // when fen 3 check data is less than 0 or greater than 3 (shows 3 check token)

    UNKNOWN // fen error occurred, but could not check why error occurred.
}

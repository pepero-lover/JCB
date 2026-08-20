package com.pepero.jcb.api.exception.type;

public enum ConvertErrorType {
    // default : shows occurred position fen, shows occurred move

    INCORRECT_SQUARE, // incorrect square like "h9", "a", "a0"

    PIECE_NOT_FOUND, // piece on board not found

    PROMOTION_CHARACTER, // promotion char not matches any other piece codes (example : 'g', 'p', 'q')
    // expected : 'q', 'r', 'b', 'n'

    ILLEGAL_MOVE, // move is illegal move

    AMBIGUITY_COULD_NOT_BE_RESOLVED, // when ambiguity could not be resolved
    // example :
    //  8   . . . . . . . .
    //  7   . . . . N . . .
    //  6   . N . . . . . .
    //  5   . . . . . . . .
    //  4   . . . . . N . .
    //  3   . . N . . . . .
    //  2   . . . . . . . .
    //  1   . . . . . . . .
    //
    //      a b c d e f g h
    // in this position, the knight on e7 can go d5, and also b6, c3, f4 knights can go d5.
    // but if san input is "Nd5", the source square knight is unidentifiable.
    // so throws exception.

    DROP_MOVE, // drop format is wrong (example : "@e4")

    LENGTH, // LAN length is too short


}

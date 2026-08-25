package com.pepero.jcb.api.enums;

/**
 * Game result types for distinguishing who has won this game
 */
public enum GameResult {
    // white won
    WHITE_WON,

    // black won
    BLACK_WON,

    // draw
    DRAW,

    // aborted
    ABORTED,

    // not yet finished or error occurred
    UNKNOWN
}

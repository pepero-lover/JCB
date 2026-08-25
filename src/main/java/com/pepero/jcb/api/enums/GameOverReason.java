package com.pepero.jcb.api.enums;

/**
 * Game over reasons for distinguishing why this game ended.
 */
public enum GameOverReason {

    // Normal game overing types

    // if the game ended with checkmate.
    CHECKMATE,

    // if the game ended with stalemate.
    STALEMATE,

    // if the game ended with a five-fold repetition draw
    FIVEFOLD,

    // if the game ended with seventy-five moves draw
    SEVENTYFIVE_MOVES,

    // if the game ended with insufficient material
    INSUFFICIENT_MATERIAL,


    // Claims

    // if the game ended with fifty-moves draw claim
    FIFTYMOVES_CLAIM,

    // if the game ended with three-fold repetition draw claim
    THREEFOLD_CLAIM,


    // Variants

    // when the game variant is three-check, and one of the player checked 3 times
    THREE_CHECK,

    // when the game variant is king of the hill, and one of the player's king has reached on center square
    KING_OF_THE_HILL,

    // when the game variant is horde, and white player doesn't have any pieces
    HORDE,

    // when the game variant is giveaway, and one of the player's piece count is zero or legal move count is zero
    GIVEAWAY,

    // when the game variant is suicide, and one of the player's piece count is zero or legal move count is zero
    SUICIDE,

    // when the game variant is atomic, and one of the player's king has been exploded
    ATOMIC,

    // when the game variant is king race, and one of the player (or both players) reached the 8th rank
    KING_RACE,


    // External game adjustment

    // agreement draw
    AGREEMENT_DRAW,

    // resignation
    RESIGNATION,

    // time over
    TIMEOVER,

    // adjudication by external
    ADJUDICATION,


    // Not yet game overed

    // not yet game overed
    NOTGAMEOVER
}

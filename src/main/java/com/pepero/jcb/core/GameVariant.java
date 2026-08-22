package com.pepero.jcb.core;

public enum GameVariant {
    STANDARD,
    CRAZY_HOUSE,
    THREE_CHECK,
    KING_OF_THE_HILL,
    HORDE,

    GIVEAWAY,                // Lichess Give away variant (if position is stalemate, it's not a draw)

    SUICIDE, /* or FICS */   // Antichess variant, but if occupancy[side] isn't 0L, and legal move is 0,
                             // the game result is decided by opponent, my piece counts.

    ATOMIC,
    RACING_KINGS
}

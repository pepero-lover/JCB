package com.pepero.jcb.core;

public enum GameVariant {
    STANDARD,
    CRAZY_HOUSE,
    THREE_CHECK,
    KING_OF_THE_HILL,
    HORDE,
    ANTICHESS, // Lichess Give away variant (if position is stalemate, it's not a draw)
    ATOMIC, RACING_KINGS
}

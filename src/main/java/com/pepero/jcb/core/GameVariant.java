package com.pepero.jcb.core;

/**
 * Game variants enum. <p>
 *
 * JCB supports 8 variants like crazy house, three check, horde, ... <p>
 * And there is {@link #GIVEAWAY}, and {@link #SUICIDE}. <br>
 * Those belong to the Antichess family. <br>
 * The only difference between giveaway and suicide is distinguishing who won the game. <br>
 * If there are legal moves on the position, and it's white's turn, white won. <br>
 * But on suicide case, distinguishes with white's and black's piece count. <br>
 * If white piece's count is greater than black piece's count, black win. <br>
 * If equal, it's a draw. and finally, if lower, white win.
 */
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

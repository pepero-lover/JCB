package com.pepero.jcb.api.parse.pgn;

/**
 * PGN token type for identifying what's the type of this token like {@link #COMMENT} data, {@link #MOVE} data, etc. <br>
 * Used on {@link PGNLexer}.
 */
public enum TokenType {
    NUMBER_INDICATOR,  // move number '1.', '5...' ...
    MOVE,              // san move string 'e4', 'e5'
    COMMENT,           // move comment {"Test comment"}
    NAG,               // move notation '$1', '$3' ...
    VARIATION_START,   // variation start '('
    VARIATION_END,     // variation end ')'
    RESULT,            // result of game like 1-0, 1/2-1/2, 0-1 and *
    EOF                // end of file (pgn string)
}

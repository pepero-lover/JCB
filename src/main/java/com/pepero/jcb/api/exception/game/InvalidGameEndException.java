package com.pepero.jcb.api.exception.game;

import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.api.enums.GameResult;

/**
 * Thrown when the game result and game over reason not matches on
 * {@link com.pepero.jcb.api.ChessGame#forceEndGameExternal(GameResult, GameOverReason)} <br>
 * e.g. {@link GameResult#WHITE_WON} and {@link GameOverReason#STALEMATE}.
 */
public class InvalidGameEndException extends RuntimeException {
    public InvalidGameEndException(String message) {
        super(message);
    }
}
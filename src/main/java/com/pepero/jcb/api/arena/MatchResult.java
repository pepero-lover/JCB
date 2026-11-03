package com.pepero.jcb.api.arena;

import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.api.enums.GameResult;

/**
 * Match result DTO for {@link MatchRunnerListener}, storing game result / reason, engine winner, and pgn string.
 *
 * @param result game result (white/draw/black)
 * @param matchWinner match winner (engine1/draw/engine2)
 * @param reason game over reason
 * @param pgn pgn string
 */
public record MatchResult(
        GameResult result,
        MatchWinner matchWinner,
        GameOverReason reason,
        String pgn
) { }

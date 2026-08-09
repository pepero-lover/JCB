package com.pepero.jcb.api.arena;

import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.api.enums.GameResult;

public record MatchFinishedEvent(
        GameResult result,
        EngineWinner engineWinner,
        GameOverReason reason,
        String pgn,
        String finalFen
) {}
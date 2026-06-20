package com.pepero.jcb.api.dto;

import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.api.enums.GameResult;

public record MatchResult(
        GameResult result, GameOverReason reason, String pgn
) { }

package com.pepero.jcb.api.dto;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.enums.GameResult;

import java.util.Map;

public record PGNGame(
        Map<String, String> headers,
        MoveNodeDTO rootNode,
        GameResult matchResult
) { }

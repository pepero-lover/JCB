package com.pepero.jcb.api.parse.pgn;

import com.pepero.jcb.api.dto.MoveNodeDTO;
import com.pepero.jcb.api.enums.GameResult;

import java.util.Map;

public record PGNGame(
        Map<String, String> headers,
        MoveNodeDTO rootNode,
        GameResult matchResult
) { }

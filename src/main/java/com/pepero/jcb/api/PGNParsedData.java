package com.pepero.jcb.api;

import com.pepero.jcb.api.enums.GameResult;
import com.pepero.jcb.core.GameVariant;

import java.util.Map;

record PGNParsedData(
        String startFEN,
        GameVariant variant,
        boolean isChess960,
        MoveNode rootNode,
        Map<Long, MoveNode> cache,
        Map<String, String> header,
        GameResult gameResult
) {
}

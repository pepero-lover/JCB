package com.pepero.jcb.api;

import com.pepero.jcb.api.enums.GameResult;
import com.pepero.jcb.core.GameVariants;

import java.util.Map;

record PGNParsedData(
        String startFEN,
        GameVariants variants,
        boolean isChess960,
        MoveNode rootNode,
        Map<Long, MoveNode> cache,
        Map<String, String> header,
        GameResult gameResult
) {
}

package com.pepero.jcb.api;

import com.pepero.jcb.api.dto.MoveNodeDTO;
import com.pepero.jcb.api.enums.GameResult;

import java.util.Map;

/**
 * PGN data for converting {@link ChessGame} to PGN string
 *
 * @param headers
 * @param rootNode
 * @param matchResult
 */
record PGNGame(
        Map<String, String> headers,
        MoveNodeDTO rootNode,
        GameResult matchResult
) { }

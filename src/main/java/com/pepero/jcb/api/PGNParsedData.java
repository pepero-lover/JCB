package com.pepero.jcb.api;

import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.api.enums.GameResult;
import com.pepero.jcb.api.util.LongObjectOpenHashMap;
import com.pepero.jcb.core.GameVariant;

import java.util.Map;

/**
 * Stores PGN parsed data created on {@link PGNParser} class. <br>
 * And this PGN parse data goes to {@link ChessGame} and applied.
 *
 * @param startFEN start fen data
 * @param variant chess variant data
 * @param isChess960 whether this position is chess 960 position
 * @param rootNode root node history tree
 * @param cache cache data for going position on history tree
 * @param header header data on PGN
 * @param gameResult game result data
 * @param gameOverReason game over reason data
 */
record PGNParsedData(
        String startFEN,
        GameVariant variant,
        boolean isChess960,
        MoveNode rootNode,
        LongObjectOpenHashMap<MoveNode> cache,
        Map<String, String> header,
        GameResult gameResult,
        GameOverReason gameOverReason
) {
}

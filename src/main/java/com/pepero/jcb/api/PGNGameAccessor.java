package com.pepero.jcb.api;

import com.pepero.jcb.core.Chessboard;
import java.util.ArrayList;
import java.util.List;

/**
 * Internal bridge exposing package-private PGN parsing internals
 * to the api.book package, without making them fully public API.
 */
public final class PGNGameAccessor {

    public record ParsedMainline(
            String startFEN,
            boolean isChess960,
            com.pepero.jcb.core.GameVariant variant,
            List<Integer> mainlineMoveData
    ) {}

    public static ParsedMainline extractMainline(String gamePgn, int maxNodesCount, int maxPly) {
        PGNParsedData parsed = PGNParser.parse(gamePgn, maxNodesCount);

        List<Integer> moves = new ArrayList<>();
        MoveNode node = parsed.rootNode();
        int ply = 0;

        while (!node.children.isEmpty() && ply < maxPly) {
            MoveNode next = node.children.getFirst();
            moves.add(next.moveData.originEncodedData());
            node = next;
            ply++;
        }

        return new ParsedMainline(parsed.startFEN(), parsed.isChess960(), parsed.variant(), moves);
    }
}
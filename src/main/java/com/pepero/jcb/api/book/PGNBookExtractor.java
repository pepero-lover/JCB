package com.pepero.jcb.api.book;

import com.pepero.jcb.api.PGNGameAccessor;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.MoveGenerator;

import java.util.ArrayList;
import java.util.List;

public class PGNBookExtractor {

    public record BookMove(long hash, int polyMove) {}

    public static List<BookMove> extract(String gamePgn, int maxPly) {
        List<BookMove> result = new ArrayList<>();

        PGNGameAccessor.ParsedMainline parsed;
        try {
            parsed = PGNGameAccessor.extractMainline(gamePgn, 100_000, maxPly);
        } catch (Exception e) {
            return result;
        }

        Chessboard board = new Chessboard(parsed.startFEN(), parsed.isChess960(), parsed.variant());

        for (int moveData : parsed.mainlineMoveData()) {
            long hash = PolyglotHashUtils.getPolyglotHash(board);
            int polyMove = PolyglotMoveEncoder.encode(moveData, board);
            result.add(new BookMove(hash, polyMove));
            MoveGenerator.makeMove(board, moveData);
        }

        return result;
    }
}
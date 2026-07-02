package com.pepero.jcb.perft.crazyhouse;

import com.pepero.jcb.constant.BoardSquares;
import com.pepero.jcb.constant.MoveCache;
import com.pepero.jcb.core.*;
import com.pepero.jcb.encode.EncodeMove;
import com.pepero.jcb.util.TimeUtils;

public class PerftCrazyHouse {

    public static long nodes;

    public static void perftDriver(Chessboard chessboard, int depth) {
        if (depth == 0) {
            nodes++;
            return;
        }

        int[] moveList = MoveCache.SEARCH_MOVE_SINGLE[chessboard.ply];

        int moveCount = MoveGenerator.generateMoves(chessboard, moveList);

        for (int i = 0; i < moveCount; i++) {
            int move = moveList[i];

            MoveGenerator.makeMove(chessboard, move);

            perftDriver(chessboard, depth - 1);

            MoveGenerator.unmakeMove(chessboard, move);
        }
    }

    /**
     * Perft Test
     */
    public static void perftTest(Chessboard chessboard, int depth) {
        System.out.println("\n    Performance test    \n");

        nodes = 0;

        int[] moveList = MoveCache.SEARCH_MOVE_SINGLE[chessboard.ply];
        int moveCount = MoveGenerator.generateMoves(chessboard, moveList);

        long startTime = TimeUtils.getTimeNt();

        for (int i = 0; i < moveCount; i++) {
            int move = moveList[i];

            MoveGenerator.makeMove(chessboard, move);

            long cumulative_nodes = nodes;

            perftDriver(chessboard, depth - 1);

            long old_nodes = nodes - cumulative_nodes;
            MoveGenerator.unmakeMove(chessboard, move);

            System.out.println("    move: " + EncodeMove.moveToString(move,
                    chessboard.gameVariants == GameVariants.CHESS960) + "  nodes: " + old_nodes);
        }

        long endTime = TimeUtils.getTimeNt();

        long durationNs = endTime - startTime;
        long durationMs = durationNs / 1_000_000;

        long nps = 0;
        if (durationNs > 0) {
            nps = (long) ((double) nodes / ((double) durationNs / 1_000_000_000.0));
        }

        System.out.println("\n\n    Depth: " + depth);
        System.out.println("    Nodes: " + nodes);
        System.out.println("     Time: " + durationMs + " ms ( + " + (durationNs % 1_000_000) + " ns)");
        System.out.printf("      NPS: %,d (%.2f MNPS)\n", nps, (double) nps / 1_000_000.0);
    }

    public static void main(String[] args) {
        Initializer.init();

        Chessboard chessboard = new Chessboard(GameVariants.CRAZY_HOUSE);

        ChessboardUtils.parseFen(chessboard, "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR[P] w KQkq - 0 1");

        // JVM preheat
        System.out.println("Preheating...");
        perftDriver(chessboard, 2);
        System.out.println("Preheating complete!");

        perftTest(chessboard, 2);
    }
}
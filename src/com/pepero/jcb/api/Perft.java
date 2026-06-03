package com.pepero.jcb.api;

import com.pepero.jcb.util.TimeUtils;

import java.util.List;

public class Perft {
    // leaf nodes (number of positions reached during the test of the move generator at a given depth
    public static long nodes;

    // perft driver
    public static void perftDriver(ChessGame chessGame, int depth){
        // recursion escape condition
        if(depth == 0){
            // increment nodes count (count reached positions)
            nodes++;
            return;
        }

        List<MoveInfo> moveList = chessGame.getLegalMoves();

        // loop over generated moves
        for (MoveInfo moveInfo : moveList) {
            chessGame.makeMove(moveInfo);

            // call perft driver recursively
            perftDriver(chessGame, depth - 1);

            chessGame.unmakeMove();
        }
    }

    // perft test
    public static void perftTest(ChessGame chessGame, int depth){
        System.out.println("\n    Performance test    \n");

        // reset nodes count
        nodes = 0;

        List<MoveInfo> moveList = chessGame.getLegalMoves();

        // init start time
        long startTime = TimeUtils.getTimeNt();

        // loop over generated moves
        for (MoveInfo moveInfo : moveList) {
            chessGame.makeMove(moveInfo);

            // cumulative nodes
            long cumulative_nodes = nodes; // noes

            // call perft driver recursively
            perftDriver(chessGame, depth - 1);

            // old nodes
            long old_nodes = nodes - cumulative_nodes;

            // take back
            chessGame.unmakeMove();

            // print move
            System.out.println("    move: " +
                    moveInfo +
                    "  nodes: " + old_nodes);
        }

        // init end time
        long endTime = TimeUtils.getTimeNt();

        // calculate duration
        long durationNs = endTime - startTime;
        long durationMs = durationNs / 1_000_000;

        // calculate nodes per second (NPS)
        long nps = 0;
        if (durationNs > 0) {
            nps = (long) ((double) nodes / ((double) durationNs / 1_000_000_000.0));
        }

        // print results
        System.out.println("\n\n    Depth: " + depth);
        System.out.println("    Nodes: " + nodes);
        System.out.println("     Time: " + durationMs + " ms ( + " + (durationNs % 1_000_000) + " ns)");
        System.out.printf("      NPS: %,d (%.2f MNPS)\n", nps, (double) nps / 1_000_000.0);
    }
}
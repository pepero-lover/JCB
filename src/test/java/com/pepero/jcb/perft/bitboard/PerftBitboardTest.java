package com.pepero.jcb.perft.bitboard;

import com.pepero.jcb.constant.BoardSquares;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.ChessboardUtils;
import com.pepero.jcb.core.Initializer;
import com.pepero.jcb.core.MoveGenerator;
import com.pepero.jcb.encode.EncodeMove;
import com.pepero.jcb.util.TimeUtils;

public class PerftBitboardTest {

    public static long nodes;

    public static void perftDriver(Chessboard chessboard, int depth) {
        if (depth == 0) {
            nodes++;
            return;
        }

        int[] moveList = new int[255];

        int moveCount = MoveGenerator.generateMoves(chessboard, moveList);

        for (int i = 0; i < moveCount; i++) {
            int move = moveList[i];

            if (MoveGenerator.makeMove(chessboard, move)) {
                perftDriver(chessboard, depth - 1);

                MoveGenerator.unmakeMove(chessboard, move);
            }
        }
    }

    /**
     * Perft Test 시작점 (루트 노드)
     */
    public static void perftTest(Chessboard chessboard, int depth) {
        System.out.println("\n    Performance test    \n");

        nodes = 0;

        int[] moveList = new int[255];
        int moveCount = MoveGenerator.generateMoves(chessboard, moveList);

        long startTime = TimeUtils.getTimeNt();

        for (int i = 0; i < moveCount; i++) {
            int move = moveList[i];

            if (MoveGenerator.makeMove(chessboard, move)) {
                long cumulative_nodes = nodes;

                perftDriver(chessboard, depth - 1);

                long old_nodes = nodes - cumulative_nodes;
                MoveGenerator.unmakeMove(chessboard, move);

                int source = EncodeMove.getMoveSource(move);
                int target = EncodeMove.getMoveTarget(move);
                int promoted = EncodeMove.getMovePromoted(move);

                String moveStr = BoardSquares.square_to_coordinates[source] +
                        BoardSquares.square_to_coordinates[target];

                if (promoted != 0) {
                    Character promoChar = ChessboardUtils.encoded_piece_to_char.get(promoted);
                    if (promoChar != null) {
                        moveStr += promoChar.toString().toLowerCase();
                    }
                }

                System.out.println("    move: " + moveStr + "  nodes: " + old_nodes);
            }
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

        Chessboard chessboard = new Chessboard();

        ChessboardUtils.parseFen(chessboard, Chessboard.start_position);

        // JVM preheat
        System.out.println("Preheating...");
        perftDriver(chessboard, 6);
        System.out.println("Preheating complete!");

        perftTest(chessboard, 6);
    }
}
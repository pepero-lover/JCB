package com.pepero.jcb.perft.crazyhouse;

import com.pepero.jcb.core.constant.MoveCache;
import com.pepero.jcb.core.*;
import com.pepero.jcb.core.encode.EncodeMove;
import com.pepero.jcb.core.util.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PerftCrazyHouseMultiThread {
    public static long perftDriver(Chessboard chessboard, int depth) {
        if (depth == 0) {
            return 1;
        }

        long nodes = 0;
        int[] moveList = MoveCache.SEARCH_MOVE_CACHE.get()[chessboard.ply];
        int moveCount = MoveGenerator.generateMoves(chessboard, moveList);

        for (int i = 0; i < moveCount; i++) {
            int move = moveList[i];

            MoveGenerator.makeMove(chessboard, move);

            nodes += perftDriver(chessboard, depth - 1);

            MoveGenerator.unmakeMove(chessboard, move);
        }
        return nodes;
    }

    private static class PerftResult {
        String moveStr;
        long nodes;

        public PerftResult(String moveStr, long nodes) {
            this.moveStr = moveStr;
            this.nodes = nodes;
        }
    }

    public static void perftTest(Chessboard chessboard, int depth) {
        System.out.println("\n    Performance test (Multi-threaded)    \n");

        int[] moveList = MoveCache.SEARCH_MOVE_CACHE.get()[chessboard.ply];
        int moveCount = MoveGenerator.generateMoves(chessboard, moveList);

        long startTime = TimeUtils.getTimeNt();

        int cores = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(cores);
        List<Future<PerftResult>> futures = new ArrayList<>();

        for (int i = 0; i < moveCount; i++) {
            int move = moveList[i];

            MoveGenerator.makeMove(chessboard, move);

            final Chessboard clonedBoard = new Chessboard(chessboard);

            MoveGenerator.unmakeMove(chessboard, move);

            String finalMoveStr = EncodeMove.moveToString(move);
            Callable<PerftResult> task = () -> {
                long branchNodes = perftDriver(clonedBoard, depth - 1);
                return new PerftResult(finalMoveStr, branchNodes);
            };
            futures.add(executor.submit(task));
        }

        long totalNodes = 0;

        try {
            for (Future<PerftResult> future : futures) {
                PerftResult result = future.get();
                totalNodes += result.nodes;
                System.out.println("    move: " + result.moveStr + "  nodes: " + result.nodes);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }

        long endTime = TimeUtils.getTimeNt();
        long durationNs = endTime - startTime;
        long durationMs = durationNs / 1_000_000;

        long nps = 0;
        if (durationNs > 0) {
            nps = (long) ((double) totalNodes / ((double) durationNs / 1_000_000_000.0));
        }

        System.out.println("\n\n    Depth: " + depth);
        System.out.println("    Nodes: " + totalNodes);
        System.out.println("     Time: " + durationMs + " ms ( + " + (durationNs % 1_000_000) + " ns)");
        System.out.printf("      NPS: %,d (%.2f MNPS)\n", nps, (double) nps / 1_000_000.0);
    }

    public static void main(String[] args) {
        Initializer.init();

        Chessboard chessboard = new Chessboard(GameVariant.CRAZY_HOUSE);
        ChessboardUtils.parseFen(chessboard, Chessboard.start_position);

        System.out.println("Preheating...");
        perftDriver(chessboard, 5);
        System.out.println("Preheating complete!");

        perftTest(chessboard, 5);
    }
}

package com.pepero.jcb.perft.api;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.util.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PerftMultiThread {
    public static long perftDriver(ChessGame chessGame, int depth) {
        if (depth == 0) {
            return 1;
        }

        long localNodes = 0;
        List<MoveInfo> moveList = chessGame.getLegalMoves();

        for (MoveInfo moveInfo : moveList) {
            chessGame.makeMove(moveInfo);
            localNodes += perftDriver(chessGame, depth - 1);
            chessGame.unmakeMove();
        }

        return localNodes;
    }

    private static class PerftResult {
        String moveStr;
        long nodes;

        public PerftResult(String moveStr, long nodes) {
            this.moveStr = moveStr;
            this.nodes = nodes;
        }
    }

    public static void perftTest(ChessGame chessGame, int depth) {
        System.out.println("\n    Performance test (Multi-threaded)    \n");

        List<MoveInfo> moveList = chessGame.getLegalMoves();
        long startTime = TimeUtils.getTimeNt();

        int cores = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(cores);
        List<Future<PerftResult>> futures = new ArrayList<>();

        for (MoveInfo moveInfo : moveList) {
            chessGame.makeMove(moveInfo);

            final ChessGame clonedGame = ChessGame.lightWeightCopy(chessGame);

            chessGame.unmakeMove();

            String moveStr = moveInfo.toString();

            Callable<PerftResult> task = () -> {
                long branchNodes = perftDriver(clonedGame, depth - 1);
                return new PerftResult(moveStr, branchNodes);
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
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.setAutoChangingGameOver(false);

        System.out.println("Preheating...");
        perftDriver(chessGame, 5);
        System.out.println("Preheating complete!");

        chessGame = ChessGame.startPosition();
        chessGame.setAutoChangingGameOver(false);

        perftTest(chessGame, 6);
    }
}

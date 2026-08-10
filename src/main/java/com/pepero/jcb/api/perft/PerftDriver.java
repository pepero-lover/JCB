package com.pepero.jcb.api.perft;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.constant.MoveCache;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.GameVariants;
import com.pepero.jcb.core.MoveGenerator;
import com.pepero.jcb.encode.EncodeMove;
import com.pepero.jcb.util.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PerftDriver {
    public static void apiWarmup(boolean silent) {
        List<Long> recentNps = new ArrayList<>();
        int depth = 4;

        long nps;
        double stableValue;

        if(!silent) System.out.println("Preheating...");

        while (true) {
            long start = System.nanoTime();
            long nodes = perftAPIDriver(ChessGame.startPosition(), depth);
            long elapsed = System.nanoTime() - start;
            nps = nodes * 1_000_000_000L / elapsed;

            recentNps.add(nps);
            stableValue = getStableValue(recentNps);

            if (recentNps.size() > 5) recentNps.removeFirst();
            if (recentNps.size() == 5 && stableValue < 0.03) break;
        }

        if(!silent) System.out.println("Preheat Complete. ( nps : " + nps + " +- " +
                String.format("%.2f", (stableValue * 100)) + "% )");
    }

    public static void bitboardWarmup(boolean silent) {
        List<Long> recentNps = new ArrayList<>();
        int depth = 4;

        long nps;
        double stableValue;

        if(!silent) System.out.println("Preheating...");

        while (true) {
            long start = System.nanoTime();
            long nodes = perftBitboardDriver(new Chessboard(Chessboard.start_position), depth);
            long elapsed = System.nanoTime() - start;
            nps = nodes * 1_000_000_000L / elapsed;

            recentNps.add(nps);
            stableValue = getStableValue(recentNps);

            if (recentNps.size() > 5) recentNps.removeFirst();
            if (recentNps.size() == 5 && stableValue < 0.03) break;
        }

        if(!silent) System.out.println("Preheat Complete. ( nps : " + nps + " +- " +
                String.format("%.2f", (stableValue * 100)) + "% )");
    }

    private static double getStableValue(List<Long> samples) {
        double mean = samples.stream().mapToLong(Long::longValue).average().orElse(0);
        double variance = samples.stream()
                .mapToDouble(n -> Math.pow(n - mean, 2))
                .average().orElse(0);
        double stdDev = Math.sqrt(variance);
        return (stdDev / mean);
    }

    public static long perftAPIDriver(ChessGame chessGame, int depth) {
        if (depth == 0) {
            return 1;
        }

        long localNodes = 0;
        List<MoveInfo> moveList = chessGame.getLegalMoves();

        for (MoveInfo moveInfo : moveList) {
            chessGame.makeMove(moveInfo);
            localNodes += perftAPIDriver(chessGame, depth - 1);
            chessGame.unmakeMove();
        }

        return localNodes;
    }

    public static PerftResult perftAPITest(ChessGame chessGame, int depth, int concurrency, boolean silent) {
        chessGame = ChessGame.lightWeightCopy(chessGame);

        List<PerftMoveResult> moveResults = new ArrayList<>();

        List<MoveInfo> moveList = chessGame.getLegalMoves();
        long startTime = TimeUtils.getTimeNt();

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        List<Future<PerftMoveResult>> futures = new ArrayList<>();

        for (MoveInfo moveInfo : moveList) {
            chessGame.makeMove(moveInfo);

            final ChessGame clonedGame = ChessGame.lightWeightCopy(chessGame);

            chessGame.unmakeMove();

            String moveStr = moveInfo.toString();

            Callable<PerftMoveResult> task = () -> {
                long branchNodes = perftAPIDriver(clonedGame, depth - 1);
                if(!silent) System.out.println("  Move " + moveStr + "  nodes : " + branchNodes);
                return new PerftMoveResult(moveStr, branchNodes);
            };
            futures.add(executor.submit(task));
        }

        long totalNodes = 0;

        try {
            for (Future<PerftMoveResult> future : futures) {
                PerftMoveResult result = future.get();
                totalNodes += result.nodes();
                moveResults.add(result);
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

        if(!silent) {
            System.out.println();
            System.out.println("Perft test finished.");
            System.out.println();
            System.out.println("Elapsed time : " + durationMs + "ms ( + " + durationNs % 1_000_000 + "ns )");
            System.out.println("NPS : " + String.format("%.2f", nps / 1_000_000.) + "MNPS ( " + nps + " nps )");
        }

        return new PerftResult(
                moveResults,
                totalNodes,
                durationMs,
                nps
        );
    }

    public static long perftBitboardDriver(Chessboard chessboard, int depth) {
        if (depth == 0) {
            return 1;
        }

        long nodes = 0;
        int[] moveList = MoveCache.SEARCH_MOVE_CACHE.get()[chessboard.ply];
        int moveCount = MoveGenerator.generateMoves(chessboard, moveList);

        for (int i = 0; i < moveCount; i++) {
            int move = moveList[i];

            MoveGenerator.makeMove(chessboard, move);
            nodes += perftBitboardDriver(chessboard, depth - 1);
            MoveGenerator.unmakeMove(chessboard, move);
        }
        return nodes;
    }

    public static PerftResult perftBitboardTest(Chessboard chessboard, int depth, int concurrency, boolean silent) {
        int[] moveList = MoveCache.SEARCH_MOVE_CACHE.get()[chessboard.ply];
        int moveCount = MoveGenerator.generateMoves(chessboard, moveList);

        List<PerftMoveResult> moveResults = new ArrayList<>();

        long startTime = TimeUtils.getTimeNt();

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        List<Future<PerftMoveResult>> futures = new ArrayList<>();

        for (int i = 0; i < moveCount; i++) {
            int move = moveList[i];

            MoveGenerator.makeMove(chessboard, move);

            final Chessboard clonedBoard = new Chessboard(chessboard);

            MoveGenerator.unmakeMove(chessboard, move);

            String finalMoveStr = EncodeMove.moveToString(move,
                    chessboard.gameVariants == GameVariants.CHESS960);
            Callable<PerftMoveResult> task = () -> {
                long branchNodes = perftBitboardDriver(clonedBoard, depth - 1);
                return new PerftMoveResult(finalMoveStr, branchNodes);
            };

            futures.add(executor.submit(task));
        }

        long totalNodes = 0;

        try {
            for (Future<PerftMoveResult> future : futures) {
                PerftMoveResult result = future.get();
                totalNodes += result.nodes();
                if(!silent) System.out.println("  Move " + result.moveStr() + "  nodes : " + result.nodes());
                moveResults.add(result);
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

        if(!silent) {
            System.out.println();
            System.out.println("Perft test finished.");
            System.out.println();
            System.out.println("Elapsed time : " + durationMs + "ms ( + " + durationNs % 1_000_000 + "ns )");
            System.out.println("NPS : " + String.format("%.2f", nps / 1_000_000.) + "MNPS ( " + nps + " nps )");
        }

        return new PerftResult(
                moveResults,
                totalNodes,
                durationMs,
                nps
        );
    }
}

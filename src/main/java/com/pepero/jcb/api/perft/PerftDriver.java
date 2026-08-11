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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PerftDriver {
    public static void apiExecutorWarmup(ExecutorService service, int concurrency, boolean silent) {
        if (!silent) System.out.println("Preheating...");

        List<Future<Void>> warmupFutures = new ArrayList<>();

        for (int i = 0; i < concurrency; i++) {
            warmupFutures.add(service.submit(() -> {
                List<Long> recentNps = new ArrayList<>();
                int depth = 4;
                double stableValue;

                while (true) {
                    long start = System.nanoTime();
                    long nodes = perftAPIDriver(ChessGame.startPosition(), depth);
                    long elapsed = System.nanoTime() - start;
                    long nps = nodes * 1_000_000_000L / elapsed;

                    recentNps.add(nps);
                    stableValue = getStableValue(recentNps);

                    if (recentNps.size() > 5) recentNps.removeFirst();
                    if (recentNps.size() == 5 && stableValue < 0.03) break;
                }

                return null;
            }));
        }

        for (Future<Void> f : warmupFutures) {
            try {
                f.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (!silent) System.out.println("Preheat Complete.");
    }

    private static void bitboardExecutorWarmup(ExecutorService service, int concurrency, boolean silent) {
        if (!silent) System.out.println("Preheating...");

        List<Future<Void>> warmupFutures = new ArrayList<>();

        for (int i = 0; i < concurrency; i++) {
            warmupFutures.add(service.submit(() -> {
                List<Long> recentNps = new ArrayList<>();
                int depth = 5;
                double stableValue;

                while (true) {
                    long start = System.nanoTime();
                    long nodes = perftBitboardDriver(new Chessboard(Chessboard.start_position), depth);
                    long elapsed = System.nanoTime() - start;
                    long nps = nodes * 1_000_000_000L / elapsed;

                    recentNps.add(nps);
                    stableValue = getStableValue(recentNps);

                    if (recentNps.size() > 5) recentNps.removeFirst();
                    if (recentNps.size() == 5 && stableValue < 0.03) break;
                }

                return null;
            }));
        }

        for (Future<Void> f : warmupFutures) {
            try {
                f.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (!silent) System.out.println("Preheat Complete.");
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

        List<MoveInfo> moveList = chessGame.getLegalMoves();

        List<ChessGame> clonedGames = new ArrayList<>(moveList.size());
        List<String> moveStrs = new ArrayList<>(moveList.size());

        for (MoveInfo moveInfo : moveList) {
            chessGame.makeMove(moveInfo);
            clonedGames.add(ChessGame.lightWeightCopy(chessGame));
            chessGame.unmakeMove();
            moveStrs.add(moveInfo.toString());
        }

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        apiExecutorWarmup(executor, concurrency, silent);

        List<PerftMoveResult> moveResults = new ArrayList<>();
        List<Future<PerftMoveResult>> futures = new ArrayList<>();

        long startTime = TimeUtils.getTimeNt();

        for (int i = 0; i < clonedGames.size(); i++) {
            final ChessGame clonedGame = clonedGames.get(i);
            final String moveStr = moveStrs.get(i);
            futures.add(executor.submit(() -> {
                long branchNodes = perftAPIDriver(clonedGame, depth - 1);
                return new PerftMoveResult(moveStr, branchNodes);
            }));
        }

        long totalNodes = 0;
        try {
            for (Future<PerftMoveResult> future : futures) {
                PerftMoveResult result = future.get();
                totalNodes += result.nodes();
                if (!silent) System.out.println("  Move " + result.moveStr() + "  nodes : " + result.nodes());
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

        List<Chessboard> clonedBoards = new ArrayList<>(moveCount);
        List<String> moveStrs = new ArrayList<>(moveCount);

        for (int i = 0; i < moveCount; i++) {
            int move = moveList[i];
            MoveGenerator.makeMove(chessboard, move);
            clonedBoards.add(new Chessboard(chessboard));
            MoveGenerator.unmakeMove(chessboard, move);
            moveStrs.add(EncodeMove.moveToString(move, chessboard.gameVariants == GameVariants.CHESS960));
        }

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        bitboardExecutorWarmup(executor, concurrency, silent);

        List<PerftMoveResult> moveResults = new ArrayList<>();
        List<Future<PerftMoveResult>> futures = new ArrayList<>();

        long startTime = TimeUtils.getTimeNt();

        for (int i = 0; i < moveCount; i++) {
            final Chessboard clonedBoard = clonedBoards.get(i);
            final String moveStr = moveStrs.get(i);
            futures.add(executor.submit(() -> {
                long branchNodes = perftBitboardDriver(clonedBoard, depth - 1);
                return new PerftMoveResult(moveStr, branchNodes);
            }));
        }

        long totalNodes = 0;
        try {
            for (Future<PerftMoveResult> future : futures) {
                PerftMoveResult result = future.get();
                totalNodes += result.nodes();
                if (!silent) System.out.println("  Move " + result.moveStr() + "  nodes : " + result.nodes());
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

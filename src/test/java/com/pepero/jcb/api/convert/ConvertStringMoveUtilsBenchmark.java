package com.pepero.jcb.api.convert;

import com.pepero.jcb.api.parse.ConvertStringMoveUtils;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.MoveGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ConvertStringMoveUtilsBenchmark {
    public static void main(String[] args) throws IOException {
        boolean lanMode = true;

        List<String> games = Files.readAllLines(Path.of("random_games_benchmark.txt"))
                .stream()
                .filter(line -> !line.isBlank())
                .toList();

        System.out.println("Loaded " + games.size() + " games for benchmarking.");

        // Warmup
        System.out.println("Warming up JIT...");
        for (int i = 0; i < 20; i++) {
            runAllGames(games, lanMode);
        }

        // Measure
        int repeatCount = 100;
        long checksum = 0;
        int totalConversions = 0;

        long start = System.nanoTime();
        for (int r = 0; r < repeatCount; r++) {
            for (String gameMove : games) {
                Chessboard board = new Chessboard(Chessboard.start_position);
                for (String move : gameMove.trim().split("\\s+")) {
                    int moveData;
                    if(lanMode) {
                        moveData = ConvertStringMoveUtils.lanToMoveData(board, move);
                    } else {
                        moveData = ConvertStringMoveUtils.sanToMoveData(board, move);
                    }
                    checksum += moveData;
                    MoveGenerator.makeMove(board, moveData);
                    totalConversions++;
                }
            }
        }
        long elapsedNanos = System.nanoTime() - start;

        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
        double perSecond = totalConversions / elapsedSeconds;

        System.out.println("Total conversions: " + totalConversions);
        System.out.println("Elapsed: " + String.format("%.3f", elapsedSeconds) + "s");
        System.out.println("Throughput: " + String.format("%.0f", perSecond) + " conversions/sec");
        System.out.println("Checksum (ignore): " + (checksum == Long.MIN_VALUE ? "unreachable" : "ok"));
    }

    private static void runAllGames(List<String> games, boolean lanMode) {
        for (String gameSan : games) {
            Chessboard board = new Chessboard(Chessboard.start_position);
            for (String move : gameSan.trim().split("\\s+")) {
                int moveData;
                if(lanMode) {
                    moveData = ConvertStringMoveUtils.lanToMoveData(board, move);
                } else {
                    moveData = ConvertStringMoveUtils.sanToMoveData(board, move);
                }
                MoveGenerator.makeMove(board, moveData);
            }
        }
    }
}
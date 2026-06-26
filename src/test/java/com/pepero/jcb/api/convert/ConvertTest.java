package com.pepero.jcb.api.convert;

import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.MoveGenerator;

import java.util.Random;

public class ConvertTest {
    public static void main(String[] args) {
        Random random = new Random(2026);
        int totalGamesToSimulate = 50_000; // game count to simulate

        System.out.println("Game count : " + totalGamesToSimulate);

        playRandomGames(new Random(41), 2000);

        System.gc();
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}

        long startTime = System.nanoTime();

        long totalConversions = playRandomGames(random, totalGamesToSimulate);

        long endTime = System.nanoTime();
        long durationNs = endTime - startTime;
        double durationMs = durationNs / 1_000_000.0;

        System.out.println("\n=== Test result ===");
        System.out.printf("Moves count: %,d\n", totalConversions);
        System.out.printf("Time: %,.2f ms\n", durationMs);
        System.out.printf("Convert move per second: %,d 회/초\n", (long)(totalConversions / (durationMs / 1000.0)));
    }

    private static long playRandomGames(Random random, int gameCount) {
        long conversionCount = 0;
        int[] moveList = new int[255];

        for (int i = 0; i < gameCount; i++) {
            Chessboard board = new Chessboard(Chessboard.start_position);

            for (int ply = 0; ply < 150; ply++) {
                int moveCount = MoveGenerator.generateMoves(board, moveList);
                if (moveCount == 0) break;

                for (int m = moveCount - 1; m > 0; m--) {
                    int index = random.nextInt(m + 1);
                    int temp = moveList[m];
                    moveList[m] = moveList[index];
                    moveList[index] = temp;
                }

                boolean isLegalMoveFound = false;

                for (int m = 0; m < moveCount; m++) {
                    int moveData = moveList[m];

                    if (MoveGenerator.makeMove(board, moveData)) {
                        isLegalMoveFound = true;
                        conversionCount++;
                        break;
                    }
                }

                if (!isLegalMoveFound) break;
            }
        }

        return conversionCount;
    }
}

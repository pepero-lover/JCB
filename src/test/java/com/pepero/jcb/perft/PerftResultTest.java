package com.pepero.jcb.perft;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.perft.PerftDriver;
import com.pepero.jcb.api.perft.PerftResult;
import com.pepero.jcb.core.Chessboard;

import java.util.ArrayList;
import java.util.List;

public class PerftResultTest {
    public static void main(String[] args) {
        Chessboard chessboard = new Chessboard(Chessboard.start_position);
        ChessGame chessGame = ChessGame.startPosition();

        int available_processor = Runtime.getRuntime().availableProcessors();

        System.out.println("Available Processors : " + available_processor);

        int averageCount = 3;

        System.out.println();
        System.out.println();
        System.out.println("---- BITBOARD TEST ---- ");
        System.out.println();
        System.out.println();


        for(int thread : new int[]{1,2,4,8}) {
            for (int depth : new int[]{6,7}) {
                if(available_processor < thread) break;

                // nps 의 평균을 구합니다.
                List<Long> nps = new ArrayList<>();
                for(int i=1;i<=averageCount;i++) {
                    PerftResult result =
                            PerftDriver.perftBitboardTest(chessboard, depth, thread, true, false);
                    nps.add(result.nps());
                    System.out.println("Calculated perft(" + depth + ") with " + thread + " thread(s) (" + i + "/" + averageCount + ")");
                }
                double npsAverage = nps.stream().mapToLong(Long::longValue)
                        .average()
                        .orElse(0.0);


                System.out.println();
                System.out.println();
                System.out.println("Calculated perft(" + depth + ") * " + averageCount
                        + " with " + thread + " thread(s)");
                System.out.println("Average NPS : " + String.format(
                        "%.2f", npsAverage / 1_000_000.
                ) + "MNPS ( " +
                        (long) npsAverage + "nps )");
                System.out.println();
            }
        }
        System.out.println();
        System.out.println();
        System.out.println("---- API TEST ---- ");
        System.out.println();
        System.out.println();

        for(int thread : new int[]{1,2,4,8}) {
            for (int depth : new int[]{5,6}) {
                if(available_processor < thread) break;

                List<Long> nps = new ArrayList<>();
                for(int i=1;i<=averageCount;i++) {
                    PerftResult result =
                            PerftDriver.perftAPITest(chessGame, depth, thread, true, false);
                    nps.add(result.nps());
                    System.out.println("Calculated perft(" + depth + ") with " + thread + " thread(s) (" + i + "/" + averageCount + ")");
                }
                double npsAverage = nps.stream().mapToLong(Long::longValue)
                        .average()
                        .orElse(0.0);


                System.out.println();
                System.out.println();
                System.out.println("Calculated perft(" + depth + ") * " + averageCount
                        + " with " + thread + " thread(s)");
                System.out.println("Average NPS : " + String.format(
                        "%.2f", npsAverage / 1_000_000.
                ) + "MNPS ( " +
                        (long) npsAverage + "nps )");
                System.out.println();
            }
        }
    }
}
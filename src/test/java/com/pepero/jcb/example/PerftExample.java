package com.pepero.jcb.example;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.perft.PerftDriver;
import com.pepero.jcb.core.Chessboard;

public class PerftExample {
    public static void main(String[] args) {
        String fen = ChessGame.START_POSITION;

        ChessGame chessGame = ChessGame.fromFEN(fen);

        System.out.println("--------------------");
        System.out.println("Perft API 1 threads");
        System.out.println("--------------------");

        // Run Perft and store the result.

        // As noted in the Javadoc, perft(int depth) is single-threaded and includes JVM warmup.
        chessGame.perft(5);

        // Now let's see the result with 4 threads.
        System.out.println();
        System.out.println("--------------------");
        System.out.println("Perft API 4 threads");
        System.out.println("--------------------");

        chessGame.perft(
                6, // Perft depth
                4 // Number of threads to use
        );

        // Now let's run Perft using Chessboard instead.
        Chessboard chessboard = new Chessboard(fen);

        System.out.println();
        System.out.println("-------------------------");
        System.out.println("Perft Bitboard 1 threads");
        System.out.println("-------------------------");

        PerftDriver.perftBitboardTest(
                chessboard,
                6, // Perft depth
                1, // Number of threads to use
                false, // Whether to suppress the test result and output
                false // Whether to use bulk counting
        );

        System.out.println();
        System.out.println("-------------------------");
        System.out.println("Perft Bitboard 4 threads");
        System.out.println("-------------------------");

        PerftDriver.perftBitboardTest(
                chessboard,
                7, // Perft depth
                4, // Number of threads to use
                false, // Whether to suppress the test result and output
                false // Whether to use bulk counting
        );
    }
}

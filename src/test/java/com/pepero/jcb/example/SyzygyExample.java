package com.pepero.jcb.example;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.SyzygyAnalyzer;
import com.pepero.jcb.api.syzygy.SyzygyTablebase;
import com.pepero.jcb.api.dto.SyzygyMoveDTO;

import java.io.IOException;
import java.nio.file.Path;

public class SyzygyExample {

    public static void main(String[] args) throws IOException {
        // Get the tablebase directory.
        Path syzygyDir = Path.of("syzygy/");
        // Load the tablebase.
        SyzygyTablebase tb = new SyzygyTablebase(syzygyDir);

        // Example position
        ChessGame game = ChessGame.fromFEN("8/8/4r3/3k4/8/8/2Q5/7K w - - 0 1");

        game.printBoard();

        // Show the WDL and DTZ results.

        // WDL values range from -2 to 2 (from the side to move's perspective).
        //  2 : a win with perfect play
        //  1 : a win, but drawn under the 50-move rule
        //  0 : a draw
        // -1 : a loss, but drawn under the 50-move rule
        // -2 : a loss with perfect play

        // DTZ tells you how many moves remain until a pawn move or capture.
        // If this position is lost (if wdl is negative), DTZ is negative.

        System.out.println("WDL : " + SyzygyAnalyzer.probeWdl(game, tb));
        System.out.println("DTZ : " + SyzygyAnalyzer.probeDtz(game, tb));
        System.out.println();

        // You can also find the best move in the current position.
        System.out.println("Syzygy best move : " + SyzygyAnalyzer.findBestMove(game, tb));
        System.out.println();

        // There's also a method that shows the WDL/DTZ results for every available move.
        for(SyzygyMoveDTO move : SyzygyAnalyzer.findRankedMoves(game, tb)) {
            System.out.println(move.move() + "  WDL" + move.ourWdl() + "  DTZ" + move.distance());
        }
    }
}

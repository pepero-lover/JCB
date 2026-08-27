package com.pepero.jcb.example;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.GaviotaAnalyzer;
import com.pepero.jcb.api.gaviota.GaviotaTablebase;
import com.pepero.jcb.api.dto.GaviotaMoveDTO;

import java.nio.file.Path;

public class GaviotaExample {

    public static void main(String[] args) {
        // Get the tablebase directory.
        Path gaviotaDir = Path.of("gaviota/");
        // Load the tablebase.
        GaviotaTablebase tb = new GaviotaTablebase(gaviotaDir);

        // Example position
        ChessGame game = ChessGame.fromFEN("8/8/4r3/3k4/8/8/2Q5/7K w - - 0 1");

        game.printBoard();

        // Show the WDL and DTM results.

        // WDL values range from -1 to 1 (from the side to move's perspective).
        //  1 : a win
        //  0 : a draw
        // -1 : a loss

        // DTM tells you the distance to mate, in half-moves.
        // If this position is lost (if wdl is negative), DTM is negative too.

        System.out.println("WDL : " + GaviotaAnalyzer.probeWdl(game, tb));
        System.out.println("DTM : " + GaviotaAnalyzer.probeDtm(game, tb));
        System.out.println();

        // You can also find the best move in the current position.
        System.out.println("Gaviota best move : " + GaviotaAnalyzer.findBestMove(game, tb));
        System.out.println();

        // There's also a method that shows the WDL/DTM results for every available move.
        for (GaviotaMoveDTO move : GaviotaAnalyzer.findRankedMoves(game, tb)) {
            System.out.println(move.move() + "  WDL" + move.ourWdl() + "  DTM" + move.distance());
        }
    }
}

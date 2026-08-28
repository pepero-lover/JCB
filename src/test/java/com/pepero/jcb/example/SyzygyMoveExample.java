package com.pepero.jcb.example;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.SyzygyAnalyzer;
import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.api.syzygy.SyzygyTablebase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;

public class SyzygyMoveExample {
    // draw if half move is more than or equals to 50
    private static boolean drawFiftyMoves = false;

    // Example positions
    //
    // (max pieces : 5 pieces)
    // "8/8/8/8/1p2P3/4P3/1k6/3K4 w - - 0 1" KPPvKP (longest sequence on 5 pieces)
    // "8/8/2k5/1r6/8/8/8/2KQ4 b - - 0 1"    KQvKR
    // "4k3/8/8/8/8/8/4P3/4K3 w - - 0 1"     KPvK

    public static void main(String[] args) throws IOException {
        // First, get syzygy dir.
        Path syzygyDir = Path.of("syzygy/");

        // Load syzygy tablebase class.
        SyzygyTablebase tb = new SyzygyTablebase(
                syzygyDir,
                5 // Max pieces which this tablebase supports (max : 7)
        );

        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));

        // Initialize chess game class with input fen string
        System.out.print("Please enter FEN string : ");
        ChessGame game = ChessGame.fromFEN(input.readLine());


        System.out.println();
        // Print WDL, DTZ data
        System.out.println("WDL : " + SyzygyAnalyzer.probeWdl(game, tb));
        System.out.println("DTZ : " + SyzygyAnalyzer.probeDtz(game, tb));

        // ply counter
        int ply = 1;

        while (true) {
            // Get best move info
            MoveInfo bestMove = SyzygyAnalyzer.findBestMove(game, tb);

            // Get whether this game ended
            GameOverReason reason = game.isGameOver();

            // if not ended,
            if(reason != GameOverReason.NOTGAMEOVER) {
                if(drawFiftyMoves) {
                    System.out.println("Game over by " + reason.toString().toLowerCase());
                    break;
                } else {
                    if(reason != GameOverReason.FIFTYMOVES_CLAIM) {
                        System.out.println("Game over by " + reason.toString().toLowerCase());
                        break;
                    }
                }
            }

            // Make move the best move chosen by Syzygy tablebase
            game.makeMove(bestMove);

            // Print this board
            game.printBoard();

            // Print info
            System.out.println("Best move : " + bestMove);
            System.out.println("Ply : " + ply);
            System.out.println();
            System.out.println("WDL : " + SyzygyAnalyzer.probeWdl(game, tb));
            System.out.println("DTZ : " + SyzygyAnalyzer.probeDtz(game, tb));
            System.out.println("FEN : " + game.getFEN());
            System.out.println();

            ply++;
        }
    }
}

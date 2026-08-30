package com.pepero.jcb.example;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.enums.GameOverReason;

public class GameStateExample {
    public static void main(String[] args) {
        // A game can be started from a FEN string.
        // Example: a position right before checkmate
        String scholarMateFen = "r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4";
        ChessGame game = ChessGame.fromFEN(scholarMateFen);

        // Assume White delivers checkmate.
        game.makeMoveLan("h5f7"); // e4 e5 Qh5 Nc6 Bc4 Nf6 Qxf7#

        // Check whether the game has ended
        GameOverReason reason = game.isGameOver();
        if (reason != GameOverReason.NOTGAMEOVER) {
            System.out.println("Game over! Reason: " + reason);
        }

        // Individual state checks are also available.
        System.out.println("Is it checkmate? : " + game.isCheckmate());
        System.out.println("Is it check? : " + game.isCheck());
    }
}

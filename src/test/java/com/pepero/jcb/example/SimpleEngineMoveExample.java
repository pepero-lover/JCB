package com.pepero.jcb.example;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.enums.GameOverReason;

public class SimpleEngineMoveExample {
    public static void main(String[] args) {
        // Note : there isn't a piece position evaluation but only a piece material count on evaluation.
        // so this engine goes bongcloud, and goes pawn to a2a3, b2b3, c2c3

        ChessGame chessGame = ChessGame.startPosition();

        while (chessGame.isGameOver() == GameOverReason.NOTGAMEOVER) {
            int[] output = SimpleEngine.search(chessGame.getBoardSnapshot(), 6);
            chessGame.makeMove(output[1]);
            chessGame.setCurrentMoveEval(String.valueOf(output[0] / 100.));
            chessGame.printBoard();
        }

        System.out.println(chessGame.isGameOver());
        System.out.println(chessGame.getPGN());
        System.out.println();
    }
}
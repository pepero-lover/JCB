package com.pepero.jcb.example;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.api.enums.GameResult;

import java.util.List;
import java.util.Random;

public class SimpleEngineMoveExample {
    public static void main(String[] args) {
        int count = 1;
        for (int i = 0; i < count; i++) {
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
}
package com.pepero.jcb.example;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.api.enums.GameResult;
import com.pepero.jcb.api.ChessGameListener;

public class ListenerExample {
    public static void main(String[] args) {
        ChessGame chessGame = ChessGame.startPosition();

        chessGame.addChessGameListener(new ChessGameListener() {
            @Override
            public void onGameOver(GameResult result, GameOverReason reason) {
                System.out.println(result);
                System.out.println(reason);
            }

            @Override
            public void onHistoryChanged() {
                System.out.println("The move history has changed.");
            }
        });

        chessGame.printBoard();
        chessGame.makeMoveSanAll("e4 e5 Qh5 Nc6 Bc4 Nf6 Qxf7#");
        chessGame.printBoard();
    }
}

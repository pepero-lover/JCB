package com.pepero.jcb.example;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.api.enums.GameResult;
import com.pepero.jcb.api.event.ChessGameListener;

public class ListenerExample {
    public static void main(String[] args) {
        ChessGame chessGame = new ChessGame();

        chessGame.addChessGameListener(new ChessGameListener() {
            @Override
            public void onGameOver(GameResult result, GameOverReason reason) {
                System.out.println(result);
                System.out.println(reason);
            }

            @Override
            public void onHistoryChanged() {
                System.out.println("기보가 변경되었습니다.");
            }
        });

        chessGame.printBoard();
        chessGame.makeMoveSanAll("e4 e5 Qh5 Nc6 Bc4 Nf6 Qxf7#");
        chessGame.printBoard();
    }
}

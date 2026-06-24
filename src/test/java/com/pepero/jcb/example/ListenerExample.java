package com.pepero.jcb.example;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.api.enums.GameResult;
import com.pepero.jcb.api.event.ChessGameListener;

public class ListenerExample {
    public static void main(String[] args) {
        ChessGame chessGame = new ChessGame();

        chessGame.addChessGameListener(new ChessGameListener() {
            @Override
            public void onMoveMade(MoveInfo moveInfo) {

            }

            @Override
            public void onMoveUnmade(MoveInfo unmadeMoveInfo) {

            }

            @Override
            public void onMoveRemade(MoveInfo remadeMoveInfo) {

            }

            @Override
            public void onPositionJumped(String targetFen) {

            }

            @Override
            public void onGameOver(GameResult result, GameOverReason reason) {
                System.out.println(result);
                System.out.println(reason);
            }
        });

        chessGame.printBoard();
        chessGame.makeMoveSanAll("e4 e5 Qh5 Nc6 Bc4 Nf6 Qxf7#");
        chessGame.printBoard();
    }
}

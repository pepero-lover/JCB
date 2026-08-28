package com.pepero.jcb.example;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.core.chess960.Chess960Utils;

public class Chess960Example {
    public static void main(String[] args) {
        // Make chess game class with chess 960 fen
        ChessGame chessGame = ChessGame.fromFEN(
                // generate chess 960 fen by index
                Chess960Utils.generate960FenByIndex(111),
                // get 111th position of chess 960 position (starts at 0, ends on 959)

                // set chess 960 option true
                true
        );

        // print chess 960 position
        chessGame.printBoard();
    }
}

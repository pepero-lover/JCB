package com.pepero.jcb.example;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.enums.PieceType;
import com.pepero.jcb.api.enums.Square;
import com.pepero.jcb.core.GameVariant;

public class CrazyhouseExample {
    public static void main(String[] args) {
        // Initialize chess game with crazyhouse variant
        ChessGame chessGame = ChessGame.startPosition(GameVariant.CRAZY_HOUSE);

        chessGame.makeMoveSanAll("e4 e5 Nf3 Nc6 Nc3 Nf6 d4 exd4 Nxd4 Nxd4 Qxd4");
        chessGame.printBoard();

        // if you want to place piece, there are several options
        // first, make move with san or lan (uci)

        // lan (uci)
        chessGame.makeMove("N@c6");
        System.out.println("UCI place knight on c6");
        chessGame.printBoard();
        chessGame.unmakeMove();

        // san
        chessGame.makeMoveSan("N@e6");
        System.out.println("SAN place knight on e6");
        chessGame.printBoard();
        chessGame.unmakeMove();

        // manual placement for like gui
        chessGame.makeDropMove(PieceType.KNIGHT, Square.b4);
        System.out.println("Manual place knight on b4");
        chessGame.printBoard();
        chessGame.unmakeMove();
    }
}

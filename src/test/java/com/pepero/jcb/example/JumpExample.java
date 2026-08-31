package com.pepero.jcb.example;

import com.pepero.jcb.api.ChessGame;

public class JumpExample {
    public static void main(String[] args) {
        ChessGame chessGame = ChessGame.startPosition();

        // Set up the position ...

        chessGame.makeMoveLan("e2e4");
        chessGame.makeMoveLan("e7e5");
        chessGame.makeMoveLan("g1f3");
        long uuid_g1f3 = chessGame.getCurrentNodeId();

        chessGame.makeMoveLan("b8c6");

        // The move history now looks like this:
        // e4 e5 Nf3 Nc6

        System.out.println("Position e4 e5 Nf3 Nc6");
        chessGame.printBoard();

        // Now let's jump to that saved node.
        chessGame.jumpToNode(uuid_g1f3);
        // This moves the game back to: e4 e5 Nf3 <-- here

        System.out.println("Position e4 e5 Nf3");
        chessGame.printBoard();
    }
}

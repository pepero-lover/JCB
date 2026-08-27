package com.pepero.jcb.example;

import com.pepero.jcb.api.ChessGame;

public class MainExample {
    public static void main(String[] args) {
        // Initialize with the default starting position.
        ChessGame chessGame = ChessGame.startPosition();

        // Make moves
        chessGame.makeMove("e2e4");
        chessGame.makeMove("e7e5");
        chessGame.makeMove("g1f3");

        // Check the current turn and FEN
        System.out.println("Current turn: " + chessGame.getTurn());
        System.out.println("Current FEN: " + chessGame.getFEN());

        // Test undo and redo
        if (chessGame.canUndo()) {
            System.out.println("Position before undo: ");
            chessGame.toAscii();
            System.out.println();

            chessGame.unmakeMove(); // undo g1f3

            System.out.println("Position after undo: ");
            chessGame.toAscii();
        }
    }
}

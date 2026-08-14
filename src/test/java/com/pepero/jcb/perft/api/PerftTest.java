package com.pepero.jcb.perft.api;

import com.pepero.jcb.api.ChessGame;

public class PerftTest {
    public static void main(String[] args) {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.perft(5,
                1,
                false,
                false
        );
    }
}
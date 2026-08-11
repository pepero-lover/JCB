package com.pepero.jcb.perft.api;

import com.pepero.jcb.api.ChessGame;

public class PerftMultiThread {
    public static void main(String[] args) {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.perft(6,
                4,
                false
        );
    }
}

package com.pepero.jcb.perft.api;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.util.TimeUtils;

import java.util.List;

public class PerftTest {
    public static void main(String[] args) {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.perft(5,
                1,
                true,
                false
        );
    }
}
package com.pepero.jcb.perft.api;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.util.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PerftMultiThread {
    public static void main(String[] args) {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.perft(5,
                Runtime.getRuntime().availableProcessors(),
                true,
                false
        );
    }
}

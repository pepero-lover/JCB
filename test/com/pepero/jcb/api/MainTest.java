package com.pepero.jcb.api;

import com.pepero.jcb.api.uci.UCIEngineWrapper;

import java.util.List;

public class MainTest {
    public static void main(String[] args) {
        UCIEngineWrapper wrapper = new UCIEngineWrapper("engines/stockfish/stockfish.exe", 100,
                new UCIEngineWrapper.EngineAnalysisListener() {
            @Override
            public void onAnalysisBundled(List<UCIEngineWrapper.EngineLine> bundledLines) {
                System.out.println(bundledLines);
            }

            @Override
            public void onBestMoveFound(String bestMove) {
                System.out.println(bestMove);
            }
        });

        ChessGame chessGame = new ChessGame();
        chessGame.makeMove("e2e4");
        chessGame.makeMove("e7e5");

        wrapper.startAnalysis(chessGame, 20, 5);
    }
}

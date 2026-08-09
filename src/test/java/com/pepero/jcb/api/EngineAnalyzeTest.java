package com.pepero.jcb.api;

import com.pepero.jcb.api.uci.UCIEngineWrapper;

import java.io.File;
import java.util.List;

public class EngineAnalyzeTest {
    public static void main(String[] args) throws InterruptedException {
        UCIEngineWrapper engineWrapper = new UCIEngineWrapper(new ProcessBuilder(
                new File("engine/stockfish").getAbsolutePath()
        ), 100, new UCIEngineWrapper.EngineAnalysisListener() {
            @Override
            public void onAnalysisBundled(List<UCIEngineWrapper.EngineLine> bundledLines) {
                System.out.println(bundledLines);
            }

            @Override
            public void onBestMoveFound(String bestMove) {

            }
        });
        ChessGame chessGame = ChessGame.startPosition();
        engineWrapper.startAnalysis(chessGame, 20, 5);
        Thread.sleep(5000);
        engineWrapper.stopAnalysis();
        chessGame.makeMove("e2e4");
        engineWrapper.startAnalysis(chessGame, 20, 5);
    }
}

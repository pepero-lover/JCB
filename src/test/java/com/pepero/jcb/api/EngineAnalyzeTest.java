package com.pepero.jcb.api;

import com.pepero.jcb.api.uci.UCIEngineWrapper;
import com.pepero.jcb.core.GameVariants;

import java.io.File;
import java.util.List;

public class EngineAnalyzeTest {
    public static void main(String[] args) throws InterruptedException {
        UCIEngineWrapper engineWrapper = new UCIEngineWrapper(new ProcessBuilder(
                new File("engine/stockfish-18.exe").getAbsolutePath()
        ), 100, new UCIEngineWrapper.EngineAnalysisListener() {
            @Override
            public void onAnalysisBundled(List<UCIEngineWrapper.EngineLine> bundledLines) {
                System.out.println(bundledLines);
            }

            @Override
            public void onBestMoveFound(String bestMove) {

            }
        });
        ChessGame chessGame = ChessGame.startPosition(GameVariants.CHESS960);
        engineWrapper.startAnalysis(chessGame, 255, 5);
        Thread.sleep(5000);
        engineWrapper.stopAnalysis();
        chessGame.makeMove("e2e4");
        engineWrapper.startAnalysis(chessGame, 255, 5);
    }
}

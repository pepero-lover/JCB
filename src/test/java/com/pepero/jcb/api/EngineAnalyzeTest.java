package com.pepero.jcb.api;

import com.pepero.jcb.api.uci.EngineAnalysisListener;
import com.pepero.jcb.api.uci.EngineLine;
import com.pepero.jcb.api.uci.UCIEngineWrapper;
import com.pepero.jcb.core.GameVariants;

import java.io.File;
import java.util.List;

public class EngineAnalyzeTest {
    public static void main(String[] args) throws InterruptedException {
        ChessGame chessGame = ChessGame.startPosition();
        UCIEngineWrapper engineWrapper = new UCIEngineWrapper(new ProcessBuilder(
                new File("engine/stockfish-18.exe").getAbsolutePath()
        ), 100, new EngineAnalysisListener() {
            @Override
            public void onAnalysisBundled(List<EngineLine> bundledLines) {
                for(EngineLine line : bundledLines) {
                    System.out.printf("Multipv %d : cp %s, pv : %s\n",
                            line.pvNumber(),
                            line.score(),
                            line.sanPv());
                }
                System.out.println();
                System.out.println();
            }

            @Override
            public void onBestMoveFound(String bestMove) {

            }
        });
        engineWrapper.startAnalysis(chessGame, 255, 5);
        Thread.sleep(5000);
        engineWrapper.stopAnalysis();
        chessGame.makeMove("e2e4");
        engineWrapper.startAnalysis(chessGame, 255, 5);
    }
}

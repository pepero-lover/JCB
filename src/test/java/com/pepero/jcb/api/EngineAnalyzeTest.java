package com.pepero.jcb.api;

import com.pepero.jcb.api.uci.EngineAnalysisListener;
import com.pepero.jcb.api.uci.EngineLine;
import com.pepero.jcb.api.uci.UCIEngineWrapper;
import com.pepero.jcb.core.GameVariants;

import java.io.File;
import java.util.List;

public class EngineAnalyzeTest {
    public static void main(String[] args) throws InterruptedException {
        ChessGame chessGame = ChessGame.fromFEN("r4rq1/5p1p/p5kn/1RB1Q1b1/6p1/5p2/PNP2P2/1K6 w - - 0 2");
        UCIEngineWrapper engineWrapper = new UCIEngineWrapper(new ProcessBuilder(
                new File("engine/stockfish-18.exe").getAbsolutePath()
        ), 100, new EngineAnalysisListener() {
            @Override
            public void onAnalysisBundled(List<EngineLine> bundledLines) {
                System.out.println("Depth : " + bundledLines.getFirst().depth());
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
    }
}

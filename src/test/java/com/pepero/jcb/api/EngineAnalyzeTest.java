package com.pepero.jcb.api;

import com.pepero.jcb.api.uci.EngineAnalysisListener;
import com.pepero.jcb.api.uci.EngineLine;
import com.pepero.jcb.api.uci.UCIEngineWrapper;
import com.pepero.jcb.core.GameVariants;

import java.io.File;
import java.util.List;

public class EngineAnalyzeTest {
    public static void main(String[] args) throws InterruptedException {
        ChessGame chessGame = ChessGame.fromFEN(
                "8/p5k1/4P3/6R1/4N2n/P7/2P4P/7K b - - 2+3 2 50",
                GameVariants.THREE_CHECK
        );

        UCIEngineWrapper engineWrapper = new UCIEngineWrapper(new ProcessBuilder(
                new File("engine/fairy-stockfish.exe").getAbsolutePath()
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

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
                "rnbq1bnr/ppppkppp/4p3/8/8/4P3/PPPPKPPP/RNBQ1BNR w - - 2 3",
                GameVariants.KING_OF_THE_HILL
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

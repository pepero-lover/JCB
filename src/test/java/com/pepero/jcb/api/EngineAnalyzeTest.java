package com.pepero.jcb.api;

import com.pepero.jcb.api.uci.*;

import java.io.File;
import java.util.List;

public class EngineAnalyzeTest {
    public static void main(String[] args) throws InterruptedException {
        ChessGame chessGame = ChessGame.fromFEN(
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        );

        UCIEngineWrapper engineWrapper = new UCIEngineWrapper(new ProcessBuilder(
                new File("engine/stockfish-19").getAbsolutePath()
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
        });
        System.out.println("Author : " + engineWrapper.getAuthor());
        System.out.println("Engine Name : " + engineWrapper.getEngineName());
        System.out.println();

        AnalysisResult result =
                engineWrapper.analyzeSync(chessGame,
                        SearchLimit.depth(24));
        System.out.println(result.bestMove());
        engineWrapper.startAnalysis(chessGame, 255, 5);
        Thread.sleep(10000);
    }
}

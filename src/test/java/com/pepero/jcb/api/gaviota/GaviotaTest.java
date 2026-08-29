package com.pepero.jcb.api.gaviota;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.GaviotaAnalyzer;
import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.api.exception.IllegalMoveException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class GaviotaTest {

    void assertGaviota(String fen) {
        ChessGame game = ChessGame.fromFEN(fen);

        GaviotaTablebase tb = new GaviotaTablebase(Path.of("gaviota/"));

        int previousDtm = GaviotaAnalyzer.probeDtm(game, tb);

//        game.printBoard();
//        System.out.println("WDL : " + GaviotaAnalyzer.probeWdl(game, tb));
//        System.out.println("DTM : " + previousDtm);

        while (true) {
            MoveInfo bestMove = GaviotaAnalyzer.findBestMove(game, tb);

            GameOverReason reason = game.isGameOver();

            if(reason != GameOverReason.NOTGAMEOVER) {
                if(reason != GameOverReason.FIFTYMOVES_CLAIM) {
                    break;
                }
            }
            if(bestMove == null) throw new IllegalMoveException("Best move is null!");

            game.makeMove(bestMove);

            int wdl = GaviotaAnalyzer.probeWdl(game, tb);
            int dtm = GaviotaAnalyzer.probeDtm(game, tb);

//            game.printBoard();
//            System.out.println("WDL : " + GaviotaAnalyzer.probeWdl(game, tb));
//            System.out.println("DTM : " + previousDtm);

            if(wdl != 0 && Math.abs(previousDtm) - 1 != Math.abs(dtm)) throw new IllegalStateException(
                    "DTZ value is weird!  " + previousDtm + " -> " + dtm);

            previousDtm = dtm;
        }

//        System.out.println();
//        System.out.println();
    }

    @Test
    @DisplayName("스탠다드 체스 Gaviota 검증")
    void gaviota() throws IOException {
        List<String> testCases = List.of(
                "8/8/8/8/1p2P3/4P3/1k6/3K4 w - - 0 1",
                "4k3/8/8/8/8/8/1BBB4/4K3 w - - 0 1",
                "8/4B3/8/8/8/8/4B3/K1k5 b - - 0 1",
                "K7/N7/k7/8/3p4/8/N7/8 w - - 0 1"
        );

        for(String fen : testCases){
            assertGaviota(fen);
        }
    }
}

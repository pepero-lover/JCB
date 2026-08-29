package com.pepero.jcb.api.syzygy;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.SyzygyAnalyzer;
import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.api.enums.PieceType;
import com.pepero.jcb.api.exception.IllegalMoveException;
import com.pepero.jcb.core.GameVariant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class SyzygyTest {
    void assertSyzygy(String fen, GameVariant variant) throws IOException {
        ChessGame game = ChessGame.fromFEN(fen, variant);

        Path path = Path.of(
                switch (variant) {
                    case STANDARD -> "syzygy/";
                    case SUICIDE, GIVEAWAY -> "syzygy-antichess/";
                    case ATOMIC -> "syzygy-atomic/";
                    default -> "";
                }
        );

        SyzygyTablebase tb = new SyzygyTablebase(path, variant);

        int previousDtz = SyzygyAnalyzer.probeDtz(game, tb);

        while (true) {
            MoveInfo bestMove = SyzygyAnalyzer.findBestMove(game, tb);

            GameOverReason reason = game.isGameOver();

            if(reason != GameOverReason.NOTGAMEOVER) {
                if(reason != GameOverReason.FIFTYMOVES_CLAIM) {
                    break;
                }
            }
            if(bestMove == null) throw new IllegalMoveException("Best move is null!");

            game.makeMove(bestMove);

            int dtz = SyzygyAnalyzer.probeDtz(game, tb);

//            game.printBoard();
//            System.out.println("WDL : " + SyzygyAnalyzer.probeWdl(game, tb));
//            System.out.println("DTZ : " + dtz);
//            System.out.println("FEN : " + game.getFEN());

            if(!bestMove.capture() && !bestMove.enpassant() && bestMove.pieceType().getPieceTypeEnum() != PieceType.PAWN) {
                if(Math.abs(Math.abs(previousDtz) - Math.abs(dtz)) > 2) throw new IllegalStateException("DTZ value is weird!");
            }

            previousDtz = dtz;
        }

//        System.out.println();
//        System.out.println();
    }

    @Test
    @DisplayName("스탠다드 체스 Syzygy 검증")
    void syzygyStandard() throws IOException {
        List<String> testCases = List.of(
                "8/8/8/8/1p2P3/4P3/1k6/3K4 w - - 0 1",
                "4k3/8/8/8/8/8/1BBB4/4K3 w - - 0 1",
                "8/4B3/8/8/8/8/4B3/K1k5 b - - 0 1",
                "K7/N7/k7/8/3p4/8/N7/8 w - - 0 1"
        );

        for(String fen : testCases){
            assertSyzygy(fen, GameVariant.STANDARD);
        }
    }

    @Test
    @DisplayName("Suicide Syzygy 검증")
    void syzygyAntichess() throws IOException {
        List<String> testCases = List.of(
                "K7/6p1/8/8/8/8/8/1q6 w - - 0 8",
                "8/3r4/2b5/1b6/8/4B3/5B2/8 w - - 0 2"
        );

        for(String fen : testCases){
            assertSyzygy(fen, GameVariant.SUICIDE);
        }
    }
}

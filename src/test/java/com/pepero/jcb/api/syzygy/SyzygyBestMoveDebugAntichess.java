package com.pepero.jcb.api.syzygy;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.SyzygyAnalyzer;
import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.core.GameVariant;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;

public class SyzygyBestMoveDebugAntichess {
    public static void main(String[] args) throws IOException {
        Path syzygyDir = Path.of("syzygy-antichess/");
        SyzygyTablebase tb = new SyzygyTablebase(syzygyDir, 5, GameVariant.SUICIDE);

        BufferedReader bf = new BufferedReader(new InputStreamReader(System.in));

        ChessGame game = ChessGame.fromFEN("4k3/8/3P1p2/8/8/8/8/4K3 b - - 0 1", GameVariant.SUICIDE);
        //ChessGame game = ChessGame.fromFEN(bf.readLine(), GameVariant.SUICIDE);
        System.out.println("First WDL" + SyzygyAnalyzer.probeWdl(game, tb));
        System.out.println("First DTZ" + SyzygyAnalyzer.probeDtz(game, tb));

        int ply = 0;

        boolean drawFiftyMoves = false;

        while (true) {
            MoveInfo bestMove = SyzygyAnalyzer.findBestMove(game, tb);

            GameOverReason reason = game.isGameOver();

            if(reason != GameOverReason.NOTGAMEOVER) {
                if(drawFiftyMoves) {
                    System.out.println(reason);
                    break;
                } else {
                    if(reason != GameOverReason.FIFTYMOVES_CLAIM) {
                        System.out.println(reason);
                        break;
                    }
                }
            }
            if(bestMove == null) break;

            game.makeMove(bestMove);
            game.printBoard();
            System.out.println(bestMove);
            System.out.println("ply : " + ply);

            System.out.println("WDL : " + SyzygyAnalyzer.probeWdl(game, tb));
            System.out.println("DTZ : " + SyzygyAnalyzer.probeDtz(game, tb));
            System.out.println("FEN : " + game.getFEN());

            ply++;
        }
    }
}
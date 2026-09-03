package com.pepero.jcb.api.gaviota;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.GaviotaAnalyzer;
import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.enums.GameOverReason;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;

public class GaviotaBestMove {
    public static void main(String[] args) throws IOException {
        Path syzygyDir = Path.of("gaviota/");
        GaviotaTablebase tb = new GaviotaTablebase(syzygyDir);

        BufferedReader bf = new BufferedReader(new InputStreamReader(System.in));

        //ChessGame game = ChessGame.fromFEN("8/8/8/8/1p2P3/4P3/1k6/3K4 w - - 0 1");
        ChessGame game = ChessGame.fromFEN(bf.readLine());
        System.out.println("First WDL" + GaviotaAnalyzer.probeWdl(game, tb));
        System.out.println("First DTM" + GaviotaAnalyzer.probeDtm(game, tb));

        int ply = 1;

        boolean drawFiftyMoves = false;

        while (true) {
            MoveInfo bestMove = GaviotaAnalyzer.findBestMove(game, tb);

            GameOverReason reason = game.isGameOver();

            if(reason != GameOverReason.NOTGAMEOVER) {
                if(drawFiftyMoves) {
                    System.out.println(reason);
                    break;
                } else {
                    if(reason != GameOverReason.FIFTYMOVES_CLAIM
                            && reason != GameOverReason.SEVENTYFIVE_MOVES) {
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

            System.out.println("WDL : " + GaviotaAnalyzer.probeWdl(game, tb));
            System.out.println("DTM : " + GaviotaAnalyzer.probeDtm(game, tb));
            System.out.println("FEN : " + game.getFEN());

            ply++;
        }
    }
}
package com.pepero.jcb.api.syzygy;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.enums.GameOverReason;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;

public class SyzygyBestMoveDebug {
    public static void main(String[] args) throws IOException {
        Path syzygyDir = Path.of("syzygy/");
        SyzygyTablebase tb = new SyzygyTablebase(syzygyDir, 6);

        BufferedReader bf = new BufferedReader(new InputStreamReader(System.in));

        //ChessGame game = ChessGame.fromFEN("8/8/8/8/1p2P3/4P3/1k6/3K4 w - - 0 1");
        ChessGame game = ChessGame.fromFEN(bf.readLine());
        System.out.println("First WDL" + game.probeSyzygyWdl(tb));
        System.out.println("First DTZ" + game.probeSyzygyDtz(tb));

        int ply = 0;

        boolean drawFiftyMoves = false;

        while (true) {
            MoveInfo bestMove = game.findBestMoveSyzygy(tb);

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

            System.out.println("WDL : " + game.probeSyzygyWdl(tb));
            System.out.println("DTZ : " + game.probeSyzygyDtz(tb));
            System.out.println("FEN : " + game.getFEN());

            ply++;
        }
    }
}
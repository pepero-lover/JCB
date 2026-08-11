package com.pepero.jcb.api.syzygy;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.encode.EncodeMove;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.pepero.jcb.constant.EncodedPieces.*;

public class SyzygyBestMoveDebug {
    public static void main(String[] args) throws IOException {
        Path syzygyDir = Path.of("syzygy/");
        System.out.println(syzygyDir.toAbsolutePath());
        SyzygyTablebase tb = new SyzygyTablebase(syzygyDir, 6);

        BufferedReader bf = new BufferedReader(new InputStreamReader(System.in));

        ChessGame game = ChessGame.fromFEN("6k1/5n2/8/8/8/5n2/1RK5/1N6 w - - 0 1");
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
                    if(reason != GameOverReason.FIFTYMOVES) {
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
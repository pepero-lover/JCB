package com.pepero.jcb.api.syzygy;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.encode.EncodeMove;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.pepero.jcb.constant.EncodedPieces.*;

public class SyzygyBestMoveDebug {
    public static void main(String[] args) throws IOException {
        Path syzygyDir = Path.of("syzygy/");
        SyzygyTablebase tb = new SyzygyTablebase(syzygyDir);

        System.out.println(syzygyDir.toAbsolutePath().toString());

        ChessGame game = new ChessGame("8/k2K4/B7/B7/8/8/8/8 w - - 20 11");
        System.out.println("First WDL" + game.probeSyzygyWdl(tb));
        System.out.println("First DTZ" + game.probeSyzygyDtz(tb));

        int ply = 0;

        while (true) {
            MoveInfo bestMove = game.findBestMoveSyzygy(tb);

            if(game.isThreefoldRepetition()) {
                System.out.println("repetition");
                break;
            }
            if(bestMove == null) break;

            game.makeMove(bestMove);
            System.out.println(bestMove);
            System.out.println("ply : " + ply);

            System.out.println("WDL : " + game.probeSyzygyWdl(tb));
            System.out.println("DTZ : " + game.probeSyzygyDtz(tb));
            System.out.println("FEN : " + game.getFEN());

            ply++;
        }
    }
}
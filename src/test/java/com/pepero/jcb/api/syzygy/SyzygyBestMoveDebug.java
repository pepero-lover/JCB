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

        ChessGame game = new ChessGame("8/2B2k2/8/4KB2/8/8/8/8 b - - 0 1");
        System.out.println("First WDL" + game.probeSyzygyWdl(tb));
        System.out.println("First DTZ" + game.probeSyzygyDtz(tb));

        int ply = 0;

        while (true) {
            MoveInfo bestMove = game.findBestMoveSyzygy(tb);

            if(game.isThreefoldRepetition()) {
                System.out.println(game.findRankedSyzygyMoves(tb));
                System.out.println("repetition");
                break;
            }
            if(bestMove == null) break;

            game.makeMove(bestMove);
            System.out.println("ply : " + ply);

            System.out.println("WDL : " + game.probeSyzygyWdl(tb));
            System.out.println("DTZ : " + game.probeSyzygyDtz(tb));
            System.out.println("FEN : " + game.getFEN());

            ply++;
        }
    }
}
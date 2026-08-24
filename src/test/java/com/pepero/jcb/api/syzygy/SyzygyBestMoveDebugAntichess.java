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
    public static String resultString(int wdl, int dtz, boolean white) {
        String wdlString = switch (wdl) {
            case -2 -> white ? "White is losing." : "Black is losing.";
            case -1 -> white ? "White is losing, but because of 50 moves rule, it's a draw." :
                "Black is losing, but because of 50 moves rule, it's a draw.";
            case 0 -> "Drew position";
            case 1 -> white ? "White is winning, but because of 50 moves rule, it's a draw." :
                    "Black is winning, but because of 50 moves rule, it's a draw.";
            case 2 -> white ? "White is winning." : "Black is winning.";
            default -> "Could not find the wdl data.";
        };

        return wdlString + "  Progress in " + dtz + " ply";
    }

    public static void main(String[] args) throws IOException {
        Path syzygyDir = Path.of("syzygy-antichess/");
        SyzygyTablebase tb = new SyzygyTablebase(syzygyDir, 5, GameVariant.SUICIDE);

        BufferedReader bf = new BufferedReader(new InputStreamReader(System.in));

        //ChessGame game = ChessGame.fromFEN("K7/6p1/8/8/8/8/8/1q6 w - - 0 8", GameVariant.SUICIDE);
        ChessGame game = ChessGame.fromFEN(bf.readLine(), GameVariant.SUICIDE);
        System.out.println("First WDL" + SyzygyAnalyzer.probeWdl(game, tb));
        System.out.println("First DTZ" + SyzygyAnalyzer.probeDtz(game, tb));

        int ply = 1;

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

            int wdl = SyzygyAnalyzer.probeWdl(game, tb);
            int dtz = SyzygyAnalyzer.probeDtz(game, tb);
            System.out.println(resultString(wdl, dtz, game.isWhiteTurn()));
            System.out.println("FEN : " + game.getFEN());

            ply++;
        }
    }
}
package com.pepero.jcb.api;

import com.pepero.jcb.api.dto.PGNGame;
import com.pepero.jcb.api.parse.PGNUtils;

public class MainTest {
    public static void main(String[] args) {
        ChessGame chessGame = new ChessGame();

        PGNGame pgn = chessGame.loadPGN("[Event \"?\"]\n" +
                "[Site \"?\"]\n" +
                "[Date \"????.??.??\"]\n" +
                "[Round \"?\"]\n" +
                "[White \"?\"]\n" +
                "[Black \"?\"]\n" +
                "[Result \"*\"]\n" +
                "[Link \"https://www.chess.com/analysis/game/pgn/2fzzf4FktN/analysis\"]\n" +
                "\n" +
                "1. e4 (1. d4 d5 2. c4 (2. Nc3 Nf6)) 1... e5 (1... c5 2. Nf3 d6 (2... Nc6)) 2.\n" +
                "Nf3 (2. Nc3 Nf6 3. Nf3 (3. f4 d5 (3... exf4 $6 4. e5 Ng8 $1))) *");

        System.out.println(PGNUtils.export(pgn));
    }
}

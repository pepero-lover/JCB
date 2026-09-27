package com.pepero.jcb.api;

import com.pepero.jcb.api.gaviota.GaviotaTablebase;
import com.pepero.jcb.api.syzygy.SyzygyTablebase;

import java.io.IOException;
import java.nio.file.Path;

public class CombinedAnalyzeTest {
    public static void main(String[] args) throws IOException {
        SyzygyTablebase syzygy = new SyzygyTablebase(Path.of("D:/tablebases/syzygy"));
        GaviotaTablebase gaviota = new GaviotaTablebase(Path.of("D:/tablebases/gaviota"));

        ChessGame chessGame = ChessGame.fromFEN("8/8/8/8/1p2P3/4P3/1k6/3K4 w - - 0 1");

        System.out.println(CombinedAnalyzer.findRankedMoves(chessGame, syzygy, gaviota));
    }
}

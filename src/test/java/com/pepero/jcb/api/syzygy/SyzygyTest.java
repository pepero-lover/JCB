package com.pepero.jcb.api.syzygy;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.SyzygyAnalyzer;

import java.io.IOException;
import java.nio.file.Path;

public class SyzygyTest {
    public static void main(String[] args) throws IOException {
        ChessGame game = ChessGame.fromFEN("r3k3/8/8/8/8/8/8/4K2R w Kq - 0 1", true);

        Path path = Path.of("syzygy/");

        SyzygyTablebase syzygy = new SyzygyTablebase(path, 5);

        System.out.println("WDL : " + SyzygyAnalyzer.probeWdl(game, syzygy));
        System.out.println("DTZ : " + SyzygyAnalyzer.probeDtz(game, syzygy));
        System.out.println("Rank : " + SyzygyAnalyzer.findRankedMoves(game, syzygy));
    }
}

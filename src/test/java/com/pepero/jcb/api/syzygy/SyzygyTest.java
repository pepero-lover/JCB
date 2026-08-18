package com.pepero.jcb.api.syzygy;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.core.GameVariants;

import java.io.IOException;
import java.nio.file.Path;

public class SyzygyTest {
    public static void main(String[] args) throws IOException {
        ChessGame game = ChessGame.fromFEN("r3k3/8/8/8/8/8/8/4K2R w Kq - 0 1", true);

        Path path = Path.of("syzygy/");

        SyzygyTablebase syzygy = new SyzygyTablebase(path, 5);

        System.out.println("WDL : " + game.probeSyzygyWdl(syzygy));
        System.out.println("DTZ : " + game.probeSyzygyDtz(syzygy));
        System.out.println("Rank : " + game.findRankedSyzygyMoves(syzygy));
    }
}

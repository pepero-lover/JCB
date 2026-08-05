package com.pepero.jcb.api.syzygy;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.syzygy.logics.*;

import java.io.IOException;
import java.nio.file.Path;

public class SyzygyTest {
    public static void main(String[] args) throws IOException {
        ChessGame game = new ChessGame("8/8/8/4P3/1p6/4P3/1k6/3K4 b - - 0 1");

        Path path = Path.of("syzygy/");

        SyzygyTablebase syzygy = new SyzygyTablebase(path, 5);

        System.out.println("WDL : " + game.probeSyzygyWdl(syzygy));
        System.out.println("DTZ : " + game.probeSyzygyDtz(syzygy));
    }
}

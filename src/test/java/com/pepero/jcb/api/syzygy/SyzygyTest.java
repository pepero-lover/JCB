package com.pepero.jcb.api.syzygy;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.syzygy.logics.*;

import java.io.IOException;
import java.nio.file.Path;

public class SyzygyTest {
    public static void main(String[] args) throws IOException {
        ChessGame game = new ChessGame("8/8/4P3/8/kp6/4P3/8/3K4 w - - 1 3");

        Path path = Path.of("syzygy/");

        SyzygyTablebase syzygy = new SyzygyTablebase(path, 5);

        System.out.println("WDL : " + game.probeSyzygyWdl(syzygy));
        System.out.println("DTZ : " + game.probeSyzygyDtz(syzygy));
    }
}

package com.pepero.jcb.api.syzygy;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.syzygy.logics.*;

import java.io.IOException;
import java.nio.file.Path;

public class SyzygyTest {
    public static void main(String[] args) throws IOException {
        ChessGame game = new ChessGame("4k3/8/8/8/2BN4/8/8/4K3 w - - 0 1");

        Path path = Path.of("syzygy/");

        SyzygyTablebase syzygy = new SyzygyTablebase(path, 5);

        System.out.println(game.probeSyzygyWdl(syzygy));
        System.out.println(game.probeSyzygyDtz(syzygy));
    }
}

package com.pepero.jcb.api.syzygy;

import com.pepero.jcb.api.syzygy.logics.*;
import com.pepero.jcb.bitboard.BitBoardUtils;
import com.pepero.jcb.core.Chessboard;

import java.io.IOException;
import java.nio.file.Path;

import static com.pepero.jcb.constant.EncodedPieces.P;

public class SyzygyTest {
    public static void main(String[] args) throws IOException {
        Chessboard board = new Chessboard("4k3/8/4K3/4P3/8/8/8/8 w - - 0 1");

        Path path = Path.of("syzygy/");

        System.out.println(SyzygyProbe.probeWdl(board, path));
    }
}

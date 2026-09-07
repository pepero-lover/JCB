package com.pepero.jcb.api.syzygy;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.SyzygyAnalyzer;
import com.pepero.jcb.api.dto.SyzygyMoveDTO;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class SyzygyRankedMove {
    public static void main(String[] args) throws IOException {
        String fen = "8/2n5/2kb2Q1/8/3rN3/8/4K3/8 w - -";
        ChessGame chessGame = ChessGame.fromFEN(fen);

        SyzygyTablebase tablebase = new SyzygyTablebase(Path.of("D:/syzygy"));

        List<SyzygyMoveDTO> syzygyRankedMoves = SyzygyAnalyzer.findRankedMoves(chessGame, tablebase);

        for(SyzygyMoveDTO syzygyMoveDTO : syzygyRankedMoves) {
            System.out.println(syzygyMoveDTO);
        }
    }
}

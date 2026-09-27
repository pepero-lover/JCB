package com.pepero.jcb.api.syzygy;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.SyzygyAnalyzer;
import com.pepero.jcb.api.dto.SyzygyMoveDTO;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class SyzygyRankedMove {
    public static void main(String[] args) throws IOException {
        String fen = "8/8/8/4P3/1p6/4P3/1k6/3K4 b - - 0 1";
        ChessGame chessGame = ChessGame.fromFEN(fen);

        SyzygyTablebase tablebase = new SyzygyTablebase(Path.of("D:/tablebases/syzygy/"));

        List<SyzygyMoveDTO> syzygyRankedMoves = SyzygyAnalyzer.findRankedMoves(chessGame, tablebase);

        for(SyzygyMoveDTO syzygyMoveDTO : syzygyRankedMoves) {
            System.out.println(syzygyMoveDTO);
        }
    }
}

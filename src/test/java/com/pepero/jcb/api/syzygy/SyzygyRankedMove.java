package com.pepero.jcb.api.syzygy;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.SyzygyAnalyzer;
import com.pepero.jcb.api.dto.SyzygyMoveDTO;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class SyzygyRankedMove {
    public static void main(String[] args) throws IOException {
        String fen = "8/8/3nQ3/4b3/2r5/3Nk3/6K1/8 w - - 1029 516";
        ChessGame chessGame = ChessGame.fromFEN(fen);

        SyzygyTablebase tablebase = new SyzygyTablebase(Path.of("D:/syzygy"));

        List<SyzygyMoveDTO> syzygyRankedMoves = SyzygyAnalyzer.findRankedMoves(chessGame, tablebase);

        for(SyzygyMoveDTO syzygyMoveDTO : syzygyRankedMoves) {
            System.out.println(syzygyMoveDTO);
        }
    }
}

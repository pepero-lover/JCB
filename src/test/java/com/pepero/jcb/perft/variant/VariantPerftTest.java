package com.pepero.jcb.perft.variant;

import com.pepero.jcb.api.parse.ConvertStringMoveUtils;
import com.pepero.jcb.api.perft.PerftDriver;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.ChessboardUtils;
import com.pepero.jcb.core.GameVariants;
import com.pepero.jcb.core.MoveGenerator;

public class VariantPerftTest {
    public static void makeMove(Chessboard chessboard, String lan) {
        MoveGenerator.makeMove(chessboard, ConvertStringMoveUtils.parseLanToEncodedMove(chessboard, lan));
    }

    public static void main(String[] args) {
        Chessboard chessboard = new Chessboard(
                Chessboard.start_position,
                false,
                GameVariants.ATOMIC
        );

        ChessboardUtils.printChessBoard(chessboard);

        System.out.println(PerftDriver.perftBitboardTest(chessboard, 6,
                1, false, true));
    }
}

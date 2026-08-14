package com.pepero.jcb.bitboard;

import com.pepero.jcb.api.perft.PerftDriver;
import com.pepero.jcb.constant.SideToMove;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.ChessboardUtils;
import com.pepero.jcb.core.MoveGenerator;
import com.pepero.jcb.encode.EncodeMove;

import static com.pepero.jcb.constant.BoardSquares.*;
import static com.pepero.jcb.constant.SideToMove.*;

public class BitboardTesting {
    public static void main(String[] args) {
        Chessboard chessboard = new Chessboard(Chessboard.start_position);
        System.out.println(Runtime.getRuntime().availableProcessors());
        System.out.println(
                PerftDriver.perftBitboardTest(chessboard, 8, 10, false, true)
        );
    }
}

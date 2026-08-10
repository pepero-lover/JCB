package com.pepero.jcb.perft.bitboard;

import com.pepero.jcb.api.perft.PerftDriver;
import com.pepero.jcb.constant.BoardSquares;
import com.pepero.jcb.constant.MoveCache;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.ChessboardUtils;
import com.pepero.jcb.core.Initializer;
import com.pepero.jcb.core.MoveGenerator;
import com.pepero.jcb.encode.EncodeMove;
import com.pepero.jcb.util.TimeUtils;

public class PerftBitboardTest {
    public static void main(String[] args) {
        Chessboard chessboard = new Chessboard(Chessboard.start_position);
        PerftDriver.bitboardWarmup(false);
        PerftDriver.perftBitboardTest(chessboard,
                6,
                1,
                false
        );
    }
}
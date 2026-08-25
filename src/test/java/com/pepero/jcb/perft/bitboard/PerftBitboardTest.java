package com.pepero.jcb.perft.bitboard;

import com.pepero.jcb.api.perft.PerftDriver;
import com.pepero.jcb.core.Chessboard;

public class PerftBitboardTest {
    public static void main(String[] args) {
        Chessboard chessboard = new Chessboard(Chessboard.start_position);
        PerftDriver.perftBitboardTest(chessboard,
                6,
                1,
                false,
                false
        );
    }
}
package com.pepero.jcb.bitboard;

import com.pepero.jcb.api.perft.PerftDriver;
import com.pepero.jcb.core.Chessboard;

public class BitboardTesting {
    public static void main(String[] args) {
        Chessboard chessboard = new Chessboard(Chessboard.start_position);
        System.out.println(Runtime.getRuntime().availableProcessors());
        System.out.println(
                PerftDriver.perftBitboardTest(chessboard, 8, 10, false, true)
        );
    }
}

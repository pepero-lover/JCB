package com.pepero.jcb.api;

import com.pepero.jcb.api.perft.PerftDriver;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.GameVariants;

public class MoveGenTest {
    public static void main(String[] args) {
        Chessboard chessboard = new Chessboard(
                Chessboard.start_position,
                GameVariants.CRAZY_HOUSE
        );

        System.out.println(PerftDriver.perftBitboardTest(chessboard, 5, 1, false, false));
    }
}

package com.pepero.jcb.perft.atomic;

import com.pepero.jcb.api.perft.PerftDriver;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.GameVariants;

public class AtomicPerft {
    public static void main(String[] args) {
        Chessboard chessboard = new Chessboard(
                Chessboard.start_position,
                false,
                GameVariants.ATOMIC
        );

        System.out.println(PerftDriver.perftBitboardTest(chessboard, 6,
                1, false, true));
    }
}

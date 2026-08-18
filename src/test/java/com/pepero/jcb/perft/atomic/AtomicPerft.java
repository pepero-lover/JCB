package com.pepero.jcb.perft.atomic;

import com.pepero.jcb.api.perft.PerftDriver;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.GameVariants;

public class AtomicPerft {
    public static void main(String[] args) {
        Chessboard chessboard = new Chessboard(
                "r3k1rR/5K2/8/8/8/8/8/8 b kq - 0 1",
                GameVariants.ATOMIC
        );

        System.out.println(PerftDriver.perftBitboardTest(chessboard, 4,
                1, false, true));
    }
}

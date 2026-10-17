package com.pepero.jcb.perft.bitboard;

import com.pepero.jcb.api.perft.PerftDriver;
import com.pepero.jcb.core.Chessboard;

public class PerftBitboardTest {
    public static void main(String[] args) {
        // 4k3/8/8/8/8/8/4P3/4K3 w - - 0 1 pawn
        // 4k3/3b4/8/7p/7P/8/3B4/4K3 w - - 0 1 bishop
        // 4k3/8/8/8/8/8/8/4K3 w - - 0 1 king
        // 4k3/1r6/8/8/8/8/7R/4K3 w - - 0 1 rook
        // rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 start
        // kiwipete r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq -

        Chessboard chessboard = new Chessboard(Chessboard.start_position);
        PerftDriver.perftBitboardTest(chessboard,
                6,
                1,
                false,
                false
        );
    }
}
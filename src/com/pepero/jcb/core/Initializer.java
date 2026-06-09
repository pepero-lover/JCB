package com.pepero.jcb.core;

import com.pepero.jcb.bitboard.Attacks;
import com.pepero.jcb.bitboard.Magics;
import com.pepero.jcb.encode.EncodeMove;
import com.pepero.jcb.hash.Zobrist;

public class Initializer {
    /**
     * Init all variables
     */
    public static void init(){
        Attacks.initLeapersAttacks();

        Magics.initSlidersAttacks(Attacks.bishop);
        Magics.initSlidersAttacks(Attacks.rook);

        ChessboardUtils.initCharPieces();

        EncodeMove.initPromotedPiecesChar();

        Zobrist.initHashKeys();

        // it doesn't need now because the magic numbers is now saved on arrays
        // Magics.initMagicNumbers();
    }
}

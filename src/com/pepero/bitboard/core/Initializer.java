package com.pepero.bitboard.core;

import com.pepero.bitboard.bitboard.Attacks;
import com.pepero.bitboard.bitboard.Magics;
import com.pepero.bitboard.encode.EncodeMove;

public class Initializer {
    /**
     * Init all variables
     */
    public static void init(){
        Attacks.initLeapersAttacks();

        Magics.initSlidersAttacks(Attacks.bishop);
        Magics.initSlidersAttacks(Attacks.rook);

        ChessBoardUtils.initCharPieces();

        EncodeMove.initPromotedPiecesChar();

        // it doesn't need now because the magic numbers is now saved on arrays
        // Magics.initMagicNumbers();
    }
}

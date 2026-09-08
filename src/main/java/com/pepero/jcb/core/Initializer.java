package com.pepero.jcb.core;

import com.pepero.jcb.core.bitboard.Attacks;
import com.pepero.jcb.core.bitboard.Magics;
import com.pepero.jcb.core.constant.EncodedPieces;
import com.pepero.jcb.core.encode.EncodeMove;
import com.pepero.jcb.core.hash.Zobrist;

/**
 * Initializing {@link Attacks} table, magic slider attacks, hash keys, etc. <br>
 * Called just once when {@link Chessboard} is loaded on JVM.
 */
public class Initializer {
    /**
     * Initializing {@link Attacks} table, magic slider attacks, hash keys, etc. <br>
     * Called just once when {@link Chessboard} is loaded on JVM.
     */
    public static void init(){
        Attacks.initLeapersAttacks();

        Magics.initSlidersAttacks(Attacks.bishop);
        Magics.initSlidersAttacks(Attacks.rook);

        EncodedPieces.initCharPieces();

        EncodeMove.initPromotedPiecesChar();

        Zobrist.initHashKeys();

        // it doesn't need now because the magic numbers is now saved on arrays
        // Magics.initMagicNumbers();
    }
}

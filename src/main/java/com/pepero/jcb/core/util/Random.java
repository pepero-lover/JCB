package com.pepero.jcb.core.util;

/**
 * Seed-random number generator for generating {@link com.pepero.jcb.core.bitboard.MagicNumbers}
 * and {@link com.pepero.jcb.core.hash.Zobrist} hash keys. <p>
 *
 * Fun fact : The starting state seed is 111111 (pepero-lover)
 */
public class Random {
    // i love pepero

    // pseudo random number state
    private static long state = 111111L;

    // state for MAGIC NUM
    private static final long MAGIC_NUM_STATE = 111111L;

    // state for HASHING
    private static final long HASHING_STATE = 111111L;

    /**
     * Generate 32-bit pseudo legal numbers
     * <p>
     * It depends on the state, so if the state changes to 111111 to 111110,
     * it prints completely another number.
     * <p>
     * In other words, if the state is the same, the random result is always the same.
     *
     * @return random 32bits number (int)
     */
    public static int getRandom32BitsNumber() {
        return (int) (getRandom64BitsNumber() >>> 32);
    }


    /**
     * Generate 64-bit pseudo legal numbers
     * <p>
     * It depends on the state, so if the state changes to 111111 to 111110,
     * it prints completely another number
     * <p>
     * In other words, if the state is the same, the random result is always the same.
     *
     * @return random 64bits number (long)
     */
    public static long getRandom64BitsNumber() {
        long z = (state += 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * Generate magic number candidate
     *
     * @return the magic number candidate
     */
    public static long generateMagicNumber(){
        return getRandom64BitsNumber() & getRandom64BitsNumber() & getRandom64BitsNumber();
    }

    /**
     * Set random state value to MAGIC_NUM_STATE value
     */
    public static void setRandomStateForMagicNumber(){
        state = MAGIC_NUM_STATE;
    }

    /**
     * Set random state value to HASHING_STATE value
     */
    public static void setRandomStateForHashing(){
        state = HASHING_STATE;
    }
}

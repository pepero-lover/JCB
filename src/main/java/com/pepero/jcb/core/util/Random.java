package com.pepero.jcb.core.util;

public class Random {
    // i love pepero

    // pseudo random number state
    private static int state = 111111;

    // state for MAGIC NUM
    private static final int MAGIC_NUM_STATE = 111111;

    // state for HASHING
    private static final int HASHING_STATE = 111111;

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
    public static int getRandom32BitsNumber(){
        // get current states
        int number = state;

        // XOR shift algorithm
        number ^= number << 13;
        number ^= number >>> 17;
        number ^= number << 5;

        // update random number state
        state = number;

        // return random number
        return number;
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
    public static long getRandom64BitsNumber(){
        // define 4 random numbers
        long n1, n2, n3 ,n4;

        // init random numbers slicing 16 bits from MS1B side
        n1 = (long) getRandom32BitsNumber() & 0xFFFF;
        n2 = (long) getRandom32BitsNumber() & 0xFFFF;
        n3 = (long) getRandom32BitsNumber() & 0xFFFF;
        n4 = (long) getRandom32BitsNumber() & 0xFFFF;

        // return random number
        return n1 | (n2 << 16) | (n3 << 32) | (n4 << 48);
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

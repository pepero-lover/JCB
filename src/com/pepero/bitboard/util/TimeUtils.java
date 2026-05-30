package com.pepero.bitboard.util;

public class TimeUtils {
    // get time milliseconds
    public static long getTimeMs(){
        return System.nanoTime() / 1_000_000;
    }

    // get time nano time
    public static long getTimeNt(){
        return System.nanoTime();
    }
}

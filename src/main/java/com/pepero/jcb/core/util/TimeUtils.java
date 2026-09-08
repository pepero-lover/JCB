package com.pepero.jcb.core.util;

/**
 * Time measurement utils for perft driver.
 */
public class TimeUtils {
    // get time milliseconds
    public static long getTimeMs(){
        return System.currentTimeMillis();
    }

    // get time nano time
    public static long getTimeNt(){
        return System.nanoTime();
    }
}

package com.pepero.jcb.core.util;

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

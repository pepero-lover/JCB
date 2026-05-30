package com.pepero.jcb.constant;

/**
 * define castling rights
 */
public class CastlingRights {
    /*
    If you want to know what these numbers are doing, go to the Bitboard.java class and check the
    variable named castle
    */

    // castling bits binary representation

    public static int WK = 1; // 0001
    public static int WQ = 2; // 0100
    public static int BK = 4; // 0010
    public static int BQ = 8; // 1000
}

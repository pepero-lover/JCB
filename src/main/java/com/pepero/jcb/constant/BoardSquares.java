package com.pepero.jcb.constant;

import com.pepero.jcb.bitboard.BitBoardUtils;

public class BoardSquares {
    // For human reading board numbers (a1 = 0, LERF: Little-Endian Rank-File)
    public static final int a1 = 0;     public static final int b1 = 1;     public static final int c1 = 2;     public static final int d1 = 3;     public static final int e1 = 4;     public static final int f1 = 5;     public static final int g1 = 6;     public static final int h1 = 7;
    public static final int a2 = 8;     public static final int b2 = 9;     public static final int c2 = 10;     public static final int d2 = 11;     public static final int e2 = 12;     public static final int f2 = 13;     public static final int g2 = 14;     public static final int h2 = 15;
    public static final int a3 = 16;     public static final int b3 = 17;     public static final int c3 = 18;     public static final int d3 = 19;     public static final int e3 = 20;     public static final int f3 = 21;     public static final int g3 = 22;     public static final int h3 = 23;
    public static final int a4 = 24;     public static final int b4 = 25;     public static final int c4 = 26;     public static final int d4 = 27;     public static final int e4 = 28;     public static final int f4 = 29;     public static final int g4 = 30;     public static final int h4 = 31;
    public static final int a5 = 32;     public static final int b5 = 33;     public static final int c5 = 34;     public static final int d5 = 35;     public static final int e5 = 36;     public static final int f5 = 37;     public static final int g5 = 38;     public static final int h5 = 39;
    public static final int a6 = 40;     public static final int b6 = 41;     public static final int c6 = 42;     public static final int d6 = 43;     public static final int e6 = 44;     public static final int f6 = 45;     public static final int g6 = 46;     public static final int h6 = 47;
    public static final int a7 = 48;     public static final int b7 = 49;     public static final int c7 = 50;     public static final int d7 = 51;     public static final int e7 = 52;     public static final int f7 = 53;     public static final int g7 = 54;     public static final int h7 = 55;
    public static final int a8 = 56;     public static final int b8 = 57;     public static final int c8 = 58;     public static final int d8 = 59;     public static final int e8 = 60;     public static final int f8 = 61;     public static final int g8 = 62;     public static final int h8 = 63;

    // no square
    public static final int no_sq = 64;


    // Rank constants

    public static final long RANK_1 = 0x00000000000000FFL;
    public static final long RANK_2 = 0x000000000000FF00L;
    public static final long RANK_3 = 0x0000000000FF0000L;
    public static final long RANK_4 = 0x00000000FF000000L;
    public static final long RANK_5 = 0x000000FF00000000L;
    public static final long RANK_6 = 0x0000FF0000000000L;
    public static final long RANK_7 = 0x00FF000000000000L;
    public static final long RANK_8 = 0xFF00000000000000L;


    // for masking king of the hills center squares
    public static final long CENTER_SQUARES =
            (1L << d4) | (1L << e4) | (1L << d5) | (1L << e5);

    // for masking racing kings goal line
    // 8 rank mask
    public static final long GOAL_LINE = RANK_8;


    public static String[] square_to_coordinates = {
            "a1", "b1", "c1", "d1", "e1", "f1", "g1", "h1",
            "a2", "b2", "c2", "d2", "e2", "f2", "g2", "h2",
            "a3", "b3", "c3", "d3", "e3", "f3", "g3", "h3",
            "a4", "b4", "c4", "d4", "e4", "f4", "g4", "h4",
            "a5", "b5", "c5", "d5", "e5", "f5", "g5", "h5",
            "a6", "b6", "c6", "d6", "e6", "f6", "g6", "h6",
            "a7", "b7", "c7", "d7", "e7", "f7", "g7", "h7",
            "a8", "b8", "c8", "d8", "e8", "f8", "g8", "h8",
    };

    public static int coordinates_to_square(String coordinate) {
        if (coordinate == null || coordinate.length() != 2) {
            return -1; // return if the coordinate is not right
        }

        int file = coordinate.charAt(0) - 'a';
        int rank = coordinate.charAt(1) - '1';

        // if square is out of bounds
        if (file < 0 || file > 7 || rank < 0 || rank > 7) {
            return -1;
        }

        // return result
        return rank * 8 + file;
    }
}
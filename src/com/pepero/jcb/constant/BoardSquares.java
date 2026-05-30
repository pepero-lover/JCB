package com.pepero.jcb.constant;

public class BoardSquares {
    // For human reading board numbers

    public static final int a8 = 0;     public static final int b8 = 1;     public static final int c8 = 2;     public static final int d8 = 3;     public static final int e8 = 4;     public static final int f8 = 5;     public static final int g8 = 6;     public static final int h8 = 7;
    public static final int a7 = 8;     public static final int b7 = 9;     public static final int c7 = 10;     public static final int d7 = 11;     public static final int e7 = 12;     public static final int f7 = 13;     public static final int g7 = 14;     public static final int h7 = 15;
    public static final int a6 = 16;     public static final int b6 = 17;     public static final int c6 = 18;     public static final int d6 = 19;     public static final int e6 = 20;     public static final int f6 = 21;     public static final int g6 = 22;     public static final int h6 = 23;
    public static final int a5 = 24;     public static final int b5 = 25;     public static final int c5 = 26;     public static final int d5 = 27;     public static final int e5 = 28;     public static final int f5 = 29;     public static final int g5 = 30;     public static final int h5 = 31;
    public static final int a4 = 32;     public static final int b4 = 33;     public static final int c4 = 34;     public static final int d4 = 35;     public static final int e4 = 36;     public static final int f4 = 37;     public static final int g4 = 38;     public static final int h4 = 39;
    public static final int a3 = 40;     public static final int b3 = 41;     public static final int c3 = 42;     public static final int d3 = 43;     public static final int e3 = 44;     public static final int f3 = 45;     public static final int g3 = 46;     public static final int h3 = 47;
    public static final int a2 = 48;     public static final int b2 = 49;     public static final int c2 = 50;     public static final int d2 = 51;     public static final int e2 = 52;     public static final int f2 = 53;     public static final int g2 = 54;     public static final int h2 = 55;
    public static final int a1 = 56;     public static final int b1 = 57;     public static final int c1 = 58;     public static final int d1 = 59;     public static final int e1 = 60;     public static final int f1 = 61;     public static final int g1 = 62;     public static final int h1 = 63;

    // no square
    public static final int no_sq = 64;


    public static String[] square_to_coordinates = {
            "a8", "b8", "c8", "d8", "e8", "f8", "g8", "h8",
            "a7", "b7", "c7", "d7", "e7", "f7", "g7", "h7",
            "a6", "b6", "c6", "d6", "e6", "f6", "g6", "h6",
            "a5", "b5", "c5", "d5", "e5", "f5", "g5", "h5",
            "a4", "b4", "c4", "d4", "e4", "f4", "g4", "h4",
            "a3", "b3", "c3", "d3", "e3", "f3", "g3", "h3",
            "a2", "b2", "c2", "d2", "e2", "f2", "g2", "h2",
            "a1", "b1", "c1", "d1", "e1", "f1", "g1", "h1",
    };

    public static int coordinates_to_square(String coordinate) {
        if (coordinate == null || coordinate.length() != 2) {
            return -1; // return if the coordinate is not right
        }

        int file = coordinate.charAt(0) - 'a';
        int rank = '8' - coordinate.charAt(1);

        // if square is out of bounds
        if (file < 0 || file > 7 || rank < 0 || rank > 7) {
            return -1;
        }

        // return result
        return rank * 8 + file;
    }
}

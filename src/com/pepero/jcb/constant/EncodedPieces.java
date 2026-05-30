package com.pepero.jcb.constant;

public class EncodedPieces {
    // white pieces
    public static final int P = 0; // white pawn
    public static final int N = 1; // white knight
    public static final int B = 2; // white bishop
    public static final int R = 3; // white rook
    public static final int Q = 4; // white queen
    public static final int K = 5; // white king

    // black pieces
    public static final int p = 6; // black pawn
    public static final int n = 7; // black knight
    public static final int b = 8; // black bishop
    public static final int r = 9; // black rook
    public static final int q = 10; // black queen
    public static final int k = 11; // black king

    public static String encodedPieceToString(int piece){
        return switch (piece) {
            case N, n -> "N";
            case B, b -> "B";
            case R, r -> "R";
            case Q, q -> "Q";
            case K, k -> "K";
            default -> "";
        };
    }
}

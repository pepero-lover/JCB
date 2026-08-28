package com.pepero.jcb.core.constant;

import java.util.HashMap;
import java.util.Map;

import static com.pepero.jcb.core.constant.SideToMove.*;

/**
 * Encoded Pieces constant for bitboard
 */
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

    public static final char[] ascii_pieces = {
            'P','N','B','R','Q','K',
            'p','n','b','r','q','k'
    };

    public static final char[] promotion_pieces = {
            'p','n','b','r','q','k'
    };

    /**
     * Normalize a color-agnostic piece type constant (0~5) to match the given side.
     * If the piece is already color-correct, returns it unchanged.
     *
     * @param pieceType piece type constant (can be either color, e.g. N or n)
     * @param side side to normalize to (white/black)
     * @return piece type constant matching {@code side}
     */
    public static int normalizePieceColor(int pieceType, int side) {
        if (side == black && pieceType < 6) return pieceType + 6;
        if (side == white && pieceType >= 6) return pieceType - 6;
        return pieceType;
    }

    // convert char pieces to encoded constants
    public static final Map<Character, Integer> char_to_encoded_piece = new HashMap<>();

    // init piece char
    public static void initCharPieces(){
        initCharToEncodedPiece();
    }

    // init char map
    private static void initCharToEncodedPiece(){
        char_to_encoded_piece.put('P', P);
        char_to_encoded_piece.put('N', N);
        char_to_encoded_piece.put('B', B);
        char_to_encoded_piece.put('R', R);
        char_to_encoded_piece.put('Q', Q);
        char_to_encoded_piece.put('K', K);

        char_to_encoded_piece.put('p', p);
        char_to_encoded_piece.put('n', n);
        char_to_encoded_piece.put('b', b);
        char_to_encoded_piece.put('r', r);
        char_to_encoded_piece.put('q', q);
        char_to_encoded_piece.put('k', k);
    }
}

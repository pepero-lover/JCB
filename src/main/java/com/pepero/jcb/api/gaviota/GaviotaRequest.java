package com.pepero.jcb.api.gaviota;

import java.util.Arrays;

/**
 * Ported from gaviota.py's Request class. Piece type constants follow the
 * python-chess convention used throughout gaviota.py: PAWN=1, KNIGHT=2,
 * BISHOP=3, ROOK=4, QUEEN=5, KING=6 (so sorting descending puts the king
 * first, matching every pctoindex() function's assumption that index 0 is
 * always the king).
 */
final class GaviotaRequest {

    static final int PAWN = 1;
    static final int KNIGHT = 2;
    static final int BISHOP = 3;
    static final int ROOK = 4;
    static final int QUEEN = 5;
    static final int KING = 6;

    static char pieceSymbol(int type) {
        switch (type) {
            case PAWN: return 'p';
            case KNIGHT: return 'n';
            case BISHOP: return 'b';
            case ROOK: return 'r';
            case QUEEN: return 'q';
            case KING: return 'k';
            default: throw new IllegalArgumentException("Invalid piece type: " + type);
        }
    }

    // sorted (descending by piece type: king..pawn), set at construction — mirrors
    // gaviota.py's self.white_squares/self.white_types (pre material-key resolution)
    final int[] whiteSquares;
    final int[] whiteTypes;
    final int[] blackSquares;
    final int[] blackTypes;

    final int realSide; // 0 = white to move, 1 = black to move (as originally requested)
    int side;            // may get flipped (opp()) if the material key needed reversal

    // set by GaviotaTablebase's material-key resolution step (mirrors _setup_tablebase):
    // the actual piece order/side used to probe, which may be a color-flipped mirror
    // of the original position if only the reversed material name has a table file.
    String egKey;
    int[] whitePieceSquares;
    int[] whitePieceTypes;
    int[] blackPieceSquares;
    int[] blackPieceTypes;
    boolean isReversed;

    GaviotaRequest(int[] whiteSquares, int[] whiteTypes, int[] blackSquares, int[] blackTypes, int side) {
        int[][] w = sortByTypeDescending(whiteSquares, whiteTypes);
        this.whiteSquares = w[0];
        this.whiteTypes = w[1];

        int[][] b = sortByTypeDescending(blackSquares, blackTypes);
        this.blackSquares = b[0];
        this.blackTypes = b[1];

        this.realSide = side;
        this.side = side;
    }

    /** Ported from gaviota.py's sortlists(): stable sort by piece type, descending. */
    private static int[][] sortByTypeDescending(int[] squares, int[] types) {
        int n = squares.length;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, (a, c) -> types[c] - types[a]); // descending, stable (Java sort on Object[] is stable)

        int[] sq = new int[n];
        int[] ty = new int[n];
        for (int i = 0; i < n; i++) {
            sq[i] = squares[order[i]];
            ty[i] = types[order[i]];
        }
        return new int[][]{sq, ty};
    }
}
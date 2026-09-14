package com.pepero.jcb.api.gaviota;

import java.util.Arrays;

/**
 * NOT a ported C struct/class — {@code gtb-probe.c} has no "Request" object.
 * {@code tb_probe_hard()}/{@code tb_probe_soft()}/{@code tb_probe_()} take the
 * equivalent data as plain parameters instead:
 * <pre>
 * tb_probe_(unsigned stm, SQUARE epsq,
 *           const SQUARE *inp_wSQ, const SQUARE *inp_bSQ,
 *           const SQ_CONTENT *inp_wPC, const SQ_CONTENT *inp_bPC,
 *           bool_t probingtype, unsigned *res, unsigned *ply)
 * </pre>
 * This class exists purely to bundle those same six pieces of per-probe state
 * (white/black square+type lists, side-to-move) into one object for JCB's own
 * call sites; grouping them isn't something to "match" against the C, since
 * the C intentionally keeps them as separate parameters.
 * <p>
 * The piece-type constants (PAWN=1 .. KING=6) are NOT merely a python-chess
 * convention — {@code gtb-probe.c} asserts this exact numbering itself:
 * {@code assert (PAWN == 1 && KNIGHT == 2 && BISHOP == 3 && ROOK == 4 &&
 * QUEEN == 5 && KING == 6);}. Sorting descending by this value puts the king
 * first, matching every {@code pctoindex()} function's assumption that index 0
 * is always the king.
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

    // sorted (descending by piece type: king..pawn), set at construction — corresponds
    // to gtb-probe.c's tb_probe_() local ws/wp (white) and bs/bp (black) arrays
    // right after its own sortlists(ws, wp)/sortlists(bs, bp) calls, before the
    // straight-vs-reversed material-key branch below runs.
    final int[] whiteSquares;
    final int[] whiteTypes;
    final int[] blackSquares;
    final int[] blackTypes;

    final int realSide; // 0 = white to move, 1 = black to move (as originally requested)
    int side;            // may get flipped (opp()) if the material key needed reversal

    // set by GaviotaTablebase's material-key resolution step. Corresponds to
    // tb_probe_()'s straight-vs-reversed branch: it tries egtb_get_id(wp, bp) first
    // (straight — table file matches white-then-black material), and only if that
    // fails falls back to egtb_get_id(bp, wp) (reversed — only the black-then-white
    // table file exists). In the reversed case the C does
    // list_sq_flipNS(ws); list_sq_flipNS(bs); swap(ws, bs); stm = Opp(stm);
    // — i.e. XOR every square by 0x38 (070 octal) AND swap which side's list plays
    // which role AND flip side-to-move, all together. whitePieceSquares/blackPieceSquares
    // below hold that same post-swap, post-flip result.
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

    /**
     * Corresponds to gtb-probe.c's {@code sortlists()}: descending sort by piece
     * type (king first). Note one deliberate difference: the C version is a plain
     * swap-based nested loop ({@code if (wp[j] > wp[i]) swap(...)}), which is NOT
     * guaranteed stable for two pieces of the same type — whichever ends up in the
     * lower index after the swaps is whatever the swap pattern happens to produce.
     * This port uses a stable sort instead. This has not been verified to be safe
     * across every {@code pctoindex()} function for materials with duplicate piece
     * types (e.g. KRR, KNN) — if a specific pctoindex() function turns out to
     * assume the C's particular tie-breaking order rather than treating same-type
     * squares as freely reorderable, this stable sort could disagree with it.
     */
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
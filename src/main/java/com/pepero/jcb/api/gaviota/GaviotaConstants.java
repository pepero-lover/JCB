package com.pepero.jcb.api.gaviota;

/**
 * Static index tables for Gaviota material classes (KKIDX, AAIDX, PP48, PPP48, ...).
 * <p>
 * Derived directly from the {@code init_kkidx}/{@code init_aaidx}/{@code init_ppidx}/
 * {@code init_flipt}/{@code init_aaa}/{@code init_pp48_idx}/{@code init_ppp48_idx}
 * functions in {@code gtb-probe.c}, part of the Gaviota Tablebases probing code:
 * <pre>
 * Gaviota Tablebases Probing Code
 * Copyright (c) 2010 Miguel A. Ballicora
 * Released under the X11 ("MIT") license — see gtb-probe.c's own header.
 * https://github.com/michiguel/Gaviota-Tablebases
 * </pre>
 * Square numbering here is a1=0 LERF (JCB's own convention), matching the C
 * source's A1=0..H8=63 enum, so no remapping is needed.
 * <p>
 * One C init function -&gt; one private static {@code initX()} method below,
 * called once from a static initializer, rather than one giant anonymous
 * static block per table.
 */
final class GaviotaConstants {

    private GaviotaConstants() {}

    // ---- sizes (mirror gtb-probe.c's MAX_* #defines) ----

    static final int NOSQUARE = 64;
    static final int NOINDEX = -1;

    static final int MAX_KKINDEX = 462;
    static final int MAX_PPINDEX = 576;
    static final int MAX_PpINDEX = 24 * 48;
    static final int MAX_AAINDEX = (63 - 62) + (62 * (127 - 62) / 2) - 1 + 1; // 2016
    static final int MAX_AAAINDEX = 64 * 21 * 31;
    static final int MAX_PP48_INDEX = 1128;
    static final int MAX_PPP48_INDEX = 8648;

    // ---- per-material max-index constants (mirror gtb-probe.c's TB_INDEXES enum) ----

    static final long MAX_KXK = (long) MAX_KKINDEX * 64;
    static final long MAX_kabk = (long) MAX_KKINDEX * 64 * 64;
    static final long MAX_kakb = (long) MAX_KKINDEX * 64 * 64;
    static final long MAX_kpk = 24L * 64 * 64;
    static final long MAX_kakp = 24L * 64 * 64 * 64;
    static final long MAX_kapk = 24L * 64 * 64 * 64;
    static final long MAX_kppk = (long) MAX_PPINDEX * 64 * 64;
    static final long MAX_kpkp = (long) MAX_PpINDEX * 64 * 64;
    static final long MAX_kaak = (long) MAX_KKINDEX * MAX_AAINDEX;
    static final long MAX_kabkc = (long) MAX_KKINDEX * 64 * 64 * 64;
    static final long MAX_kabck = (long) MAX_KKINDEX * 64 * 64 * 64;
    static final long MAX_kaakb = (long) MAX_KKINDEX * MAX_AAINDEX * 64;
    static final long MAX_kaabk = (long) MAX_KKINDEX * MAX_AAINDEX * 64;
    static final long MAX_kabbk = (long) MAX_KKINDEX * MAX_AAINDEX * 64;
    static final long MAX_kaaak = (long) MAX_KKINDEX * MAX_AAAINDEX;
    static final long MAX_kapkb = 24L * 64 * 64 * 64 * 64;
    static final long MAX_kabkp = 24L * 64 * 64 * 64 * 64;
    static final long MAX_kabpk = 24L * 64 * 64 * 64 * 64;
    static final long MAX_kppka = MAX_kppk * 64;
    static final long MAX_kappk = MAX_kppk * 64;
    static final long MAX_kapkp = MAX_kpkp * 64;
    static final long MAX_kaapk = 24L * MAX_AAINDEX * 64 * 64;
    static final long MAX_kaakp = 24L * MAX_AAINDEX * 64 * 64;
    static final long MAX_kppkp = 24L * MAX_PP48_INDEX * 64 * 64;
    static final long MAX_kpppk = (long) MAX_PPP48_INDEX * 64 * 64;

    static final int PLYSHIFT = 3;
    static final int INFOMASK = 7;

    static final int WE_FLAG = 1;
    static final int NS_FLAG = 2;
    static final int NW_SE_FLAG = 4;

    static final int ENTRIES_PER_BLOCK = 16 * 1024;
    static final int EGTB_MAXBLOCKSIZE = 65536;

    // 0-indexed d-file / 4th rank, used by norm_kkindex()/flip_type()
    private static final int FILE_D = 3;
    private static final int RANK_4 = 3;

    // ---- ITOSQ: gtb-probe.c's `itosq` array (a1=0 LERF square numbers) ----
    static final int[] ITOSQ = {
            55, 54, 53, 52,   // H7,G7,F7,E7
            47, 46, 45, 44,   // H6,G6,F6,E6
            39, 38, 37, 36,   // H5,G5,F5,E5
            31, 30, 29, 28,   // H4,G4,F4,E4
            23, 22, 21, 20,   // H3,G3,F3,E3
            15, 14, 13, 12,   // H2,G2,F2,E2
            51, 50, 49, 48,   // D7,C7,B7,A7
            43, 42, 41, 40,   // D6,C6,B6,A6
            35, 34, 33, 32,   // D5,C5,B5,A5
            27, 26, 25, 24,   // D4,C4,B4,A4
            19, 18, 17, 16,   // D3,C3,B3,A3
            11, 10, 9, 8      // D2,C2,B2,A2
    };

    // ============================================================
    // basic square math (gtb-probe.c's flipWE/flipNS/flipNW_SE/getcol/getrow/
    // in_queenside macros)
    // ============================================================

    static int squareFile(int sq) {
        return sq & 7;
    }

    static int squareRank(int sq) {
        return sq >> 3;
    }

    static int flipWe(int x) {
        return x ^ 7;
    }

    static int flipNs(int x) {
        return x ^ 56;
    }

    static int flipNwSe(int x) {
        return ((x & 7) << 3) | (x >> 3);
    }

    static boolean inQueenside(int x) {
        return (x & (1 << 2)) == 0;
    }

    static boolean idxIsEmpty(int x) {
        return x == -1;
    }

    /**
     * True when a king on x and a king on y would be on the same square or
     * mutually attacking (adjacent) — the two-king-specific case of
     * gtb-probe.c's {@code possible_attack(x, y, wK)}, which relies on a
     * full king-attack table not reproduced here.
     */
    private static boolean kingsAdjacentOrSame(int x, int y) {
        if (x == y) return true;
        int df = Math.abs(squareFile(x) - squareFile(y));
        int dr = Math.abs(squareRank(x) - squareRank(y));
        return df <= 1 && dr <= 1;
    }

    // ============================================================
    // norm_kkindex() / flip_type() — ported from gtb-probe.c
    // ============================================================

    /** Ported from norm_kkindex(). @return {normX, normY} */
    static int[] normKkIndex(int x, int y) {
        if (squareFile(x) > FILE_D) {
            x = flipWe(x);
            y = flipWe(y);
        }
        if (squareRank(x) > RANK_4) {
            x = flipNs(x);
            y = flipNs(y);
        }

        int rowx = squareRank(x);
        int colx = squareFile(x);
        if (rowx > colx) {
            x = flipNwSe(x);
            y = flipNwSe(y);
        }

        int rowy = squareRank(y);
        int coly = squareFile(y);
        if (rowx == colx && rowy > coly) {
            x = flipNwSe(x);
            y = flipNwSe(y);
        }

        return new int[]{x, y};
    }

    /** Ported from flip_type(). */
    static int flipType(int x, int y) {
        int ret = 0;

        if (squareFile(x) > FILE_D) {
            x = flipWe(x);
            y = flipWe(y);
            ret |= 1;
        }
        if (squareRank(x) > RANK_4) {
            x = flipNs(x);
            y = flipNs(y);
            ret |= 2;
        }

        int rowx = squareRank(x);
        int colx = squareFile(x);
        if (rowx > colx) {
            x = flipNwSe(x);
            y = flipNwSe(y);
            ret |= 4;
        }

        int rowy = squareRank(y);
        int coly = squareFile(y);
        if (rowx == colx && rowy > coly) {
            ret |= 4;
        }

        return ret;
    }

    // ============================================================
    // pp_putanchorfirst() / wsq_to_pidx24() / wsq_to_pidx48() — ported from gtb-probe.c
    // ============================================================

    /** Ported from pp_putanchorfirst(). @return {anchor, loosen} */
    static int[] ppPutAnchorFirst(int a, int b) {
        int rowA = a & 56;
        int rowB = b & 56;

        int anchor = a;
        int loosen = b;

        if (rowB > rowA) {
            anchor = b;
            loosen = a;
        } else if (rowB == rowA) {
            int col = a & 7;
            int hiA = ((1 << col) | (1 << (col ^ 7))) & (((1 << col) | (1 << (col ^ 7))) - 1);

            col = b & 7;
            int hiB = ((1 << col) | (1 << (col ^ 7))) & (((1 << col) | (1 << (col ^ 7))) - 1);

            if (hiB > hiA) {
                anchor = b;
                loosen = a;
            } else if (hiB < hiA) {
                anchor = a;
                loosen = b;
            } else if (a < b) {
                anchor = a;
                loosen = b;
            } else {
                anchor = b;
                loosen = a;
            }
        }

        return new int[]{anchor, loosen};
    }

    /** Ported from wsq_to_pidx24(): pawn square (queenside only) -> 0..23 slice. */
    static int wsqToPidx24(int pawn) {
        int sq = pawn;
        sq = flipNs(sq);
        sq -= 8;
        return (sq + (sq & 3)) >> 1;
    }

    /** Ported from wsq_to_pidx48(): pawn square (either side) -> 0..47 slice. */
    static int wsqToPidx48(int pawn) {
        int sq = pawn;
        sq = flipNs(sq);
        sq -= 8;
        return sq;
    }

    // ============================================================
    // KKIDX — ported from init_kkidx()
    // ============================================================

    static final int[][] KKIDX = new int[64][64];
    static final int[] WKSQ = new int[MAX_KKINDEX];
    static final int[] BKSQ = new int[MAX_KKINDEX];

    /** Ported from init_kkidx(). modifies KKIDX[][], WKSQ[], BKSQ[]. */
    private static void initKkidx() {
        for (int[] row : KKIDX) java.util.Arrays.fill(row, NOINDEX);

        int idx = 0;
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                if (kingsAdjacentOrSame(x, y)) continue;

                int[] norm = normKkIndex(x, y);
                int i = norm[0], j = norm[1];

                if (idxIsEmpty(KKIDX[i][j])) {
                    KKIDX[i][j] = idx;
                    KKIDX[x][y] = idx;
                    BKSQ[idx] = i;
                    WKSQ[idx] = j;
                    idx++;
                }
            }
        }
    }

    // ============================================================
    // AAIDX — ported from init_aaidx()
    // ============================================================

    static final int[] AABASE = new int[MAX_AAINDEX];
    static final int[][] AAIDX = new int[64][64];

    /** Ported from init_aaidx(). modifies AABASE[], AAIDX[][]. */
    private static void initAaidx() {
        for (int[] row : AAIDX) java.util.Arrays.fill(row, NOINDEX);

        int idx = 0;
        for (int x = 0; x < 64; x++) {
            for (int y = x + 1; y < 64; y++) {
                if (idxIsEmpty(AAIDX[x][y])) {
                    AAIDX[x][y] = idx;
                    AAIDX[y][x] = idx;
                    AABASE[idx] = x;
                    idx++;
                }
            }
        }
    }

    // ============================================================
    // PPIDX — ported from init_ppidx()
    // ============================================================

    static final int[][] PPIDX = new int[24][48];
    static final int[] PP_HI24 = new int[MAX_PPINDEX];
    static final int[] PP_LO48 = new int[MAX_PPINDEX];

    /** Ported from init_ppidx(). modifies PPIDX[][], PP_HI24[], PP_LO48[]. */
    private static void initPpidx() {
        for (int[] row : PPIDX) java.util.Arrays.fill(row, NOINDEX);
        java.util.Arrays.fill(PP_HI24, NOINDEX);
        java.util.Arrays.fill(PP_LO48, NOINDEX);

        int idx = 0;
        for (int a = 55; a >= 8; a--) { // H7 downto A2
            if (squareFile(a) < 4) continue; // queenside only

            for (int b = a - 1; b >= 8; b--) {
                int[] al = ppPutAnchorFirst(a, b);
                int anchor = al[0], loosen = al[1];

                if (squareFile(anchor) > 3) {
                    anchor = flipWe(anchor);
                    loosen = flipWe(loosen);
                }

                int i = wsqToPidx24(anchor);
                int j = wsqToPidx48(loosen);

                if (idxIsEmpty(PPIDX[i][j])) {
                    PPIDX[i][j] = idx;
                    PP_HI24[idx] = i;
                    PP_LO48[idx] = j;
                    idx++;
                }
            }
        }
    }

    // ============================================================
    // FLIPT — ported from init_flipt()
    // ============================================================

    static final int[][] FLIPT = new int[64][64];

    /** Ported from init_flipt(). modifies FLIPT[][]. */
    private static void initFlipt() {
        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 64; j++) {
                FLIPT[i][j] = flipType(i, j);
            }
        }
    }

    // ============================================================
    // AAA — ported from init_aaa()/aaa_getsubi()
    // ============================================================

    static final int[] AAA_BASE = new int[64];
    static final int[][] AAA_XYZ = new int[MAX_AAAINDEX][3];

    /** Ported from aaa_getsubi(). */
    static int aaaGetSubi(int x, int y, int z) {
        return x + (y - 1) * y / 2 + AAA_BASE[z];
    }

    /** Ported from init_aaa(). modifies AAA_BASE[], AAA_XYZ[][]. */
    private static void initAaa() {
        int[] comb = new int[64];
        for (int a = 1; a < 64; a++) comb[a] = a * (a - 1) / 2;

        int accum = 0;
        for (int a = 0; a < 63; a++) {
            accum += comb[a];
            AAA_BASE[a + 1] = accum;
        }

        for (int[] row : AAA_XYZ) java.util.Arrays.fill(row, NOINDEX);

        int idx = 0;
        for (int z = 0; z < 64; z++) {
            for (int y = 0; y < z; y++) {
                for (int x = 0; x < y; x++) {
                    AAA_XYZ[idx][0] = x;
                    AAA_XYZ[idx][1] = y;
                    AAA_XYZ[idx][2] = z;
                    idx++;
                }
            }
        }
    }

    // ============================================================
    // PP48 — ported from init_pp48_idx()
    // ============================================================

    static final int[][] PP48_IDX = new int[48][48];
    static final int[] PP48_SQ_X = new int[MAX_PP48_INDEX];
    static final int[] PP48_SQ_Y = new int[MAX_PP48_INDEX];

    /** Ported from init_pp48_idx(). modifies PP48_IDX[][], PP48_SQ_X[], PP48_SQ_Y[]. */
    private static void initPp48Idx() {
        for (int[] row : PP48_IDX) java.util.Arrays.fill(row, NOINDEX);
        java.util.Arrays.fill(PP48_SQ_X, NOSQUARE);
        java.util.Arrays.fill(PP48_SQ_Y, NOSQUARE);

        int idx = 0;
        for (int a = 55; a >= 8; a--) { // H7 downto A2
            for (int b = a - 1; b >= 8; b--) {
                int i = flipWe(flipNs(a)) - 8;
                int j = flipWe(flipNs(b)) - 8;

                if (idxIsEmpty(PP48_IDX[i][j])) {
                    PP48_IDX[i][j] = idx;
                    PP48_IDX[j][i] = idx;
                    PP48_SQ_X[idx] = i;
                    PP48_SQ_Y[idx] = j;
                    idx++;
                }
            }
        }
    }

    // ============================================================
    // PPP48 — ported from init_ppp48_idx()
    // ============================================================

    static final int[][][] PPP48_IDX = new int[48][48][48];
    static final int[] PPP48_SQ_X = new int[MAX_PPP48_INDEX];
    static final int[] PPP48_SQ_Y = new int[MAX_PPP48_INDEX];
    static final int[] PPP48_SQ_Z = new int[MAX_PPP48_INDEX];

    /** Ported from init_ppp48_idx(). modifies PPP48_IDX[][][], PPP48_SQ_X/Y/Z[]. */
    private static void initPpp48Idx() {
        for (int[][] plane : PPP48_IDX)
            for (int[] row : plane) java.util.Arrays.fill(row, NOINDEX);
        java.util.Arrays.fill(PPP48_SQ_X, NOSQUARE);
        java.util.Arrays.fill(PPP48_SQ_Y, NOSQUARE);
        java.util.Arrays.fill(PPP48_SQ_Z, NOSQUARE);

        int idx = 0;
        for (int x = 0; x < 48; x++) {
            for (int y = x + 1; y < 48; y++) {
                for (int z = y + 1; z < 48; z++) {
                    int a = ITOSQ[x];
                    int b = ITOSQ[y];
                    int c = ITOSQ[z];
                    if (!inQueenside(b) || !inQueenside(c)) continue;

                    int i = a - 8, j = b - 8, k = c - 8;

                    if (idxIsEmpty(PPP48_IDX[i][j][k])) {
                        PPP48_IDX[i][j][k] = idx;
                        PPP48_IDX[i][k][j] = idx;
                        PPP48_IDX[j][i][k] = idx;
                        PPP48_IDX[j][k][i] = idx;
                        PPP48_IDX[k][i][j] = idx;
                        PPP48_IDX[k][j][i] = idx;
                        PPP48_SQ_X[idx] = i;
                        PPP48_SQ_Y[idx] = j;
                        PPP48_SQ_Z[idx] = k;
                        idx++;
                    }
                }
            }
        }
    }

    static {
        initKkidx();
        initAaidx();
        initPpidx();
        initFlipt();
        initAaa();
        initPp48Idx();
        initPpp48Idx();
    }
}
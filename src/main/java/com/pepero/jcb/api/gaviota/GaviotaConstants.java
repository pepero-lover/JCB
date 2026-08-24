package com.pepero.jcb.api.gaviota;

/**
 * Ported from python-chess's chess/gaviota.py (pure-Python Gaviota probing code).
 * Assumes JCB's square numbering is a1=0 LERF (same as python-chess's chess.SQUARES:
 * A1=0, B1=1, ..., H1=7, A2=8, ..., H8=63) — no remapping needed if so.
 * <p>
 * Covers only the deterministic, board-independent static tables and helper
 * functions (flip/twist/index math). The per-material pctoindex() functions,
 * Request/EndgameKey registry, and the LZMA block-decoding engine live in
 * separate files.
 */
final class GaviotaConstants {

    private GaviotaConstants() {}

    // ---- basic constants ----

    static final int NOSQUARE = 64;
    static final int NOINDEX = -1;

    static final int MAX_KKINDEX = 462;
    static final int MAX_PPINDEX = 576;
    static final int MAX_PpINDEX = 24 * 48;
    static final int MAX_AAINDEX = (63 - 62) + (62 / 2 * (127 - 62)) - 1 + 1; // = 2016
    static final int MAX_AAAINDEX = 64 * 21 * 31;
    static final int MAX_PPP48_INDEX = 8648;
    static final int MAX_PP48_INDEX = 1128;

    // ---- derived per-material max-index constants (mirrors gaviota.py's MAX_* block) ----

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

    // ---- ITOSQ: hardcoded a1=0 LERF square numbers, ported from python-chess's
    // chess.H7/G7/.../A2 constants used in gaviota.py's ITOSQ list ----
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

    // ---- basic square math helpers (ported 1:1) ----

    static int map24B(int s) {
        s -= 8;
        return ((s & 3) + s) >> 1;
    }

    static int map88(int x) {
        return x + (x & 56);
    }

    static boolean inQueenside(int x) {
        return (x & (1 << 2)) == 0;
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

    static boolean idxIsEmpty(int x) {
        return x == -1;
    }

    static int squareFile(int sq) {
        return sq & 7;
    }

    static int squareRank(int sq) {
        return sq >> 3;
    }

    // FILE_D / RANK_4 in python-chess are 3 (0-indexed d-file / 4th rank)
    private static final int FILE_D = 3;
    private static final int RANK_4 = 3;

    /**
     * Ported from flip_type(). x = black king square, y = white king square
     * (see call sites: FLIPT[blackKingSq][whiteKingSq] / flipType(blackSq, whiteSq)).
     */
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
            x = flipNwSe(x);
            y = flipNwSe(y);
            ret |= 4;
        }

        return ret;
    }

    /** FLIPT[j][i] = flipType(j, i), for all squares j (black king), i (white king). */
    static final int[][] FLIPT = new int[64][64];
    static {
        for (int j = 0; j < 64; j++) {
            for (int i = 0; i < 64; i++) {
                FLIPT[j][i] = flipType(j, i);
            }
        }
    }

    // ---- PP48 (two same-color pawns, 48-square index) ----

    static final int[][] PP48_IDX = new int[48][48];
    static final int[] PP48_SQ_X = new int[MAX_PP48_INDEX];
    static final int[] PP48_SQ_Y = new int[MAX_PP48_INDEX];

    static {
        for (int[] row : PP48_IDX) java.util.Arrays.fill(row, -1);
        java.util.Arrays.fill(PP48_SQ_X, NOSQUARE);
        java.util.Arrays.fill(PP48_SQ_Y, NOSQUARE);

        int idx = 0;
        // a: H7..A2 descending, b: (a-1)..A2 descending  (H7=55, A2=8)
        for (int a = 55; a >= 8; a--) {
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

    // ---- PPP48 (three same-color pawns) ----

    static final int[][][] PPP48_IDX = new int[48][48][48];
    static final int[] PPP48_SQ_X = new int[MAX_PPP48_INDEX];
    static final int[] PPP48_SQ_Y = new int[MAX_PPP48_INDEX];
    static final int[] PPP48_SQ_Z = new int[MAX_PPP48_INDEX];

    static {
        for (int[][] plane : PPP48_IDX)
            for (int[] row : plane) java.util.Arrays.fill(row, -1);
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

                    int i = a - 8;
                    int j = b - 8;
                    int k = c - 8;

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

    // ---- AA (two same-type non-pawn pieces, unordered pair over 64 squares) ----

    static final int[] AABASE = new int[MAX_AAINDEX];
    static final int[][] AAIDX = new int[64][64];

    static {
        for (int[] row : AAIDX) java.util.Arrays.fill(row, -1);

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

    // ---- AAA (three same-type non-pawn pieces) ----

    static final int[] AAA_BASE = new int[64];
    static final int[][] AAA_XYZ = new int[MAX_AAAINDEX][3];

    static {
        int[] comb = new int[64];
        for (int a = 0; a < 64; a++) comb[a] = a * (a - 1) / 2;

        int accum = 0;
        for (int a = 0; a < 63; a++) {
            accum += comb[a];
            AAA_BASE[a + 1] = accum;
        }

        for (int[] row : AAA_XYZ) java.util.Arrays.fill(row, -1);

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

    static int aaaGetSubi(int x, int y, int z) {
        int bse = AAA_BASE[z];
        return x + (y - 1) * y / 2 + bse;
    }

    // ---- pp_putanchorfirst / wsq_to_pidx24 / wsq_to_pidx48 ----

    /** @return {anchor, loosen} */
    static int[] ppPutAnchorFirst(int a, int b) {
        int rowB = b & 56;
        int rowA = a & 56;

        int anchor = a;
        int loosen = b;

        if (rowB > rowA) {
            anchor = b;
            loosen = a;
        } else if (rowB == rowA) {
            int x = a;
            int col = x & 7;
            int inv = col ^ 7;
            x = (1 << col) | (1 << inv);
            x &= (x - 1);
            int hiA = x;

            x = b;
            col = x & 7;
            inv = col ^ 7;
            x = (1 << col) | (1 << inv);
            x &= (x - 1);
            int hiB = x;

            if (hiB > hiA) {
                anchor = b;
                loosen = a;
            } else if (hiB < hiA) {
                anchor = a;
                loosen = b;
            } else {
                if (a < b) {
                    anchor = a;
                    loosen = b;
                } else {
                    anchor = b;
                    loosen = a;
                }
            }
        }

        return new int[]{anchor, loosen};
    }

    static int wsqToPidx24(int pawn) {
        int sq = pawn;
        sq = flipNs(sq);
        sq -= 8;
        return (sq + (sq & 3)) >> 1;
    }

    static int wsqToPidx48(int pawn) {
        int sq = pawn;
        sq = flipNs(sq);
        sq -= 8;
        return sq;
    }

    // ---- PPIDX (one pawn each side / mixed-file pawn-pair index) ----

    static final int[][] PPIDX = new int[24][48];
    static final int[] PP_HI24 = new int[MAX_PPINDEX];
    static final int[] PP_LO48 = new int[MAX_PPINDEX];

    static {
        for (int[] row : PPIDX) java.util.Arrays.fill(row, -1);
        java.util.Arrays.fill(PP_HI24, -1);
        java.util.Arrays.fill(PP_LO48, -1);

        int idx = 0;
        for (int a = 55; a >= 8; a--) { // H7..A2
            if (inQueenside(a)) continue;

            for (int b = a - 1; b >= 8; b--) {
                int[] anchorLoosen = ppPutAnchorFirst(a, b);
                int anchor = anchorLoosen[0];
                int loosen = anchorLoosen[1];

                if ((anchor & 7) > 3) {
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

    // ---- KKIDX (king pair index, full-board symmetry normalized) ----

    static final int[][] KKIDX = new int[64][64];
    static final int[] WKSQ = new int[MAX_KKINDEX];
    static final int[] BKSQ = new int[MAX_KKINDEX];

    private static boolean kingsAdjacentOrSame(int x, int y) {
        if (x == y) return true;
        int fx = x & 7, rx = x >> 3;
        int fy = y & 7, ry = y >> 3;
        return Math.abs(fx - fy) <= 1 && Math.abs(rx - ry) <= 1;
    }

    /** @return {normX, normY} */
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

    static {
        for (int[] row : KKIDX) java.util.Arrays.fill(row, -1);
        java.util.Arrays.fill(WKSQ, -1);
        java.util.Arrays.fill(BKSQ, -1);

        int idx = 0;
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                if (x != y && !kingsAdjacentOrSame(x, y)) {
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
    }
}

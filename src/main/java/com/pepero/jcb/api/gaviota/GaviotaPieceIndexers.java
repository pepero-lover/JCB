package com.pepero.jcb.api.gaviota;

import java.util.Arrays;
import java.util.function.IntUnaryOperator;

import static com.pepero.jcb.api.gaviota.GaviotaConstants.*;

/**
 * Ported 1:1 from gaviota.py's kxk_pctoindex / kapkb_pctoindex / ... functions.
 * Each takes a GaviotaRequest (post material-key resolution — i.e. req.whitePieceSquares
 * / req.blackPieceSquares, which are already in the correct sub-table piece order and
 * possibly color-reversed) and returns the combinatorial index for that position, or
 * NOINDEX (-1) if the position isn't representable (shouldn't happen for legal input).
 */
final class GaviotaPieceIndexers {

    private GaviotaPieceIndexers() {}

    // ---- small local helpers (mutate a fresh copy, mirroring python's list-comprehension flips) ----

    private static int[] mapped(int[] a, IntUnaryOperator op) {
        int[] r = new int[a.length];
        for (int i = 0; i < a.length; i++) r[i] = op.applyAsInt(a[i]);
        return r;
    }

    private static int[] slice(int[] a, int n) {
        return Arrays.copyOf(a, n);
    }

    private static long NOIDX() {
        return NOINDEX;
    }

    // ================= kxk (K+one non-pawn/pawnless-side vs K, and KPvK too via kpk below) =================

    static long kxkPctoindex(GaviotaRequest c) {
        final long BLOCK_Ax = 64;

        int ft = flipType(c.blackPieceSquares[0], c.whitePieceSquares[0]);
        int[] ws = c.whitePieceSquares;
        int[] bs = c.blackPieceSquares;

        if ((ft & 1) != 0) { ws = mapped(ws, GaviotaConstants::flipWe); bs = mapped(bs, GaviotaConstants::flipWe); }
        if ((ft & 2) != 0) { ws = mapped(ws, GaviotaConstants::flipNs); bs = mapped(bs, GaviotaConstants::flipNs); }
        if ((ft & 4) != 0) { ws = mapped(ws, GaviotaConstants::flipNwSe); bs = mapped(bs, GaviotaConstants::flipNwSe); }

        int ki = KKIDX[bs[0]][ws[0]];
        if (idxIsEmpty(ki)) return NOIDX();

        return ki * BLOCK_Ax + ws[1];
    }

    // ================= kapkb =================

    static long kapkbPctoindex(GaviotaRequest c) {
        final long BLOCK_A = 64L * 64 * 64 * 64;
        final long BLOCK_B = 64L * 64 * 64;
        final long BLOCK_C = 64L * 64;
        final long BLOCK_D = 64;

        int pawn = c.whitePieceSquares[2];
        int wa = c.whitePieceSquares[1];
        int wk = c.whitePieceSquares[0];
        int bk = c.blackPieceSquares[0];
        int ba = c.blackPieceSquares[1];

        if (!(pawn >= 8 && pawn < 56)) return NOIDX();

        if ((pawn & 7) > 3) {
            pawn = flipWe(pawn); wk = flipWe(wk); bk = flipWe(bk); wa = flipWe(wa); ba = flipWe(ba);
        }

        int sq = pawn;
        sq ^= 56;
        sq -= 8;
        int pslice = (sq + (sq & 3)) >> 1;

        return pslice * BLOCK_A + wk * BLOCK_B + bk * BLOCK_C + wa * BLOCK_D + ba;
    }

    // ================= kabpk =================

    static long kabpkPctoindex(GaviotaRequest c) {
        final long BLOCK_A = 64L * 64 * 64 * 64;
        final long BLOCK_B = 64L * 64 * 64;
        final long BLOCK_C = 64L * 64;
        final long BLOCK_D = 64;

        int wk = c.whitePieceSquares[0];
        int wa = c.whitePieceSquares[1];
        int wb = c.whitePieceSquares[2];
        int pawn = c.whitePieceSquares[3];
        int bk = c.blackPieceSquares[0];

        if ((pawn & 7) > 3) {
            pawn = flipWe(pawn); wk = flipWe(wk); bk = flipWe(bk); wa = flipWe(wa); wb = flipWe(wb);
        }

        int pslice = wsqToPidx24(pawn);

        return pslice * BLOCK_A + wk * BLOCK_B + bk * BLOCK_C + wa * BLOCK_D + wb;
    }

    // ================= kabkp =================

    static long kabkpPctoindex(GaviotaRequest c) {
        final long BLOCK_A = 64L * 64 * 64 * 64;
        final long BLOCK_B = 64L * 64 * 64;
        final long BLOCK_C = 64L * 64;
        final long BLOCK_D = 64;

        int pawn = c.blackPieceSquares[1];
        int wa = c.whitePieceSquares[1];
        int wk = c.whitePieceSquares[0];
        int bk = c.blackPieceSquares[0];
        int wb = c.whitePieceSquares[2];

        if (!(pawn >= 8 && pawn < 56)) return NOIDX();

        if ((pawn & 7) > 3) {
            pawn = flipWe(pawn); wk = flipWe(wk); bk = flipWe(bk); wa = flipWe(wa); wb = flipWe(wb);
        }

        int sq = pawn;
        sq -= 8;
        int pslice = (sq + (sq & 3)) >> 1;

        return pslice * BLOCK_A + wk * BLOCK_B + bk * BLOCK_C + wa * BLOCK_D + wb;
    }

    // ================= kaapk =================

    static long kaapkPctoindex(GaviotaRequest c) {
        final long BLOCK_C = MAX_AAINDEX;
        final long BLOCK_B = 64L * BLOCK_C;
        final long BLOCK_A = 64L * BLOCK_B;

        int wk = c.whitePieceSquares[0];
        int wa = c.whitePieceSquares[1];
        int wa2 = c.whitePieceSquares[2];
        int pawn = c.whitePieceSquares[3];
        int bk = c.blackPieceSquares[0];

        if ((pawn & 7) > 3) {
            pawn = flipWe(pawn); wk = flipWe(wk); bk = flipWe(bk); wa = flipWe(wa); wa2 = flipWe(wa2);
        }

        int pslice = wsqToPidx24(pawn);
        int aaCombo = AAIDX[wa][wa2];
        if (idxIsEmpty(aaCombo)) return NOIDX();

        return pslice * BLOCK_A + wk * BLOCK_B + bk * BLOCK_C + aaCombo;
    }

    // ================= kaakp =================

    static long kaakpPctoindex(GaviotaRequest c) {
        final long BLOCK_C = MAX_AAINDEX;
        final long BLOCK_B = 64L * BLOCK_C;
        final long BLOCK_A = 64L * BLOCK_B;

        int wk = c.whitePieceSquares[0];
        int wa = c.whitePieceSquares[1];
        int wa2 = c.whitePieceSquares[2];
        int bk = c.blackPieceSquares[0];
        int pawn = c.blackPieceSquares[1];

        if ((pawn & 7) > 3) {
            pawn = flipWe(pawn); wk = flipWe(wk); bk = flipWe(bk); wa = flipWe(wa); wa2 = flipWe(wa2);
        }
        pawn = flipNs(pawn);

        int pslice = wsqToPidx24(pawn);
        int aaCombo = AAIDX[wa][wa2];
        if (idxIsEmpty(aaCombo)) return NOIDX();

        return pslice * BLOCK_A + wk * BLOCK_B + bk * BLOCK_C + aaCombo;
    }

    // ================= kapkp =================

    static long kapkpPctoindex(GaviotaRequest c) {
        final long BLOCK_A = 64L * 64 * 64;
        final long BLOCK_B = 64L * 64;
        final long BLOCK_C = 64;

        int wk = c.whitePieceSquares[0];
        int wa = c.whitePieceSquares[1];
        int pawnA = c.whitePieceSquares[2];
        int bk = c.blackPieceSquares[0];
        int pawnB = c.blackPieceSquares[1];

        int anchor = pawnA;
        int loosen = pawnB;

        if ((anchor & 7) > 3) {
            anchor = flipWe(anchor); loosen = flipWe(loosen); wk = flipWe(wk); bk = flipWe(bk); wa = flipWe(wa);
        }

        int m = wsqToPidx24(anchor);
        int n = loosen - 8;
        int ppSlice = m * 48 + n;
        if (idxIsEmpty(ppSlice)) return NOIDX();

        return ppSlice * BLOCK_A + wk * BLOCK_B + bk * BLOCK_C + wa;
    }

    // ================= kappk =================

    static long kappkPctoindex(GaviotaRequest c) {
        final long BLOCK_A = 64L * 64 * 64;
        final long BLOCK_B = 64L * 64;
        final long BLOCK_C = 64;

        int wk = c.whitePieceSquares[0];
        int wa = c.whitePieceSquares[1];
        int pawnA = c.whitePieceSquares[2];
        int pawnB = c.whitePieceSquares[3];
        int bk = c.blackPieceSquares[0];

        int[] al = ppPutAnchorFirst(pawnA, pawnB);
        int anchor = al[0], loosen = al[1];

        if ((anchor & 7) > 3) {
            anchor = flipWe(anchor); loosen = flipWe(loosen); wk = flipWe(wk); bk = flipWe(bk); wa = flipWe(wa);
        }

        int i = wsqToPidx24(anchor);
        int j = wsqToPidx48(loosen);
        int ppSlice = PPIDX[i][j];
        if (idxIsEmpty(ppSlice)) return NOIDX();

        return ppSlice * BLOCK_A + wk * BLOCK_B + bk * BLOCK_C + wa;
    }

    // ================= kppka =================

    static long kppkaPctoindex(GaviotaRequest c) {
        final long BLOCK_A = 64L * 64 * 64;
        final long BLOCK_B = 64L * 64;
        final long BLOCK_C = 64;

        int wk = c.whitePieceSquares[0];
        int pawnA = c.whitePieceSquares[1];
        int pawnB = c.whitePieceSquares[2];
        int bk = c.blackPieceSquares[0];
        int ba = c.blackPieceSquares[1];

        int[] al = ppPutAnchorFirst(pawnA, pawnB);
        int anchor = al[0], loosen = al[1];

        if ((anchor & 7) > 3) {
            anchor = flipWe(anchor); loosen = flipWe(loosen); wk = flipWe(wk); bk = flipWe(bk); ba = flipWe(ba);
        }

        int i = wsqToPidx24(anchor);
        int j = wsqToPidx48(loosen);
        int ppSlice = PPIDX[i][j];
        if (idxIsEmpty(ppSlice)) return NOIDX();

        return ppSlice * BLOCK_A + wk * BLOCK_B + bk * BLOCK_C + ba;
    }

    // ================= kabck (K+3 distinct non-pawn vs K+1 distinct non-pawn) =================

    static long kabckPctoindex(GaviotaRequest c) {
        final long BLOCK_A = 64L * 64 * 64;
        final long BLOCK_B = 64L * 64;
        final long BLOCK_C = 64;

        int ft = FLIPT[c.blackPieceSquares[0]][c.whitePieceSquares[0]];
        int[] ws = slice(c.whitePieceSquares, 4);
        int[] bs = slice(c.blackPieceSquares, 1);

        if ((ft & WE_FLAG) != 0) { ws = mapped(ws, GaviotaConstants::flipWe); bs = mapped(bs, GaviotaConstants::flipWe); }
        if ((ft & NS_FLAG) != 0) { ws = mapped(ws, GaviotaConstants::flipNs); bs = mapped(bs, GaviotaConstants::flipNs); }
        if ((ft & NW_SE_FLAG) != 0) { ws = mapped(ws, GaviotaConstants::flipNwSe); bs = mapped(bs, GaviotaConstants::flipNwSe); }

        int ki = KKIDX[bs[0]][ws[0]];
        if (idxIsEmpty(ki)) return NOIDX();

        return ki * BLOCK_A + ws[1] * BLOCK_B + ws[2] * BLOCK_C + ws[3];
    }

    // ================= kabbk (K+A+B+B vs K) =================

    static long kabbkPctoindex(GaviotaRequest c) {
        final long BLOCK_Bx = 64;
        final long BLOCK_Ax = BLOCK_Bx * MAX_AAINDEX;

        int ft = FLIPT[c.blackPieceSquares[0]][c.whitePieceSquares[0]];
        int[] ws = slice(c.whitePieceSquares, 4);
        int[] bs = slice(c.blackPieceSquares, 1);

        if ((ft & WE_FLAG) != 0) { ws = mapped(ws, GaviotaConstants::flipWe); bs = mapped(bs, GaviotaConstants::flipWe); }
        if ((ft & NS_FLAG) != 0) { ws = mapped(ws, GaviotaConstants::flipNs); bs = mapped(bs, GaviotaConstants::flipNs); }
        if ((ft & NW_SE_FLAG) != 0) { ws = mapped(ws, GaviotaConstants::flipNwSe); bs = mapped(bs, GaviotaConstants::flipNwSe); }

        int ki = KKIDX[bs[0]][ws[0]];
        int ai = AAIDX[ws[2]][ws[3]];
        if (idxIsEmpty(ki) || idxIsEmpty(ai)) return NOIDX();

        return ki * BLOCK_Ax + ai * BLOCK_Bx + ws[1];
    }

    // ================= kaabk (K+A+A+B vs K) =================

    static long kaabkPctoindex(GaviotaRequest c) {
        final long BLOCK_Bx = 64;
        final long BLOCK_Ax = BLOCK_Bx * MAX_AAINDEX;

        int ft = FLIPT[c.blackPieceSquares[0]][c.whitePieceSquares[0]];
        int[] ws = slice(c.whitePieceSquares, 4);
        int[] bs = slice(c.blackPieceSquares, 1);

        if ((ft & WE_FLAG) != 0) { ws = mapped(ws, GaviotaConstants::flipWe); bs = mapped(bs, GaviotaConstants::flipWe); }
        if ((ft & NS_FLAG) != 0) { ws = mapped(ws, GaviotaConstants::flipNs); bs = mapped(bs, GaviotaConstants::flipNs); }
        if ((ft & NW_SE_FLAG) != 0) { ws = mapped(ws, GaviotaConstants::flipNwSe); bs = mapped(bs, GaviotaConstants::flipNwSe); }

        int ki = KKIDX[bs[0]][ws[0]];
        int ai = AAIDX[ws[1]][ws[2]];
        if (idxIsEmpty(ki) || idxIsEmpty(ai)) return NOIDX();

        return ki * BLOCK_Ax + ai * BLOCK_Bx + ws[3];
    }

    // ================= kaaak (K+A+A+A vs K) =================

    static long kaaakPctoindex(GaviotaRequest c) {
        final long BLOCK_Ax = MAX_AAAINDEX;

        int[] ws = slice(c.whitePieceSquares, 4);
        int[] bs = slice(c.blackPieceSquares, 1);
        int ft = FLIPT[c.blackPieceSquares[0]][c.whitePieceSquares[0]];

        if ((ft & WE_FLAG) != 0) { ws = mapped(ws, GaviotaConstants::flipWe); bs = mapped(bs, GaviotaConstants::flipWe); }
        if ((ft & NS_FLAG) != 0) { ws = mapped(ws, GaviotaConstants::flipNs); bs = mapped(bs, GaviotaConstants::flipNs); }
        if ((ft & NW_SE_FLAG) != 0) { ws = mapped(ws, GaviotaConstants::flipNwSe); bs = mapped(bs, GaviotaConstants::flipNwSe); }

        if (ws[2] < ws[1]) { int t = ws[1]; ws[1] = ws[2]; ws[2] = t; }
        if (ws[3] < ws[2]) { int t = ws[2]; ws[2] = ws[3]; ws[3] = t; }
        if (ws[2] < ws[1]) { int t = ws[1]; ws[1] = ws[2]; ws[2] = t; }

        int ki = KKIDX[bs[0]][ws[0]];

        if (ws[1] == ws[2] || ws[1] == ws[3] || ws[2] == ws[3]) return NOIDX();

        int ai = aaaGetSubi(ws[1], ws[2], ws[3]);
        if (idxIsEmpty(ki) || idxIsEmpty(ai)) return NOIDX();

        return ki * BLOCK_Ax + ai;
    }

    // ================= kppkp (K+P+P vs K+P) =================

    static long kppkpPctoindex(GaviotaRequest c) {
        final long BLOCK_Ax = (long) MAX_PP48_INDEX * 64 * 64;
        final long BLOCK_Bx = 64L * 64;
        final long BLOCK_Cx = 64;

        int wk = c.whitePieceSquares[0];
        int pawnA = c.whitePieceSquares[1];
        int pawnB = c.whitePieceSquares[2];
        int bk = c.blackPieceSquares[0];
        int pawnC = c.blackPieceSquares[1];

        if ((pawnC & 7) > 3) {
            wk = flipWe(wk); pawnA = flipWe(pawnA); pawnB = flipWe(pawnB); bk = flipWe(bk); pawnC = flipWe(pawnC);
        }

        int i = flipWe(flipNs(pawnA)) - 8;
        int j = flipWe(flipNs(pawnB)) - 8;
        int k = map24B(pawnC);

        int pp48Slice = PP48_IDX[i][j];
        if (idxIsEmpty(pp48Slice)) return NOIDX();

        return k * BLOCK_Ax + pp48Slice * BLOCK_Bx + wk * BLOCK_Cx + bk;
    }

    // ================= kaakb (K+A+A vs K+B) =================

    static long kaakbPctoindex(GaviotaRequest c) {
        final long BLOCK_Bx = 64;
        final long BLOCK_Ax = BLOCK_Bx * MAX_AAINDEX;

        int ft = FLIPT[c.blackPieceSquares[0]][c.whitePieceSquares[0]];
        int[] ws = slice(c.whitePieceSquares, 3);
        int[] bs = slice(c.blackPieceSquares, 2);

        if ((ft & WE_FLAG) != 0) { ws = mapped(ws, GaviotaConstants::flipWe); bs = mapped(bs, GaviotaConstants::flipWe); }
        if ((ft & NS_FLAG) != 0) { ws = mapped(ws, GaviotaConstants::flipNs); bs = mapped(bs, GaviotaConstants::flipNs); }
        if ((ft & NW_SE_FLAG) != 0) { ws = mapped(ws, GaviotaConstants::flipNwSe); bs = mapped(bs, GaviotaConstants::flipNwSe); }

        int ki = KKIDX[bs[0]][ws[0]];
        int ai = AAIDX[ws[1]][ws[2]];
        if (idxIsEmpty(ki) || idxIsEmpty(ai)) return NOIDX();

        return ki * BLOCK_Ax + ai * BLOCK_Bx + bs[1];
    }

    // ================= kabkc (K+A+B vs K+C, all distinct non-pawn) =================

    static long kabkcPctoindex(GaviotaRequest c) {
        final long BLOCK_Ax = 64L * 64 * 64;
        final long BLOCK_Bx = 64L * 64;
        final long BLOCK_Cx = 64;

        int ft = FLIPT[c.blackPieceSquares[0]][c.whitePieceSquares[0]];
        int[] ws = slice(c.whitePieceSquares, 3);
        int[] bs = slice(c.blackPieceSquares, 2);

        if ((ft & WE_FLAG) != 0) { ws = mapped(ws, GaviotaConstants::flipWe); bs = mapped(bs, GaviotaConstants::flipWe); }
        if ((ft & NS_FLAG) != 0) { ws = mapped(ws, GaviotaConstants::flipNs); bs = mapped(bs, GaviotaConstants::flipNs); }
        if ((ft & NW_SE_FLAG) != 0) { ws = mapped(ws, GaviotaConstants::flipNwSe); bs = mapped(bs, GaviotaConstants::flipNwSe); }

        int ki = KKIDX[bs[0]][ws[0]];
        if (idxIsEmpty(ki)) return NOIDX();

        return ki * BLOCK_Ax + ws[1] * BLOCK_Bx + ws[2] * BLOCK_Cx + bs[1];
    }

    // ================= kpkp =================

    static long kpkpPctoindex(GaviotaRequest c) {
        final long BLOCK_Ax = 64L * 64;
        final long BLOCK_Bx = 64;

        int wk = c.whitePieceSquares[0];
        int bk = c.blackPieceSquares[0];
        int pawnA = c.whitePieceSquares[1];
        int pawnB = c.blackPieceSquares[1];

        int anchor = pawnA;
        int loosen = pawnB;

        if ((anchor & 7) > 3) {
            anchor = flipWe(anchor); loosen = flipWe(loosen); wk = flipWe(wk); bk = flipWe(bk);
        }

        int m = wsqToPidx24(anchor);
        int n = loosen - 8;
        int ppSlice = m * 48 + n;
        if (idxIsEmpty(ppSlice)) return NOIDX();

        return ppSlice * BLOCK_Ax + wk * BLOCK_Bx + bk;
    }

    // ================= kppk =================

    static long kppkPctoindex(GaviotaRequest c) {
        final long BLOCK_Ax = 64L * 64;
        final long BLOCK_Bx = 64;

        int wk = c.whitePieceSquares[0];
        int pawnA = c.whitePieceSquares[1];
        int pawnB = c.whitePieceSquares[2];
        int bk = c.blackPieceSquares[0];

        int[] al = ppPutAnchorFirst(pawnA, pawnB);
        int anchor = al[0], loosen = al[1];

        if ((anchor & 7) > 3) {
            anchor = flipWe(anchor); loosen = flipWe(loosen); wk = flipWe(wk); bk = flipWe(bk);
        }

        int i = wsqToPidx24(anchor);
        int j = wsqToPidx48(loosen);
        int ppSlice = PPIDX[i][j];
        if (idxIsEmpty(ppSlice)) return NOIDX();

        return ppSlice * BLOCK_Ax + wk * BLOCK_Bx + bk;
    }

    // ================= kapk =================

    static long kapkPctoindex(GaviotaRequest c) {
        final long BLOCK_Ax = 64L * 64 * 64;
        final long BLOCK_Bx = 64L * 64;
        final long BLOCK_Cx = 64;

        int pawn = c.whitePieceSquares[2];
        int wa = c.whitePieceSquares[1];
        int wk = c.whitePieceSquares[0];
        int bk = c.blackPieceSquares[0];

        if (!(pawn >= 8 && pawn < 56)) return NOIDX();

        if ((pawn & 7) > 3) {
            pawn = flipWe(pawn); wk = flipWe(wk); bk = flipWe(bk); wa = flipWe(wa);
        }

        int sq = pawn;
        sq ^= 56;
        sq -= 8;
        int pslice = (sq + (sq & 3)) >> 1;

        return pslice * BLOCK_Ax + wk * BLOCK_Bx + bk * BLOCK_Cx + wa;
    }

    // ================= kabk (K+A+B vs K) =================

    static long kabkPctoindex(GaviotaRequest c) {
        final long BLOCK_Ax = 64L * 64;
        final long BLOCK_Bx = 64;

        int ft = flipType(c.blackPieceSquares[0], c.whitePieceSquares[0]);
        int[] ws = c.whitePieceSquares;
        int[] bs = c.blackPieceSquares;

        if ((ft & 1) != 0) { ws = mapped(ws, GaviotaConstants::flipWe); bs = mapped(bs, GaviotaConstants::flipWe); }
        if ((ft & 2) != 0) { ws = mapped(ws, GaviotaConstants::flipNs); bs = mapped(bs, GaviotaConstants::flipNs); }
        if ((ft & 4) != 0) { ws = mapped(ws, GaviotaConstants::flipNwSe); bs = mapped(bs, GaviotaConstants::flipNwSe); }

        int ki = KKIDX[bs[0]][ws[0]];
        if (idxIsEmpty(ki)) return NOIDX();

        return ki * BLOCK_Ax + ws[1] * BLOCK_Bx + ws[2];
    }

    // ================= kakp =================

    static long kakpPctoindex(GaviotaRequest c) {
        final long BLOCK_Ax = 64L * 64 * 64;
        final long BLOCK_Bx = 64L * 64;
        final long BLOCK_Cx = 64;

        int pawn = c.blackPieceSquares[1];
        int wa = c.whitePieceSquares[1];
        int wk = c.whitePieceSquares[0];
        int bk = c.blackPieceSquares[0];

        if (!(pawn >= 8 && pawn < 56)) return NOIDX();

        if ((pawn & 7) > 3) {
            pawn = flipWe(pawn); wk = flipWe(wk); bk = flipWe(bk); wa = flipWe(wa);
        }

        int sq = pawn;
        sq -= 8;
        int pslice = (sq + (sq & 3)) >> 1;

        return pslice * BLOCK_Ax + wk * BLOCK_Bx + bk * BLOCK_Cx + wa;
    }

    // ================= kaak (K+A+A vs K) =================

    static long kaakPctoindex(GaviotaRequest c) {
        final long BLOCK_Ax = MAX_AAINDEX;

        int ft = FLIPT[c.blackPieceSquares[0]][c.whitePieceSquares[0]];
        int[] ws = slice(c.whitePieceSquares, 3);
        int[] bs = slice(c.blackPieceSquares, 1);

        if ((ft & WE_FLAG) != 0) { ws = mapped(ws, GaviotaConstants::flipWe); bs = mapped(bs, GaviotaConstants::flipWe); }
        if ((ft & NS_FLAG) != 0) { ws = mapped(ws, GaviotaConstants::flipNs); bs = mapped(bs, GaviotaConstants::flipNs); }
        if ((ft & NW_SE_FLAG) != 0) { ws = mapped(ws, GaviotaConstants::flipNwSe); bs = mapped(bs, GaviotaConstants::flipNwSe); }

        int ki = KKIDX[bs[0]][ws[0]];
        int ai = AAIDX[ws[1]][ws[2]];
        if (idxIsEmpty(ki) || idxIsEmpty(ai)) return NOIDX();

        return ki * BLOCK_Ax + ai;
    }

    // ================= kakb =================

    static long kakbPctoindex(GaviotaRequest c) {
        final long BLOCK_Ax = 64L * 64;
        final long BLOCK_Bx = 64;

        int ft = FLIPT[c.blackPieceSquares[0]][c.whitePieceSquares[0]];
        int[] ws = c.whitePieceSquares.clone();
        int[] bs = c.blackPieceSquares.clone();

        if ((ft & 1) != 0) {
            ws[0] = flipWe(ws[0]); ws[1] = flipWe(ws[1]); bs[0] = flipWe(bs[0]); bs[1] = flipWe(bs[1]);
        }
        if ((ft & 2) != 0) {
            ws[0] = flipNs(ws[0]); ws[1] = flipNs(ws[1]); bs[0] = flipNs(bs[0]); bs[1] = flipNs(bs[1]);
        }
        if ((ft & 4) != 0) {
            ws[0] = flipNwSe(ws[0]); ws[1] = flipNwSe(ws[1]); bs[0] = flipNwSe(bs[0]); bs[1] = flipNwSe(bs[1]);
        }

        int ki = KKIDX[bs[0]][ws[0]];
        if (idxIsEmpty(ki)) return NOIDX();

        return ki * BLOCK_Ax + ws[1] * BLOCK_Bx + bs[1];
    }

    // ================= kpk =================

    static long kpkPctoindex(GaviotaRequest c) {
        final long BLOCK_A = 64L * 64;
        final long BLOCK_B = 64;

        int pawn = c.whitePieceSquares[1];
        int wk = c.whitePieceSquares[0];
        int bk = c.blackPieceSquares[0];

        if (!(pawn >= 8 && pawn < 56)) return NOIDX();

        if ((pawn & 7) > 3) {
            pawn = flipWe(pawn); wk = flipWe(wk); bk = flipWe(bk);
        }

        int sq = pawn;
        sq ^= 56;
        sq -= 8;
        int pslice = (sq + (sq & 3)) >> 1;

        return pslice * BLOCK_A + wk * BLOCK_B + bk;
    }

    // ================= kpppk =================

    static long kpppkPctoindex(GaviotaRequest c) {
        final long BLOCK_A = 64L * 64;
        final long BLOCK_B = 64;

        int wk = c.whitePieceSquares[0];
        int pawnA = c.whitePieceSquares[1];
        int pawnB = c.whitePieceSquares[2];
        int pawnC = c.whitePieceSquares[3];
        int bk = c.blackPieceSquares[0];

        int i = pawnA - 8, j = pawnB - 8, k = pawnC - 8;
        int ppp48Slice = PPP48_IDX[i][j][k];

        if (idxIsEmpty(ppp48Slice)) {
            wk = flipWe(wk); pawnA = flipWe(pawnA); pawnB = flipWe(pawnB); pawnC = flipWe(pawnC); bk = flipWe(bk);
        }

        i = pawnA - 8; j = pawnB - 8; k = pawnC - 8;
        ppp48Slice = PPP48_IDX[i][j][k];
        if (idxIsEmpty(ppp48Slice)) return NOIDX();

        return ppp48Slice * BLOCK_A + wk * BLOCK_B + bk;
    }
}
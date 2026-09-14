package com.pepero.jcb.api.gaviota;

import java.util.Arrays;
import java.util.function.IntUnaryOperator;

import static com.pepero.jcb.api.gaviota.GaviotaConstants.*;

/**
 * One index-computing function per Gaviota material class, derived directly
 * from the {@code *_pctoindex} functions in {@code gtb-probe.c}, part of the
 * Gaviota Tablebases probing code:
 * <pre>
 * Gaviota Tablebases Probing Code
 * Copyright (c) 2010 Miguel A. Ballicora
 * Released under the X11 ("MIT") license — see gtb-probe.c's own header.
 * https://github.com/michiguel/Gaviota-Tablebases
 * </pre>
 * Each function takes a {@link GaviotaRequest} (post material-key resolution
 * — i.e. {@code req.whitePieceSquares}/{@code req.blackPieceSquares}, already
 * in the sub-table's expected piece order and possibly color-reversed) and
 * returns the combinatorial index for that position, or {@code NOINDEX} if
 * the position isn't representable in this sub-table (shouldn't happen for
 * legal input).
 * <p>
 * C's flat {@code SQUARE*} arrays terminated by {@code NOSQUARE} are read
 * here as fixed-length {@code int[]} slices taken off the request's sorted
 * square arrays; the {@code index_t*} out-param + {@code bool_t} return
 * becomes a single {@code long} return, with {@code NOINDEX} standing in
 * for the false/failure case.
 */
final class GaviotaPieceIndexers {

    private GaviotaPieceIndexers() {}

    // ---- helpers: flip a fixed-length square array, or take the first n squares ----

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

    /** Applies flip_type()'s WE/NS/NW_SE flags to a pair of (white, black) square arrays. */
    private static int[][] applyFlipType(int ft, int[] ws, int[] bs) {
        if ((ft & WE_FLAG) != 0) { ws = mapped(ws, GaviotaConstants::flipWe); bs = mapped(bs, GaviotaConstants::flipWe); }
        if ((ft & NS_FLAG) != 0) { ws = mapped(ws, GaviotaConstants::flipNs); bs = mapped(bs, GaviotaConstants::flipNs); }
        if ((ft & NW_SE_FLAG) != 0) { ws = mapped(ws, GaviotaConstants::flipNwSe); bs = mapped(bs, GaviotaConstants::flipNwSe); }
        return new int[][]{ws, bs};
    }

    // ============================================================
    // kxk_pctoindex — K+one non-pawn piece vs lone K
    // ============================================================

    static long kxkPctoindex(GaviotaRequest c) {
        final long BLOCK_A = 64;

        int ft = flipType(c.blackPieceSquares[0], c.whitePieceSquares[0]);
        int[][] flipped = applyFlipType(ft, c.whitePieceSquares, c.blackPieceSquares);
        int[] ws = flipped[0], bs = flipped[1];

        int ki = KKIDX[bs[0]][ws[0]]; // kkidx[black king][white king]
        if (idxIsEmpty(ki)) return NOIDX();

        return ki * BLOCK_A + ws[1];
    }

    // ============================================================
    // kabk_pctoindex — K+A+B vs K (A, B distinct non-pawn white pieces)
    // ============================================================

    static long kabkPctoindex(GaviotaRequest c) {
        final long BLOCK_A = 64L * 64;
        final long BLOCK_B = 64;

        int ft = flipType(c.blackPieceSquares[0], c.whitePieceSquares[0]);
        int[][] flipped = applyFlipType(ft, c.whitePieceSquares, c.blackPieceSquares);
        int[] ws = flipped[0], bs = flipped[1];

        int ki = KKIDX[bs[0]][ws[0]];
        if (idxIsEmpty(ki)) return NOIDX();

        return ki * BLOCK_A + ws[1] * BLOCK_B + ws[2];
    }

    // ============================================================
    // kabkc_pctoindex — K+A+B vs K+C (all distinct non-pawn)
    // ============================================================

    static long kabkcPctoindex(GaviotaRequest c) {
        final long BLOCK_A = 64L * 64 * 64;
        final long BLOCK_B = 64L * 64;
        final long BLOCK_C = 64;

        int ft = FLIPT[c.blackPieceSquares[0]][c.whitePieceSquares[0]];
        int[][] flipped = applyFlipType(ft, slice(c.whitePieceSquares, 3), slice(c.blackPieceSquares, 2));
        int[] ws = flipped[0], bs = flipped[1];

        int ki = KKIDX[bs[0]][ws[0]];
        if (idxIsEmpty(ki)) return NOIDX();

        return ki * BLOCK_A + ws[1] * BLOCK_B + ws[2] * BLOCK_C + bs[1];
    }

    // ============================================================
    // kabck_pctoindex — K+A+B+C vs K (all distinct non-pawn white pieces)
    // ============================================================

    static long kabckPctoindex(GaviotaRequest c) {
        final long BLOCK_A = 64L * 64 * 64;
        final long BLOCK_B = 64L * 64;
        final long BLOCK_C = 64;

        int ft = FLIPT[c.blackPieceSquares[0]][c.whitePieceSquares[0]];
        int[][] flipped = applyFlipType(ft, slice(c.whitePieceSquares, 4), slice(c.blackPieceSquares, 1));
        int[] ws = flipped[0], bs = flipped[1];

        int ki = KKIDX[bs[0]][ws[0]];
        if (idxIsEmpty(ki)) return NOIDX();

        return ki * BLOCK_A + ws[1] * BLOCK_B + ws[2] * BLOCK_C + ws[3];
    }

    // ============================================================
    // kakb_pctoindex — K+A vs K+B (one non-pawn piece per side)
    // ============================================================

    static long kakbPctoindex(GaviotaRequest c) {
        final long BLOCK_A = 64L * 64;
        final long BLOCK_B = 64;

        int ft = FLIPT[c.blackPieceSquares[0]][c.whitePieceSquares[0]];
        int[][] flipped = applyFlipType(ft, c.whitePieceSquares.clone(), c.blackPieceSquares.clone());
        int[] ws = flipped[0], bs = flipped[1];

        int ki = KKIDX[bs[0]][ws[0]];
        if (idxIsEmpty(ki)) return NOIDX();

        return ki * BLOCK_A + ws[1] * BLOCK_B + bs[1];
    }

    // ============================================================
    // kaakb_pctoindex — K+A+A vs K+B (A,A same non-pawn white piece)
    // ============================================================

    static long kaakbPctoindex(GaviotaRequest c) {
        final long BLOCK_B = 64;
        final long BLOCK_A = BLOCK_B * MAX_AAINDEX;

        int ft = FLIPT[c.blackPieceSquares[0]][c.whitePieceSquares[0]];
        int[][] flipped = applyFlipType(ft, slice(c.whitePieceSquares, 3), slice(c.blackPieceSquares, 2));
        int[] ws = flipped[0], bs = flipped[1];

        int ki = KKIDX[bs[0]][ws[0]];
        int ai = AAIDX[ws[1]][ws[2]];
        if (idxIsEmpty(ki) || idxIsEmpty(ai)) return NOIDX();

        return ki * BLOCK_A + ai * BLOCK_B + bs[1];
    }

    // ============================================================
    // kaabk_pctoindex — K+A+A+B vs K
    // ============================================================

    static long kaabkPctoindex(GaviotaRequest c) {
        final long BLOCK_B = 64;
        final long BLOCK_A = BLOCK_B * MAX_AAINDEX;

        int ft = FLIPT[c.blackPieceSquares[0]][c.whitePieceSquares[0]];
        int[][] flipped = applyFlipType(ft, slice(c.whitePieceSquares, 4), slice(c.blackPieceSquares, 1));
        int[] ws = flipped[0], bs = flipped[1];

        int ki = KKIDX[bs[0]][ws[0]];
        int ai = AAIDX[ws[1]][ws[2]];
        if (idxIsEmpty(ki) || idxIsEmpty(ai)) return NOIDX();

        return ki * BLOCK_A + ai * BLOCK_B + ws[3];
    }

    // ============================================================
    // kabbk_pctoindex — K+A+B+B vs K (B,B same non-pawn white piece)
    // ============================================================

    static long kabbkPctoindex(GaviotaRequest c) {
        final long BLOCK_B = 64;
        final long BLOCK_A = BLOCK_B * MAX_AAINDEX;

        int ft = FLIPT[c.blackPieceSquares[0]][c.whitePieceSquares[0]];
        int[][] flipped = applyFlipType(ft, slice(c.whitePieceSquares, 4), slice(c.blackPieceSquares, 1));
        int[] ws = flipped[0], bs = flipped[1];

        int ki = KKIDX[bs[0]][ws[0]];
        int ai = AAIDX[ws[2]][ws[3]];
        if (idxIsEmpty(ki) || idxIsEmpty(ai)) return NOIDX();

        return ki * BLOCK_A + ai * BLOCK_B + ws[1];
    }

    // ============================================================
    // kaaak_pctoindex — K+A+A+A vs K (three identical non-pawn white pieces)
    // ============================================================

    static long kaaakPctoindex(GaviotaRequest c) {
        final long BLOCK_A = MAX_AAAINDEX;

        int ft = FLIPT[c.blackPieceSquares[0]][c.whitePieceSquares[0]];
        int[][] flipped = applyFlipType(ft, slice(c.whitePieceSquares, 4), slice(c.blackPieceSquares, 1));
        int[] ws = flipped[0], bs = flipped[1];

        // sort ws[1..3] ascending (3-element bubble sort, same as gtb-probe.c)
        if (ws[2] < ws[1]) { int t = ws[1]; ws[1] = ws[2]; ws[2] = t; }
        if (ws[3] < ws[2]) { int t = ws[2]; ws[2] = ws[3]; ws[3] = t; }
        if (ws[2] < ws[1]) { int t = ws[1]; ws[1] = ws[2]; ws[2] = t; }

        int ki = KKIDX[bs[0]][ws[0]];
        if (ws[1] == ws[2] || ws[1] == ws[3] || ws[2] == ws[3]) return NOIDX();

        int ai = aaaGetSubi(ws[1], ws[2], ws[3]);
        if (idxIsEmpty(ki) || idxIsEmpty(ai)) return NOIDX();

        return ki * BLOCK_A + ai;
    }

    // ============================================================
    // kapkb_pctoindex — K+A+P vs K+B (white pawn)
    // ============================================================

    static long kapkbPctoindex(GaviotaRequest c) {
        final long BLOCK_A = 64L * 64 * 64 * 64;
        final long BLOCK_B = 64L * 64 * 64;
        final long BLOCK_C = 64L * 64;
        final long BLOCK_D = 64;

        int wk = c.whitePieceSquares[0];
        int wa = c.whitePieceSquares[1];
        int pawn = c.whitePieceSquares[2];
        int bk = c.blackPieceSquares[0];
        int ba = c.blackPieceSquares[1];

        if (!(pawn >= 8 && pawn < 56)) return NOIDX();

        if (squareFile(pawn) > 3) {
            pawn = flipWe(pawn); wk = flipWe(wk); bk = flipWe(bk); wa = flipWe(wa); ba = flipWe(ba);
        }

        int pslice = wsqToPidx24(pawn);

        return pslice * BLOCK_A + wk * BLOCK_B + bk * BLOCK_C + wa * BLOCK_D + ba;
    }

    // ============================================================
    // kabkp_pctoindex — K+A+B vs K+P (black pawn)
    // ============================================================

    static long kabkpPctoindex(GaviotaRequest c) {
        final long BLOCK_A = 64L * 64 * 64 * 64;
        final long BLOCK_B = 64L * 64 * 64;
        final long BLOCK_C = 64L * 64;
        final long BLOCK_D = 64;

        int wk = c.whitePieceSquares[0];
        int wa = c.whitePieceSquares[1];
        int wb = c.whitePieceSquares[2];
        int bk = c.blackPieceSquares[0];
        int pawn = c.blackPieceSquares[1];

        if (!(pawn >= 8 && pawn < 56)) return NOIDX();

        if (squareFile(pawn) > 3) {
            pawn = flipWe(pawn); wk = flipWe(wk); bk = flipWe(bk); wa = flipWe(wa); wb = flipWe(wb);
        }

        // NB: no flipNS here — the pawn square is used as-is (matches gtb-probe.c's
        // kakp_pctoindex/kabkp_pctoindex, which comment out the NS flip for a black pawn).
        int pslice = (pawn - 8 + ((pawn - 8) & 3)) >> 1;

        return pslice * BLOCK_A + wk * BLOCK_B + bk * BLOCK_C + wa * BLOCK_D + wb;
    }

    // ============================================================
    // kpk_pctoindex — K+P vs K (white pawn)
    // ============================================================

    static long kpkPctoindex(GaviotaRequest c) {
        final long BLOCK_A = 64L * 64;
        final long BLOCK_B = 64;

        int wk = c.whitePieceSquares[0];
        int pawn = c.whitePieceSquares[1];
        int bk = c.blackPieceSquares[0];

        if (!(pawn >= 8 && pawn < 56)) return NOIDX();

        if (squareFile(pawn) > 3) {
            pawn = flipWe(pawn); wk = flipWe(wk); bk = flipWe(bk);
        }

        int pslice = wsqToPidx24(pawn);

        return pslice * BLOCK_A + wk * BLOCK_B + bk;
    }

    // ============================================================
    // kppk_pctoindex — K+P+P vs K (two white pawns)
    // ============================================================

    static long kppkPctoindex(GaviotaRequest c) {
        final long BLOCK_A = 64L * 64;
        final long BLOCK_B = 64;

        int wk = c.whitePieceSquares[0];
        int pawnA = c.whitePieceSquares[1];
        int pawnB = c.whitePieceSquares[2];
        int bk = c.blackPieceSquares[0];

        int[] al = ppPutAnchorFirst(pawnA, pawnB);
        int anchor = al[0], loosen = al[1];

        if (squareFile(anchor) > 3) {
            anchor = flipWe(anchor); loosen = flipWe(loosen); wk = flipWe(wk); bk = flipWe(bk);
        }

        int i = wsqToPidx24(anchor);
        int j = wsqToPidx48(loosen);
        int ppSlice = PPIDX[i][j];
        if (idxIsEmpty(ppSlice)) return NOIDX();

        return ppSlice * BLOCK_A + wk * BLOCK_B + bk;
    }

    // ============================================================
    // kakp_pctoindex — K+A vs K+P (black pawn)
    // ============================================================

    static long kakpPctoindex(GaviotaRequest c) {
        final long BLOCK_A = 64L * 64 * 64;
        final long BLOCK_B = 64L * 64;
        final long BLOCK_C = 64;

        int wk = c.whitePieceSquares[0];
        int wa = c.whitePieceSquares[1];
        int bk = c.blackPieceSquares[0];
        int pawn = c.blackPieceSquares[1];

        if (!(pawn >= 8 && pawn < 56)) return NOIDX();

        if (squareFile(pawn) > 3) {
            pawn = flipWe(pawn); wk = flipWe(wk); bk = flipWe(bk); wa = flipWe(wa);
        }

        // NB: no flipNS — see kabkpPctoindex.
        int pslice = (pawn - 8 + ((pawn - 8) & 3)) >> 1;

        return pslice * BLOCK_A + wk * BLOCK_B + bk * BLOCK_C + wa;
    }

    // ============================================================
    // kapk_pctoindex — K+A+P vs K (white pawn)
    // ============================================================

    static long kapkPctoindex(GaviotaRequest c) {
        final long BLOCK_A = 64L * 64 * 64;
        final long BLOCK_B = 64L * 64;
        final long BLOCK_C = 64;

        int wk = c.whitePieceSquares[0];
        int wa = c.whitePieceSquares[1];
        int pawn = c.whitePieceSquares[2];
        int bk = c.blackPieceSquares[0];

        if (!(pawn >= 8 && pawn < 56)) return NOIDX();

        if (squareFile(pawn) > 3) {
            pawn = flipWe(pawn); wk = flipWe(wk); bk = flipWe(bk); wa = flipWe(wa);
        }

        int pslice = wsqToPidx24(pawn);

        return pslice * BLOCK_A + wk * BLOCK_B + bk * BLOCK_C + wa;
    }

    // ============================================================
    // kaak_pctoindex — K+A+A vs K (two identical non-pawn white pieces)
    // ============================================================

    static long kaakPctoindex(GaviotaRequest c) {
        final long BLOCK_A = MAX_AAINDEX;

        int ft = FLIPT[c.blackPieceSquares[0]][c.whitePieceSquares[0]];
        int[][] flipped = applyFlipType(ft, slice(c.whitePieceSquares, 3), slice(c.blackPieceSquares, 1));
        int[] ws = flipped[0], bs = flipped[1];

        int ki = KKIDX[bs[0]][ws[0]];
        int ai = AAIDX[ws[1]][ws[2]];
        if (idxIsEmpty(ki) || idxIsEmpty(ai)) return NOIDX();

        return ki * BLOCK_A + ai;
    }

    // ============================================================
    // kppka_pctoindex — K+P+P vs K+A (two white pawns, one black non-pawn)
    // ============================================================

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

        if (squareFile(anchor) > 3) {
            anchor = flipWe(anchor); loosen = flipWe(loosen); wk = flipWe(wk); bk = flipWe(bk); ba = flipWe(ba);
        }

        int i = wsqToPidx24(anchor);
        int j = wsqToPidx48(loosen);
        int ppSlice = PPIDX[i][j];
        if (idxIsEmpty(ppSlice)) return NOIDX();

        return ppSlice * BLOCK_A + wk * BLOCK_B + bk * BLOCK_C + ba;
    }

    // ============================================================
    // kappk_pctoindex — K+A+P+P vs K (two white pawns, one white non-pawn)
    // ============================================================

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

        if (squareFile(anchor) > 3) {
            anchor = flipWe(anchor); loosen = flipWe(loosen); wk = flipWe(wk); bk = flipWe(bk); wa = flipWe(wa);
        }

        int i = wsqToPidx24(anchor);
        int j = wsqToPidx48(loosen);
        int ppSlice = PPIDX[i][j];
        if (idxIsEmpty(ppSlice)) return NOIDX();

        return ppSlice * BLOCK_A + wk * BLOCK_B + bk * BLOCK_C + wa;
    }

    // ============================================================
    // kapkp_pctoindex — K+A+P vs K+P (one pawn each side)
    // ============================================================

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

        if (squareFile(anchor) > 3) {
            anchor = flipWe(anchor); loosen = flipWe(loosen); wk = flipWe(wk); bk = flipWe(bk); wa = flipWe(wa);
        }

        int m = wsqToPidx24(anchor);
        int n = loosen - 8;
        int ppSlice = m * 48 + n;

        return ppSlice * BLOCK_A + wk * BLOCK_B + bk * BLOCK_C + wa;
    }

    // ============================================================
    // kabpk_pctoindex — K+A+B+P vs K (white pawn, two other white non-pawn pieces)
    // ============================================================

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

        if (squareFile(pawn) > 3) {
            pawn = flipWe(pawn); wk = flipWe(wk); bk = flipWe(bk); wa = flipWe(wa); wb = flipWe(wb);
        }

        int pslice = wsqToPidx24(pawn);

        return pslice * BLOCK_A + wk * BLOCK_B + bk * BLOCK_C + wa * BLOCK_D + wb;
    }

    // ============================================================
    // kaapk_pctoindex — K+A+A+P vs K (white pawn, two identical white non-pawn pieces)
    // ============================================================

    static long kaapkPctoindex(GaviotaRequest c) {
        final long BLOCK_C = MAX_AAINDEX;
        final long BLOCK_B = 64L * BLOCK_C;
        final long BLOCK_A = 64L * BLOCK_B;

        int wk = c.whitePieceSquares[0];
        int wa = c.whitePieceSquares[1];
        int wa2 = c.whitePieceSquares[2];
        int pawn = c.whitePieceSquares[3];
        int bk = c.blackPieceSquares[0];

        if (squareFile(pawn) > 3) {
            pawn = flipWe(pawn); wk = flipWe(wk); bk = flipWe(bk); wa = flipWe(wa); wa2 = flipWe(wa2);
        }

        int pslice = wsqToPidx24(pawn);
        int aaCombo = AAIDX[wa][wa2];
        if (idxIsEmpty(aaCombo)) return NOIDX();

        return pslice * BLOCK_A + wk * BLOCK_B + bk * BLOCK_C + aaCombo;
    }

    // ============================================================
    // kaakp_pctoindex — K+A+A vs K+P (black pawn, two identical white non-pawn pieces)
    // ============================================================

    static long kaakpPctoindex(GaviotaRequest c) {
        final long BLOCK_C = MAX_AAINDEX;
        final long BLOCK_B = 64L * BLOCK_C;
        final long BLOCK_A = 64L * BLOCK_B;

        int wk = c.whitePieceSquares[0];
        int wa = c.whitePieceSquares[1];
        int wa2 = c.whitePieceSquares[2];
        int bk = c.blackPieceSquares[0];
        int pawn = c.blackPieceSquares[1];

        if (squareFile(pawn) > 3) {
            pawn = flipWe(pawn); wk = flipWe(wk); bk = flipWe(bk); wa = flipWe(wa); wa2 = flipWe(wa2);
        }
        pawn = flipNs(pawn);

        int pslice = wsqToPidx24(pawn);
        int aaCombo = AAIDX[wa][wa2];
        if (idxIsEmpty(aaCombo)) return NOIDX();

        return pslice * BLOCK_A + wk * BLOCK_B + bk * BLOCK_C + aaCombo;
    }

    // ============================================================
    // kppkp_pctoindex — K+P+P vs K+P (two white pawns, one black pawn)
    // ============================================================

    static long kppkpPctoindex(GaviotaRequest c) {
        final long BLOCK_A = (long) MAX_PP48_INDEX * 64 * 64;
        final long BLOCK_B = 64L * 64;
        final long BLOCK_C = 64;

        int wk = c.whitePieceSquares[0];
        int pawnA = c.whitePieceSquares[1];
        int pawnB = c.whitePieceSquares[2];
        int bk = c.blackPieceSquares[0];
        int pawnC = c.blackPieceSquares[1];

        if (squareFile(pawnC) > 3) {
            wk = flipWe(wk); pawnA = flipWe(pawnA); pawnB = flipWe(pawnB); bk = flipWe(bk); pawnC = flipWe(pawnC);
        }

        int i = flipWe(flipNs(pawnA)) - 8;
        int j = flipWe(flipNs(pawnB)) - 8;
        int k = map24B(pawnC);

        int pp48Slice = PP48_IDX[i][j];
        if (idxIsEmpty(pp48Slice)) return NOIDX();

        return k * BLOCK_A + pp48Slice * BLOCK_B + wk * BLOCK_C + bk;
    }

    /** Ported from gtb-probe.c's inline pawn-slice math ({@code map24_b}): pawn square -> 0..23 (black-side pawn, no NS flip). */
    private static int map24B(int s) {
        s -= 8;
        return (s + (s & 3)) >> 1;
    }

    // ============================================================
    // kpppk_pctoindex — K+P+P+P vs K (three white pawns)
    // ============================================================

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
            i = pawnA - 8; j = pawnB - 8; k = pawnC - 8;
            ppp48Slice = PPP48_IDX[i][j][k];
        }
        if (idxIsEmpty(ppp48Slice)) return NOIDX();

        return ppp48Slice * BLOCK_A + wk * BLOCK_B + bk;
    }

    // ============================================================
    // kpkp_pctoindex — K+P vs K+P (one pawn each side)
    // ============================================================

    static long kpkpPctoindex(GaviotaRequest c) {
        final long BLOCK_A = 64L * 64;
        final long BLOCK_B = 64;

        int wk = c.whitePieceSquares[0];
        int pawnA = c.whitePieceSquares[1];
        int bk = c.blackPieceSquares[0];
        int pawnB = c.blackPieceSquares[1];

        int anchor = pawnA;
        int loosen = pawnB;

        if (squareFile(anchor) > 3) {
            anchor = flipWe(anchor); loosen = flipWe(loosen); wk = flipWe(wk); bk = flipWe(bk);
        }

        int m = wsqToPidx24(anchor);
        int n = loosen - 8;
        int ppSlice = m * 48 + n;

        return ppSlice * BLOCK_A + wk * BLOCK_B + bk;
    }
}
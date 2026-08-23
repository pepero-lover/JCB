package com.pepero.jcb.api.syzygy;

/**
 * Ported from Fathom's encode() (tbprobe.c). Converts an array of piece
 * squares (already in the sub-table's piece order — see SyzygySubTable's
 * wtmPieces/btmPieces order) into the final combinatorial index used to
 * probe decompress_pairs.
 * <p>
 * NOTE: this function expects p[] to already contain the correct square
 * numbers (0~63) for each piece, IN THE SAME ORDER as the piece-type list
 * for this sub-table/side. Building that p[] array from an actual board
 * position (matching pieces to the expected order, called fill_squares in
 * the original) is a separate step, not implemented here yet — needs to be
 * wired up against JCB's actual board/bitboard representation.
 * <p>
 * p[] IS MUTATED by this method (symmetry transforms + sorting), matching
 * the original C behavior — pass a copy if the caller needs the original order preserved.
 */
class SyzygyEncoder {

    public static long encode(int[] p, SyzygyEncInfo ei, SyzygyMaterial material, SyzygyEncType enc) {
        int n = material.getTotalPieceCount();
        long idx;
        int k;

        // if the "first" piece is on the queen-side half (file e~h has bit 0x04 set
        // in this square numbering), mirror the whole position left-right
        if ((p[0] & 0x04) != 0) {
            for (int i = 0; i < n; i++) p[i] ^= 0x07;
        }

        if (enc == SyzygyEncType.PIECE_ENC) {
            // if on the bottom half of the board, flip vertically too
            if ((p[0] & 0x20) != 0) {
                for (int i = 0; i < n; i++) p[i] ^= 0x38;
            }

            // if any of the first few pieces sits off the main diagonal, flip along
            // the diagonal so the "anchor" piece ends up in the canonical triangle
            for (int i = 0; i < n; i++) {
                if (SyzygyEncodeTables.OFF_DIAG[p[i]] != 0) {
                    if (SyzygyEncodeTables.OFF_DIAG[p[i]] > 0 && i < (material.isKkEnc() ? 2 : 3)) {
                        for (int j = 0; j < n; j++) {
                            p[j] = SyzygyEncodeTables.FLIP_DIAG[p[j]];
                        }
                    }
                    break;
                }
            }

            if (material.isKkEnc()) {
                if (material.isConnectedKings()) {
                    int i2 = (p[1] > p[0]) ? 1 : 0;
                    if (SyzygyEncodeTables.OFF_DIAG[p[0]] != 0) {
                        idx = (long) SyzygyEncodeTables.TRIANGLE[p[0]] * 63 + (p[1] - i2);
                    } else if (SyzygyEncodeTables.OFF_DIAG[p[1]] != 0) {
                        idx = 6L * 63 + (long) SyzygyEncodeTables.DIAG[p[0]] * 28 + SyzygyEncodeTables.LOWER[p[1]];
                    } else {
                        idx = 6L * 63 + 4L * 28 + (long) SyzygyEncodeTables.DIAG[p[0]] * 7
                                + (SyzygyEncodeTables.DIAG[p[1]] - i2);
                    }
                } else {
                    idx = SyzygyIndexTables.KK_IDX[SyzygyEncodeTables.TRIANGLE[p[0]]][p[1]];
                }
                k = 2;
            } else {
                int s1 = (p[1] > p[0]) ? 1 : 0;
                int s2 = ((p[2] > p[0]) ? 1 : 0) + ((p[2] > p[1]) ? 1 : 0);

                if (SyzygyEncodeTables.OFF_DIAG[p[0]] != 0) {
                    idx = (long) SyzygyEncodeTables.TRIANGLE[p[0]] * 63 * 62
                            + (long) (p[1] - s1) * 62 + (p[2] - s2);
                } else if (SyzygyEncodeTables.OFF_DIAG[p[1]] != 0) {
                    idx = 6L * 63 * 62
                            + (long) SyzygyEncodeTables.DIAG[p[0]] * 28 * 62
                            + (long) SyzygyEncodeTables.LOWER[p[1]] * 62 + (p[2] - s2);
                } else if (SyzygyEncodeTables.OFF_DIAG[p[2]] != 0) {
                    idx = 6L * 63 * 62 + 4L * 28 * 62
                            + (long) SyzygyEncodeTables.DIAG[p[0]] * 7 * 28
                            + (long) (SyzygyEncodeTables.DIAG[p[1]] - s1) * 28
                            + SyzygyEncodeTables.LOWER[p[2]];
                } else {
                    idx = 6L * 63 * 62 + 4L * 28 * 62 + 4L * 7 * 28
                            + (long) SyzygyEncodeTables.DIAG[p[0]] * 7 * 6
                            + (long) (SyzygyEncodeTables.DIAG[p[1]] - s1) * 6
                            + (SyzygyEncodeTables.DIAG[p[2]] - s2);
                }
                k = 3;
            }
            idx *= ei.getFactor()[0];
        } else {
            // FILE_ENC -> PAWN_TWIST/PAWN_IDX/FLAP index 0, RANK_ENC -> index 1
            int encIdx = (enc == SyzygyEncType.FILE_ENC) ? 0 : 1;
            int[] pawnCount = material.getPawnCount();

            // sort our own pawns by twist value, descending
            for (int i = 1; i < pawnCount[0]; i++) {
                for (int j = i + 1; j < pawnCount[0]; j++) {
                    if (SyzygyIndexTables.PAWN_TWIST[encIdx][p[i]] < SyzygyIndexTables.PAWN_TWIST[encIdx][p[j]]) {
                        int tmp = p[i]; p[i] = p[j]; p[j] = tmp;
                    }
                }
            }

            k = pawnCount[0];
            idx = SyzygyIndexTables.PAWN_IDX[encIdx][k - 1][SyzygyEncodeTables.FLAP[encIdx][p[0]]];
            for (int i = 1; i < k; i++) {
                idx += SyzygyMaterial.binomial[k - i][SyzygyIndexTables.PAWN_TWIST[encIdx][p[i]]];
            }
            idx *= ei.getFactor()[0];

            // opponent's pawns (only relevant when both sides have pawns)
            if (pawnCount[1] > 0) {
                int t = k + pawnCount[1];
                for (int i = k; i < t; i++) {
                    for (int j = i + 1; j < t; j++) {
                        if (p[i] > p[j]) { int tmp = p[i]; p[i] = p[j]; p[j] = tmp; }
                    }
                }

                long s = 0;
                for (int i = k; i < t; i++) {
                    int sq = p[i];
                    int skips = 0;
                    for (int j = 0; j < k; j++) skips += (sq > p[j]) ? 1 : 0;
                    s += SyzygyMaterial.binomial[i - k + 1][sq - skips - 8];
                }
                idx += s * ei.getFactor()[k];
                k = t;
            }
        }

        // remaining piece groups (non-king/non-pawn), same combinatorial pattern
        while (k < n) {
            int t = k + ei.getNorm()[k];
            for (int i = k; i < t; i++) {
                for (int j = i + 1; j < t; j++) {
                    if (p[i] > p[j]) { int tmp = p[i]; p[i] = p[j]; p[j] = tmp; }
                }
            }

            long s = 0;
            for (int i = k; i < t; i++) {
                int sq = p[i];
                int skips = 0;
                for (int j = 0; j < k; j++) skips += (sq > p[j]) ? 1 : 0;
                s += SyzygyMaterial.binomial[i - k + 1][sq - skips];
            }
            idx += s * ei.getFactor()[k];
            k = t;
        }

        return idx;
    }
}
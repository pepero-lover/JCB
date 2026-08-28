package com.pepero.jcb.api.syzygy;

import static com.pepero.jcb.api.syzygy.SyzygyEncodeTables.*;
import static com.pepero.jcb.api.syzygy.SyzygyIndexTables.MULTIDX;

class SyzygyEncoder {
    public static long encode(int[] p, SyzygyEncInfo ei, SyzygyMaterial material, SyzygyEncType enc) {
        int n = material.getTotalPieceCount();
        long idx = 0;
        int k;

        if (enc == SyzygyEncType.PIECE_ENC) {
            int encTypeCase = material.getEncTypeCase();

            if (encTypeCase < 3) {
                if ((p[0] & 0x04) != 0) { for (int i = 0; i < n; i++) p[i] ^= 0x07; }
                if ((p[0] & 0x20) != 0) { for (int i = 0; i < n; i++) p[i] ^= 0x38; }

                for (int i = 0; i < n; i++) {
                    if (SyzygyEncodeTables.OFF_DIAG[p[i]] != 0) {
                        if (SyzygyEncodeTables.OFF_DIAG[p[i]] > 0 && i < (encTypeCase == 0 ? 3 : 2)) {
                            for (int j = 0; j < n; j++) p[j] = SyzygyEncodeTables.FLIP_DIAG[p[j]];
                        }
                        break;
                    }
                }

                if (encTypeCase == 0) {
                    int s1 = (p[1] > p[0]) ? 1 : 0;
                    int s2 = ((p[2] > p[0]) ? 1 : 0) + ((p[2] > p[1]) ? 1 : 0);

                    if (SyzygyEncodeTables.OFF_DIAG[p[0]] != 0) {
                        idx = (long) SyzygyEncodeTables.TRIANGLE[p[0]] * 63 * 62 + (long) (p[1] - s1) * 62 + (p[2] - s2);
                    } else if (SyzygyEncodeTables.OFF_DIAG[p[1]] != 0) {
                        idx = 6L * 63 * 62 + (long) SyzygyEncodeTables.DIAG[p[0]] * 28 * 62 + (long) SyzygyEncodeTables.LOWER[p[1]] * 62 + (p[2] - s2);
                    } else if (SyzygyEncodeTables.OFF_DIAG[p[2]] != 0) {
                        idx = 6L * 63 * 62 + 4L * 28 * 62 + (long) SyzygyEncodeTables.DIAG[p[0]] * 7 * 28 + (long) (SyzygyEncodeTables.DIAG[p[1]] - s1) * 28 + SyzygyEncodeTables.LOWER[p[2]];
                    } else {
                        idx = 6L * 63 * 62 + 4L * 28 * 62 + 4L * 7 * 28 + (long) SyzygyEncodeTables.DIAG[p[0]] * 7 * 6 + (long) (SyzygyEncodeTables.DIAG[p[1]] - s1) * 6 + (SyzygyEncodeTables.DIAG[p[2]] - s2);
                    }
                    k = 3;
                } else { // encTypeCase == 2
                    if (material.isConnectedKings()) {
                        int i2 = (p[1] > p[0]) ? 1 : 0;
                        if (SyzygyEncodeTables.OFF_DIAG[p[0]] != 0) {
                            idx = (long) SyzygyEncodeTables.TRIANGLE[p[0]] * 63 + (p[1] - i2);
                        } else if (SyzygyEncodeTables.OFF_DIAG[p[1]] != 0) {
                            idx = 6L * 63 + (long) SyzygyEncodeTables.DIAG[p[0]] * 28 + SyzygyEncodeTables.LOWER[p[1]];
                        } else {
                            idx = 6L * 63 + 4L * 28 + (long) SyzygyEncodeTables.DIAG[p[0]] * 7 + (SyzygyEncodeTables.DIAG[p[1]] - i2);
                        }
                    } else {
                        idx = SyzygyIndexTables.KK_IDX[SyzygyEncodeTables.TRIANGLE[p[0]]][p[1]];
                    }
                    k = 2;
                }
            } else if (encTypeCase == 3) {
                if (SyzygyEncodeTables.TRIANGLE[p[0]] > SyzygyEncodeTables.TRIANGLE[p[1]]) {
                    int tmp = p[0]; p[0] = p[1]; p[1] = tmp;
                }
                if ((p[0] & 0x04) != 0) { for (int i = 0; i < n; i++) p[i] ^= 0x07; }
                if ((p[0] & 0x20) != 0) { for (int i = 0; i < n; i++) p[i] ^= 0x38; }
                if (SyzygyEncodeTables.OFF_DIAG[p[0]] > 0 || (SyzygyEncodeTables.OFF_DIAG[p[0]] == 0 && SyzygyEncodeTables.OFF_DIAG[p[1]] > 0)) {
                    for (int i = 0; i < n; i++) p[i] = SyzygyEncodeTables.FLIP_DIAG[p[i]];
                }
                if (TEST45[p[1]] && SyzygyEncodeTables.TRIANGLE[p[0]] == SyzygyEncodeTables.TRIANGLE[p[1]]) {
                    int tmp = p[0]; p[0] = p[1]; p[1] = tmp;
                    for (int i = 0; i < n; i++) p[i] = SyzygyEncodeTables.FLIP_DIAG[p[i] ^ 0x38];
                }
                idx = PP_IDX[SyzygyEncodeTables.TRIANGLE[p[0]]][p[1]];
                k = 2;
            } else {
                int norm0 = ei.getNorm()[0];
                for (int i = 1; i < norm0; i++) {
                    if (SyzygyEncodeTables.TRIANGLE[p[0]] > SyzygyEncodeTables.TRIANGLE[p[i]]) {
                        int tmp = p[0]; p[0] = p[i]; p[i] = tmp;
                    }
                }
                if ((p[0] & 0x04) != 0) { for (int i = 0; i < n; i++) p[i] ^= 0x07; }
                if ((p[0] & 0x20) != 0) { for (int i = 0; i < n; i++) p[i] ^= 0x38; }
                if (SyzygyEncodeTables.OFF_DIAG[p[0]] > 0) {
                    for (int i = 0; i < n; i++) p[i] = SyzygyEncodeTables.FLIP_DIAG[p[i]];
                }
                for (int i = 1; i < norm0; i++) {
                    for (int j = i + 1; j < norm0; j++) {
                        if (MTWIST[p[i]] > MTWIST[p[j]]) {
                            int tmp = p[i]; p[i] = p[j]; p[j] = tmp;
                        }
                    }
                }
                idx = MULTIDX[norm0 - 1][SyzygyEncodeTables.TRIANGLE[p[0]]];
                for (int i = 1; i < norm0; i++) {
                    idx += SyzygyMaterial.binomial[i][MTWIST[p[i]]];
                }
                k = norm0;
            }
            idx *= ei.getFactor()[0];

        } else {
            // FILE_ENC
            if ((p[0] & 0x04) != 0) { for (int i = 0; i < n; i++) p[i] ^= 0x07; }
            int encIdx = (enc == SyzygyEncType.FILE_ENC) ? 0 : 1;
            int[] pawnCount = material.getPawnCount();

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
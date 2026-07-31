package com.pepero.jcb.api.syzygy.logics;

public class SyzygyEncInfo {
    private final int[] norm;
    private final long[] factor;
    private final long tbSize;

    private SyzygyEncInfo(int[] norm, long[] factor, long tbSize) {
        this.norm = norm;
        this.factor = factor;
        this.tbSize = tbSize;
    }

    public int[] getNorm() {
        return norm;
    }

    public long[] getFactor() {
        return factor;
    }

    public long getTbSize() {
        return tbSize;
    }

    public static SyzygyEncInfo build(
        SyzygySubTable subTable,
        boolean isWtm,
        SyzygyMaterial material,
        int fileClassIndex,
        SyzygyEncType encType
    ) {
        int totalPieceCount = material.getTotalPieceCount();
        int[] pieces = isWtm ? subTable.wtmPieces() : subTable.btmPieces();
        int order = isWtm ? subTable.orderWtm() : subTable.orderBtm();
        int order2 = isWtm ? subTable.order2Wtm() : subTable.order2Btm();

        boolean morePawns = encType != SyzygyEncType.PIECE_ENC
                && material.getPawnCount()[1] > 0;

        int[] norm = new int[totalPieceCount];


        int k = norm[0] = (encType != SyzygyEncType.PIECE_ENC)
                ? material.getPawnCount()[0]
                : (material.isKkEnc() ? 2 : 3);

        if (morePawns) {
            norm[k] = material.getPawnCount()[1];
            k += norm[k];
        }

        for (int i = k; i < totalPieceCount; i += norm[i]) {
            for (int j = i; j < totalPieceCount && pieces[j] == pieces[i]; j++) {
                norm[i]++;
            }
        }

        int n = 64 - k;
        long f = 1;
        long[] factor = new long[totalPieceCount];

        for (int i = 0; k < totalPieceCount || i == order || i == order2; i++) {
            if (i == order) {
                factor[0] = f;
                if (encType == SyzygyEncType.FILE_ENC) {
                    f *= SyzygyIndexTables.PAWN_FACTOR_FILE[norm[0] - 1][fileClassIndex];
                } else if (encType == SyzygyEncType.RANK_ENC) {
                    f *= SyzygyIndexTables.PAWN_FACTOR_RANK[norm[0] - 1][fileClassIndex];
                } else {
                    f *= material.isKkEnc() ? 462 : 31332;
                }
            } else if (i == order2) {
                factor[norm[0]] = f;
                f *= SyzygyMaterial.subfactor(norm[norm[0]], 48 - norm[0]);
            } else {
                factor[k] = f;
                f *= SyzygyMaterial.subfactor(norm[k], n);
                n -= norm[k];
                k += norm[k];
            }
        }

        return new SyzygyEncInfo(norm, factor, f);
    }
}
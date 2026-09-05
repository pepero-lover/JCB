package com.pepero.jcb.api.syzygy;

/**
 * Ported from the DTZ branch of probe_table() (tbprobe.c, ~line 1885~1892).
 * Takes the raw decompress_pairs() output and turns it into the actual DTZ distance.
 */
class SyzygyDtzPostProcess {

    // WdlToMap[wdlResult + 2] -> which of the 4 dtzMap buckets to use
    private static final int[] WDL_TO_MAP = {1, 3, 0, 2, 0};

    // PAFlags[wdlResult + 2] -> flag bit checked for the final *2 parity adjustment
    private static final int[] PA_FLAGS = {8, 0, 0, 0, 4};

    /**
     * @param header    the mapped tablebase file (needed to actually read the dtzMap entries)
     * @param w0        first leaf byte from SyzygyDecompressor.decompressPairsRaw
     * @param w1        second leaf byte from SyzygyDecompressor.decompressPairsRaw
     * @param wdlResult the WDL result for this SAME position, on our 0~4 scale
     *                  (0=Loss .. 4=Win), e.g. from SyzygyProbe.probeWdl
     * @param flags     the flags byte from this sub-table's SyzygyPairsHeader
     * @param mapEntry  this sub-table's dtzMap entry, or null if flags bit 2 is unset
     * @return the actual DTZ distance
     */
    public static int postProcess(SyzygyMappedFile header, int w0, int w1, int wdlResult,
                                  int flags, SyzygyDtzMapEntry mapEntry) {
        int s = wdlResult - 2;
        int v = w0 + ((w1 & 0x0f) << 8);

        if ((flags & 2) != 0) {
            int m = WDL_TO_MAP[s + 2];
            v = mapEntry.valueAt(header, m, v);
        }

        if ((flags & PA_FLAGS[s + 2]) == 0 || (s & 1) != 0) {
            v *= 2;
        }

        v += 1;

        if ((s & 1) != 0) {
            v += 100;
        }

        if (s < 0) {
            v = -v;
        }

        return v;
    }
}
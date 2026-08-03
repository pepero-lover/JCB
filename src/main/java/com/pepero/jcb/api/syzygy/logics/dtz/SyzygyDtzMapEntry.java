package com.pepero.jcb.api.syzygy.logics.dtz;

import com.pepero.jcb.api.syzygy.logics.SyzygyByteReader;

import java.nio.ByteBuffer;

/**
 * The DTZ remap table for ONE sub-table. Fathom stores 4 separate remap arrays
 * per sub-table (indexed by "m" = WdlToMap[wdlResult+2], i.e. which WDL bucket
 * the position falls into), used to translate the raw decompressed value into
 * the real DTZ distance.
 * <p>
 * "wide" means each entry is 2 bytes (uint16); otherwise each entry is 1 byte.
 */
public record SyzygyDtzMapEntry(boolean wide, int[] absOffsets) {

    /**
     * Look up remapped value #v within bucket m.
     *
     * @param header full file bytes
     * @param m      which of the 4 buckets (0~3), from WdlToMap[wdlResult + 2]
     * @param v      the raw index (decompress_pairs' leaf value) to remap
     * @return the remapped value
     */
    public int valueAt(ByteBuffer header, int m, int v) {
        int base = absOffsets[m];
        return wide
                ? SyzygyByteReader.readU16(header, base + 2 * v)
                : SyzygyByteReader.readU8(header, base + v);
    }
}
package com.pepero.jcb.api.syzygy;

import java.nio.ByteBuffer;

import static com.pepero.jcb.api.syzygy.SyzygyByteReader.*;

/**
 * Ported from the DTZ-specific section of init_table() (tbprobe.c, lines ~1607~1631).
 * This section sits AFTER all the pairs headers and BEFORE the indexTable/sizeTable/
 * block-data region — it's completely absent for WDL files, only present for DTZ.
 * <p>
 * For each sub-table (DTZ never splits, so one entry per sub-table, not per side):
 *   if (flags & 2) == 0:  no remap needed for this sub-table at all (skip)
 *   else if (flags & 16) == 0 ("narrow"): 4 arrays, each [1-byte length][that many raw bytes]
 *   else ("wide"): align to even offset, then 4 arrays, each [2-byte count][that many u16 values]
 * followed by one final even-alignment for the whole section.
 */
class SyzygyDtzMapParser {

    record Result(SyzygyDtzMapEntry[] perTable, int nextOffset) {}

    /**
     * @param header      full file bytes
     * @param startOffset file offset right after all pairs headers (i.e. right where the
     *                    dtzMap section begins, if this material needs one at all)
     * @param dtzHeaders  the pairs header for each sub-table (DTZ has no "side" dimension,
     *                    so this is pairsHeaders[t][0] for t = 0..numTables-1)
     * @return per-table remap entries (null for tables that don't need remapping) + next offset
     */
    public static Result parse(ByteBuffer header, int startOffset, SyzygyPairsHeader[] dtzHeaders) {
        int numTables = dtzHeaders.length;
        SyzygyDtzMapEntry[] result = new SyzygyDtzMapEntry[numTables];

        int offset = startOffset;

        for (int t = 0; t < numTables; t++) {
            int flags = dtzHeaders[t].flags();

            // bit 1 (value 2): does this sub-table need remapping at all?
            if ((flags & 2) == 0) {
                result[t] = null;
                continue;
            }

            // bit 4 (value 16): narrow (1-byte entries) vs wide (2-byte entries)
            boolean wide = (flags & 16) != 0;
            int[] absOffsets = new int[4];

            if (!wide) {
                for (int i = 0; i < 4; i++) {
                    int length = readU8(header, offset);
                    absOffsets[i] = offset + 1; // element 0 starts right after the length byte
                    offset += 1 + length;
                }
            } else {
                // align to an even offset before reading wide (2-byte) entries
                if (offset % 2 != 0) offset += 1;

                for (int i = 0; i < 4; i++) {
                    int count = readU16(header, offset);
                    absOffsets[i] = offset + 2; // element 0 starts right after the 2-byte count
                    offset += 2 + 2 * count;
                }
            }

            result[t] = new SyzygyDtzMapEntry(wide, absOffsets);
        }

        // one final alignment after the whole section
        if (offset % 2 != 0) offset += 1;

        return new Result(result, offset);
    }
}
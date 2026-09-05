package com.pepero.jcb.api.syzygy;

import static com.pepero.jcb.api.syzygy.SyzygyByteReader.*;

/**
 * Computes and parses the indexTable / sizeTable / compressed-block region
 * that follows all the pairs headers in a Syzygy file.
 * <p>
 * IMPORTANT: the real layout is NOT "indexTable, sizeTable, data" repeated per
 * sub-table+side. It's grouped in three separate passes across ALL sub-table+side
 * entries (matching Fathom's init_table second pass):
 *   1. ALL indexTables (one per sub-table+side, in order)
 *   2. ALL sizeTables  (one per sub-table+side, in order)
 *   3. ALL block data  (one per sub-table+side, in order), each one
 *      64-byte aligned (padded up to the next multiple of 64) before it starts.
 * <p>
 * Each "entry" here corresponds to one (sub-table, side) pair — i.e. one
 * SyzygyPairsHeader (skip constant-flag ones entirely, they have no index/size/data).
 * <p>
 * All offsets are {@code long}: for 7-piece tablebases the block-data region alone
 * can sit tens of GB into the file, well past {@link Integer#MAX_VALUE}.
 */
class SyzygyBlockLayout {

    /** Per-entry layout info (one per non-constant sub-table+side). */
    record Entry(
            long tbSize,
            int idxBits,
            long numBlocks,
            int blockSize,
            long indexTableSize,   // size[0]
            long sizeTableSize,    // size[1]
            long blockDataSize,    // size[2]
            long indexTableOffset,
            long sizeTableOffset,
            long blockDataOffset
    ) {}

    private final Entry[] entries;
    private final int[][] entryIndexOf; // [t][s] -> index into entries[], or -1 if that (t,s) was constant
    private final long nextOffset; // file offset right after everything (start of next material's data, if any)

    private SyzygyBlockLayout(Entry[] entries, int[][] entryIndexOf, long nextOffset) {
        this.entries = entries;
        this.entryIndexOf = entryIndexOf;
        this.nextOffset = nextOffset;
    }

    public Entry[] getEntries() {
        return entries;
    }

    /**
     * Map a (sub-table, side) pair to its position in {@link #getEntries()}.
     * Constant sub-tables (see {@code SyzygyPairsHeader.isConstant()}) contribute no
     * index/size/data region at all, so naive {@code t * sides + side} arithmetic breaks
     * as soon as ANY earlier (t,s) entry was constant (it shifts every later index down).
     * Callers must check isConstant() themselves BEFORE calling this — this returns -1
     * for constant entries since there's no corresponding Entry to return.
     */
    public int getEntryIndex(int t, int s) {
        return entryIndexOf[t][s];
    }

    public long getNextOffset() {
        return nextOffset;
    }

    /**
     * Compute the full layout for ALL (sub-table, side) entries at once.
     *
     * @param startOffset file offset right after all pairs headers
     * @param tbSizes     tbSize per entry, in the same [table][side] order as pairsHeaders
     * @param pairsHeaders  the parsed pairs headers (same shape as tbSizes); entries with
     *                      isConstant()==true are skipped (contribute no index/size/data)
     * @return computed layout
     */
    public static SyzygyBlockLayout compute(long startOffset, long[][] tbSizes, SyzygyPairsHeader[][] pairsHeaders) {
        int subTableCount = pairsHeaders.length;
        int sides = pairsHeaders[0].length;

        // flatten in [table][side] order, skipping constant entries
        java.util.List<Entry> entryList = new java.util.ArrayList<>();
        java.util.List<int[]> indexPositions = new java.util.ArrayList<>(); // just for bookkeeping (t,s)

        for (int t = 0; t < subTableCount; t++) {
            for (int s = 0; s < sides; s++) {
                SyzygyPairsHeader h = pairsHeaders[t][s];
                if (h.isConstant()) continue; // no index/size/data region for constant tables

                long tbSize = tbSizes[t][s];
                long numBlocks = h.realNumBlocks() + h.delta();

                long numIndices = (tbSize + (1L << h.idxBits()) - 1) >>> h.idxBits();
                long indexTableSize = 6L * numIndices;
                long sizeTableSize = 2L * numBlocks;
                long blockDataSize = h.realNumBlocks() * (1L << h.blockSize());

                // offsets filled in below, in three passes
                indexPositions.add(new int[]{t, s});
                entryList.add(new Entry(tbSize, h.idxBits(), numBlocks, h.blockSize(),
                        indexTableSize, sizeTableSize, blockDataSize,
                        -1L, -1L, -1L));
            }
        }

        int[][] entryIndexOf = new int[subTableCount][sides];
        for (int[] row : entryIndexOf) java.util.Arrays.fill(row, -1);
        for (int i = 0; i < indexPositions.size(); i++) {
            int[] ts = indexPositions.get(i);
            entryIndexOf[ts[0]][ts[1]] = i; // list order == final entries[] order, unaffected by later offset computation
        }

        int n = entryList.size();
        long[] indexOffsets = new long[n];
        long[] sizeOffsets = new long[n];
        long[] dataOffsets = new long[n];

        long offset = startOffset;

        // pass 1: all indexTables
        for (int i = 0; i < n; i++) {
            indexOffsets[i] = offset;
            offset += entryList.get(i).indexTableSize();
        }

        // pass 2: all sizeTables
        for (int i = 0; i < n; i++) {
            sizeOffsets[i] = offset;
            offset += entryList.get(i).sizeTableSize();
        }

        // pass 3: all block data, each 64-byte aligned before it starts
        for (int i = 0; i < n; i++) {
            offset = alignUp64(offset);
            dataOffsets[i] = offset;
            offset += entryList.get(i).blockDataSize();
        }

        Entry[] finalEntries = new Entry[n];
        for (int i = 0; i < n; i++) {
            Entry e = entryList.get(i);
            finalEntries[i] = new Entry(e.tbSize(), e.idxBits(), e.numBlocks(), e.blockSize(),
                    e.indexTableSize(), e.sizeTableSize(), e.blockDataSize(),
                    indexOffsets[i], sizeOffsets[i], dataOffsets[i]);
        }

        return new SyzygyBlockLayout(finalEntries, entryIndexOf, offset);
    }

    private static long alignUp64(long offset) {
        // round up to next multiple of 64 (matches C's (x + 0x3f) & ~0x3f)
        return (offset + 0x3f) & ~0x3fL;
    }

    /**
     * Read one indexTable entry (6 bytes: little-endian 48-bit value) for the given Entry.
     */
    public long readIndexEntry(SyzygyMappedFile header, Entry entry, long entryIdx) {
        long off = entry.indexTableOffset() + entryIdx * 6;
        long lo = readU32(header, off);
        long hi = readU16(header, off + 4);
        return lo | (hi << 32);
    }

    /**
     * Read one sizeTable entry (2 bytes, little-endian) for the given Entry.
     */
    public int readSizeEntry(SyzygyMappedFile header, Entry entry, long blockIdx) {
        long off = entry.sizeTableOffset() + blockIdx * 2;
        return readU16(header, off);
    }
}
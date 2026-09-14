package com.pepero.jcb.api.gaviota;

import java.nio.ByteBuffer;

import static com.pepero.jcb.api.gaviota.GaviotaConstants.ENTRIES_PER_BLOCK;

/**
 * Ported from gtb-probe.c's {@code egtb_loadindexes()}/{@code egtb_block_getnumber()}/
 * {@code egtb_block_getsize()}/{@code egtb_block_getsize_zipped()}/{@code egtb_block_park()}/
 * {@code split_index()}. These turn a probe index into (which block holds it,
 * where that block's compressed bytes live in the file, how big it is).
 */
final class GaviotaBlockIndex {

    private GaviotaBlockIndex() {}

    /**
     * Reads the 10-uint32 file header + trailing block-offset table.
     * Ported from gtb-probe.c's {@code egtb_loadindexes()}, which reads 10
     * little-endian uint32 fields via {@code fread32()} (only field 8, the
     * data-start offset, is actually used — the other 9 reads exist in the C
     * just to advance the file position past reserved/blocksize/tailblocksize
     * fields this port doesn't otherwise need): header[8] (0-indexed) gives
     * the file offset where the actual block data starts ({@code offset} in
     * the C); everything between the 40-byte header and that offset is the
     * block-offset index table itself (one uint32 per block, plus one
     * trailing entry marking the end of the last block, per
     * {@code blocks = (offset - 40) / 4 - 1; n_idx = blocks + 1}).
     */
    static GaviotaZipInfo loadIndexes(ByteBuffer header) {
        long[] hdr = new long[10];
        for (int i = 0; i < 10; i++) {
            hdr[i] = GaviotaByteReader.readU32LE(header, i * 4);
        }

        long dataStartOffset = hdr[8];
        long blocks = ((dataStartOffset - 40) / 4) - 1;
        int nIdx = (int) (blocks + 1);

        long[] blockIndex = new long[nIdx];
        int base = 40; // 10 * 4 bytes
        for (int i = 0; i < nIdx; i++) {
            blockIndex[i] = GaviotaByteReader.readU32LE(header, base + i * 4);
        }

        return new GaviotaZipInfo(0, nIdx, blockIndex);
    }

    /** Ported from gtb-probe.c's {@code egtb_block_getsize_zipped()}: compressed byte length of one block. */
    static long getSizeZipped(GaviotaZipInfo zipInfo, int block) {
        long i = zipInfo.blockIndex[block];
        long j = zipInfo.blockIndex[block + 1];
        return j - i;
    }

    /** Ported from gtb-probe.c's {@code egtb_block_park()}: absolute file offset where block's compressed bytes start. */
    static long park(GaviotaZipInfo zipInfo, int block) {
        return zipInfo.blockIndex[block] + zipInfo.extraOffset;
    }

    /**
     * Ported from gtb-probe.c's {@code egtb_block_getnumber()}: which block (within
     * this table's file, across both side-to-move halves) holds probe index {@code idx}.
     */
    static int getBlockNumber(GaviotaEndgameKey key, int side, long idx) {
        long maxIndex = key.maxIndex();
        long blocksPerSide = 1 + (maxIndex - 1) / ENTRIES_PER_BLOCK;
        long blockInSide = idx / ENTRIES_PER_BLOCK;
        return (int) (side * blocksPerSide + blockInSide);
    }

    /**
     * Ported from gtb-probe.c's {@code egtb_block_getsize()}: uncompressed entry count
     * for the block containing {@code idx} (the last block of a table may be shorter
     * than ENTRIES_PER_BLOCK).
     */
    static int getBlockSize(GaviotaEndgameKey key, long idx) {
        long blocksz = ENTRIES_PER_BLOCK;
        long maxIndex = key.maxIndex();
        long block = idx / blocksz;
        long offset = block * blocksz;

        if ((offset + blocksz) > maxIndex) {
            return (int) (maxIndex - offset);
        }
        return (int) blocksz;
    }

    /**
     * Ported from gtb-probe.c's {@code split_index()}, with one deliberate scale
     * change: the C version returns {@code o = blockNumber * entries_per_block}
     * (an absolute index) as its "offset", used only as a cache-equality key
     * ({@code dtm_cache_pointblock()} compares it with {@code ==}). This port
     * returns the bare block number instead ({@code idx / ENTRIES_PER_BLOCK}) —
     * still a bijective, consistent cache key, just without the extra multiply,
     * since nothing here needs the absolute-index form. {@code remainder}
     * ({@code idx % ENTRIES_PER_BLOCK}) is unchanged and matches the C exactly.
     *
     * @return {blockNumber, remainderWithinBlock}
     */
    static long[] splitIndex(long idx) {
        return new long[]{idx / ENTRIES_PER_BLOCK, idx % ENTRIES_PER_BLOCK};
    }
}
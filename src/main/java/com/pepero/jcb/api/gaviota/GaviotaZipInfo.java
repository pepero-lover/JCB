package com.pepero.jcb.api.gaviota;

/**
 * Ported from gaviota.py's ZipInfo dataclass. blockIndex[i]/blockIndex[i+1]
 * are the byte offsets bounding compressed block i within the file (after
 * extraOffset is added) — see egtb_block_getsize_zipped()/egtb_block_park().
 */
final class GaviotaZipInfo {
    final long extraOffset;
    final int totalBlocks;
    final long[] blockIndex; // unsigned 32-bit values widened to long

    GaviotaZipInfo(long extraOffset, int totalBlocks, long[] blockIndex) {
        this.extraOffset = extraOffset;
        this.totalBlocks = totalBlocks;
        this.blockIndex = blockIndex;
    }
}
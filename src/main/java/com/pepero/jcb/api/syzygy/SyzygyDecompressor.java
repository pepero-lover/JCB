package com.pepero.jcb.api.syzygy;

import java.nio.ByteBuffer;

import static com.pepero.jcb.api.syzygy.SyzygyByteReader.*;

/**
 * Ported from Fathom's decompress_pairs() (the DECOMP64 variant — Java's long is
 * naturally 64-bit, so we always use this path, unlike the C code which picks
 * 32-bit or 64-bit depending on a compile flag).
 * <p>
 * IMPORTANT gotchas carried over from the original C, all stemming from the same
 * root cause (Java has no unsigned primitives):
 * - The compressed bit-stream itself is BIG-endian (unlike every other header
 *   field parsed so far, which was little-endian).
 * - "code" and "base[]" are logically unsigned 64-bit values. Subtraction (code - base[l])
 *   works fine with plain '-' (two's-complement bit pattern is identical either way),
 *   but COMPARISONS must use Long.compareUnsigned, and right-shifts must use '>>>'
 *   (which Java already does correctly for both int and long).
 */
class SyzygyDecompressor {

    /**
     * Decode the leaf value at the given within-table index.
     *
     * @param data     full file bytes
     * @param entry    the index/size/block-data layout for this (sub-table, side)
     * @param huffman  the Huffman table (base/offset/symLen/symPat) for this same (sub-table, side)
     * @param idx      the position index within this sub-table (0-based, < tbSize)
     * @return the raw leaf byte (for WDL tables, this IS the WDL result code 0~4)
     */
    public static int decompressPairs(ByteBuffer data, SyzygyBlockLayout.Entry entry,
                                      SyzygyHuffmanTable huffman, long idx) {
        return decompressPairsRaw(data, entry, huffman, idx)[0];
    }

    /**
     * Same as {@link #decompressPairs}, but returns both leaf bytes (w0, w1) instead
     * of just w0. WDL only ever needs w0 (the result code), but DTZ needs w1 too
     * (the raw value is a 12-bit number: w0 + ((w1 & 0x0f) << 8)).
     *
     * @return int[]{w0, w1}
     */
    public static int[] decompressPairsRaw(ByteBuffer data, SyzygyBlockLayout.Entry entry,
                                           SyzygyHuffmanTable huffman, long idx) {
        int idxBits = entry.idxBits();

        long mainIdx = idx >>> idxBits;
        long litIdx = (idx & ((1L << idxBits) - 1)) - (1L << (idxBits - 1));

        int indexEntryOffset = entry.indexTableOffset() + (int) (mainIdx * 6);
        long block = readU32(data, indexEntryOffset);
        int idxOffset = readU16(data, indexEntryOffset + 4);

        litIdx += idxOffset;


        int sizeTableOffset = entry.sizeTableOffset();
        if (litIdx < 0) {
            while (litIdx < 0) {
                block--;
                int sz = readU16(data, sizeTableOffset + (int) block * 2);
                litIdx += sz + 1;
            }
        } else {
            while (true) {
                int sz = readU16(data, sizeTableOffset + (int) block * 2);
                if (litIdx <= sz) break;
                litIdx -= sz + 1;
                block++;
            }
        }

        int minLen = huffman.getMinLen();
        long[] base = huffman.getBase();
        int[] offset = huffman.getOffset();
        int[] symLen = huffman.getSymLen();
        byte[] symPat = huffman.getSymPat();

        int blockDataOffset = entry.blockDataOffset() + (int) (block << entry.blockSize());

        long code = readBEU64(data, blockDataOffset);
        int ptr = blockDataOffset + 8;

        int bitCnt = 0;
        int sym;

        while (true) {
            int l = minLen;
            while (Long.compareUnsigned(code, base[l - minLen]) < 0) {
                l++;
            }
            sym = offset[l - minLen] + (int) ((code - base[l - minLen]) >>> (64 - l));

            if (litIdx < symLen[sym] + 1) {
                break;
            }
            litIdx -= symLen[sym] + 1;

            code <<= l;
            bitCnt += l;
            if (bitCnt >= 32) {
                bitCnt -= 32;
                long tmp = readBEU32(data, ptr);
                ptr += 4;
                code |= (tmp << bitCnt);
            }
        }

        while (symLen[sym] != 0) {
            int w0 = symPat[3 * sym] & 0xff;
            int w1 = symPat[3 * sym + 1] & 0xff;
            int w2 = symPat[3 * sym + 2] & 0xff;

            int s1 = ((w1 & 0x0f) << 8) | w0;

            if (litIdx < symLen[s1] + 1) {
                sym = s1;
            } else {
                litIdx -= symLen[s1] + 1;
                sym = (w2 << 4) | (w1 >> 4);
            }
        }

        return new int[]{symPat[3 * sym] & 0xff, symPat[3 * sym + 1] & 0xff};
    }
}
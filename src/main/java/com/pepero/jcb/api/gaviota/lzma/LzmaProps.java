package com.pepero.jcb.api.gaviota.lzma;

/**
 * Parsed LZMA "alone" format header. Layout (13 bytes total, all little-endian):
 * <pre>
 *   byte 0       : (pb*5 + lp)*9 + lc
 *   bytes 1..4   : dictionary size (uint32 LE)
 *   bytes 5..12  : uncompressed size (uint64 LE)
 * </pre>
 * Gaviota's .gtb.cp4 blocks don't carry a real LZMA header — the caller
 * (GaviotaBlockDecoder) synthesizes one matching gaviota.py's fake-header
 * construction, since the actual compression parameters are fixed constants
 * for Gaviota tables (pb=2, lp=0, lc=3, dictSize=4096).
 */
final class LzmaProps {

    final int lc;
    final int lp;
    final int pb;
    final long dictSize;
    final long uncompressedSize;

    private LzmaProps(int lc, int lp, int pb, long dictSize, long uncompressedSize) {
        this.lc = lc;
        this.lp = lp;
        this.pb = pb;
        this.dictSize = dictSize;
        this.uncompressedSize = uncompressedSize;
    }

    static LzmaProps parse(byte[] header, int offset) {
        int d = header[offset] & 0xFF;
        int lc = d % 9;
        d /= 9;
        int lp = d % 5;
        d /= 5;
        int pb = d;

        long dictSize = readU32LE(header, offset + 1);
        long uncompressedSize = readU64LE(header, offset + 5);

        return new LzmaProps(lc, lp, pb, dictSize, uncompressedSize);
    }

    private static long readU32LE(byte[] b, int off) {
        return (b[off] & 0xFFL)
                | ((b[off + 1] & 0xFFL) << 8)
                | ((b[off + 2] & 0xFFL) << 16)
                | ((b[off + 3] & 0xFFL) << 24);
    }

    private static long readU64LE(byte[] b, int off) {
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v |= (b[off + i] & 0xFFL) << (8 * i);
        }
        return v;
    }
}

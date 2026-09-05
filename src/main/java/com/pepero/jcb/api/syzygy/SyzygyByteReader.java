package com.pepero.jcb.api.syzygy;

/**
 * Helper methods for reading unsigned integer values from a {@link SyzygyMappedFile}
 * (a long-offset random-access view backing the whole file — see that class for why
 * a plain MappedByteBuffer isn't enough for multi-GB tablebase files).
 * <p>
 * All these methods use ABSOLUTE positioning (an explicit long offset), so they
 * never carry any position/limit state of their own — safe to call repeatedly on a
 * shared file view without any bookkeeping.
 * <p>
 * Most Syzygy header fields are little-endian; the compressed bit-stream itself
 * (used only by SyzygyDecompressor) is big-endian — the BE-prefixed methods here
 * delegate to SyzygyMappedFile's explicit big-endian accessors, so mixing both
 * kinds of reads against the same file is always safe.
 */
class SyzygyByteReader {

    public static int readU8(SyzygyMappedFile buf, long offset) {
        return buf.getUnsignedByte(offset);
    }

    public static int readU16(SyzygyMappedFile buf, long offset) {
        return buf.getUnsignedShort(offset);
    }

    public static long readU32(SyzygyMappedFile buf, long offset) {
        return buf.getInt(offset) & 0xFFFFFFFFL;
    }

    /**
     * Read 4 bytes BIG-endian as an unsigned value, returned as long.
     * Used only for the compressed bit-stream in decompress_pairs.
     */
    public static long readBEU32(SyzygyMappedFile buf, long offset) {
        return buf.getBEUnsignedInt(offset);
    }

    /**
     * Read 8 bytes BIG-endian as a 64-bit value. No masking needed since
     * long already IS the full 64-bit width (the bit pattern is treated as
     * unsigned by callers via Long.compareUnsigned etc, not by this method).
     */
    public static long readBEU64(SyzygyMappedFile buf, long offset) {
        return buf.getBEUnsignedLong(offset);
    }

    /**
     * Copy `length` raw bytes starting at `offset` into a plain byte[].
     * Used for the few places (e.g. symPat) that need an actual standalone
     * array rather than repeated offset-based reads.
     */
    public static byte[] readBytes(SyzygyMappedFile buf, long offset, int length) {
        byte[] dst = new byte[length];
        buf.get(offset, dst);
        return dst;
    }
}
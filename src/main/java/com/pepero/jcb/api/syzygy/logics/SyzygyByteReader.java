package com.pepero.jcb.api.syzygy.logics;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Helper methods for reading unsigned integer values from a ByteBuffer
 * (typically a MappedByteBuffer backing the whole file, so large tablebase
 * files never need to be fully loaded into a Java byte[]).
 * <p>
 * All these methods use ABSOLUTE positioning (an explicit offset), so they
 * never touch the buffer's position/limit — safe to call repeatedly on a
 * shared buffer without any of the usual ByteBuffer position bookkeeping.
 * <p>
 * Most Syzygy header fields are little-endian; the compressed bit-stream
 * itself (used only by SyzygyDecompressor) is big-endian — each method here
 * sets the buffer's byte order explicitly right before reading, so mixing
 * both kinds of reads on the same buffer is always safe.
 */
public class SyzygyByteReader {

    public static int readU8(ByteBuffer buf, int offset) {
        return buf.get(offset) & 0xFF;
    }

    public static int readU16(ByteBuffer buf, int offset) {
        buf.order(ByteOrder.LITTLE_ENDIAN);
        return buf.getShort(offset) & 0xFFFF;
    }

    public static long readU32(ByteBuffer buf, int offset) {
        buf.order(ByteOrder.LITTLE_ENDIAN);
        return buf.getInt(offset) & 0xFFFFFFFFL;
    }

    /**
     * Read 4 bytes BIG-endian as an unsigned value, returned as long.
     * Used only for the compressed bit-stream in decompress_pairs.
     */
    public static long readBEU32(ByteBuffer buf, int offset) {
        buf.order(ByteOrder.BIG_ENDIAN);
        return buf.getInt(offset) & 0xFFFFFFFFL;
    }

    /**
     * Read 8 bytes BIG-endian as a 64-bit value. No masking needed since
     * long already IS the full 64-bit width (the bit pattern is treated as
     * unsigned by callers via Long.compareUnsigned etc, not by this method).
     */
    public static long readBEU64(ByteBuffer buf, int offset) {
        buf.order(ByteOrder.BIG_ENDIAN);
        return buf.getLong(offset);
    }

    /**
     * Copy `length` raw bytes starting at `offset` into a plain byte[].
     * Used for the few places (e.g. symPat) that need an actual standalone
     * array rather than repeated offset-based reads. Uses duplicate() so it
     * never disturbs the shared buffer's position or byte order.
     */
    public static byte[] readBytes(ByteBuffer buf, int offset, int length) {
        ByteBuffer dup = buf.duplicate();
        dup.position(offset);
        byte[] dst = new byte[length];
        dup.get(dst);
        return dst;
    }
}
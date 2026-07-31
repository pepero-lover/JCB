package com.pepero.jcb.api.syzygy.logics;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Helper methods for reading unsigned integer values from Syzygy header bytes.
 * All multi-byte values in the Syzygy format are stored little-endian.
 */
public class SyzygyByteReader {

    /**
     * Read 1 byte as an unsigned value (0~255)
     *
     * @param header header bytes
     * @param offset offset
     * @return unsigned 1-byte value
     */
    public static int readU8(byte[] header, int offset) {
        // byte is signed in Java (-128~127), so mask with 0xFF to treat it as unsigned
        return header[offset] & 0xFF;
    }

    /**
     * Read 2 bytes (little-endian) as an unsigned value (0~65535)
     *
     * @param header header bytes
     * @param offset offset
     * @return unsigned 2-byte value
     */
    public static int readU16(byte[] header, int offset) {
        // wrap(array, offset, length) -> length is in BYTES, not bits
        short raw = ByteBuffer.wrap(header, offset, 2)
                .order(ByteOrder.LITTLE_ENDIAN) // Syzygy format is little-endian;
                // ByteBuffer defaults to BIG_ENDIAN
                .getShort();

        // getShort() returns a signed short; mask to drop sign extension
        return raw & 0xFFFF;
    }

    /**
     * Read 4 bytes (little-endian) as an unsigned value (0~4294967295), returned as long
     * since a plain int can't hold the full unsigned 32-bit range.
     *
     * @param header header bytes
     * @param offset offset
     * @return unsigned 4-byte value
     */
    public static long readU32(byte[] header, int offset) {
        int raw = ByteBuffer.wrap(header, offset, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt();

        // getInt() returns a signed int; mask with 0xFFFFFFFFL (note the L suffix,
        // otherwise 0xFFFFFFFF would be interpreted as int -1 before widening)
        return raw & 0xFFFFFFFFL;
    }

    /**
     * Read 4 bytes BIG-endian as an unsigned value, returned as long.
     * Used only for the compressed bit-stream in decompress_pairs — unlike every
     * other header field, the raw bit-stream itself is stored big-endian.
     *
     * @param data byte array (file data)
     * @param offset offset
     * @return unsigned 4-byte value
     */
    public static long readBEU32(byte[] data, int offset) {
        int raw = ByteBuffer.wrap(data, offset, 4)
                .order(ByteOrder.BIG_ENDIAN) // explicit for clarity, this is BIG_ENDIAN's default anyway
                .getInt();
        return raw & 0xFFFFFFFFL;
    }

    /**
     * Read 8 bytes BIG-endian as a 64-bit value (kept as a signed Java long,
     * but treated as an unsigned bit pattern by callers via Long.compareUnsigned etc.
     * No masking needed here since long IS the full 64-bit width already).
     *
     * @param data byte array (file data)
     * @param offset offset
     * @return the 8-byte value, bit-identical to the unsigned 64-bit source value
     */
    public static long readBEU64(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 8)
                .order(ByteOrder.BIG_ENDIAN)
                .getLong();
    }
}
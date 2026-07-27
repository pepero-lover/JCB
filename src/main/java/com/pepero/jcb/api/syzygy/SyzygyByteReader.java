package com.pepero.jcb.api.syzygy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SyzygyByteReader {
    /**
     * Read header 1 byte
     *
     * @param header header
     * @param offset offset
     * @return read 1 byte
     */
    public static int readU8(byte[] header, int offset) {
        return header[offset] & 0xFF;
    }

    /**
     * Read header 2 byte
     *
     * @param header header
     * @param offset offset
     * @return read 2 byte
     */
    public static int readU16(byte[] header, int offset) {
        short raw = ByteBuffer.wrap(header, offset, 2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getShort();
        return raw & 0xFFFF;
    }

    /**
     * Read header 4 byte
     *
     * @param header header
     * @param offset offset
     * @return read 4 byte
     */
    public static long readU32(byte[] header, int offset) {
        int raw = ByteBuffer.wrap(header, offset, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt();
        return raw & 0xFFFFFFFFL;
    }
}

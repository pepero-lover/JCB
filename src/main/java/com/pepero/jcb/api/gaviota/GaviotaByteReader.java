package com.pepero.jcb.api.gaviota;

import java.nio.ByteBuffer;

/** Little-endian unsigned reads, matching gaviota.py's struct.Struct("<...") usage. */
final class GaviotaByteReader {

    private GaviotaByteReader() {}

    static long readU32LE(ByteBuffer buf, int offset) {
        return (buf.get(offset) & 0xFFL)
                | ((buf.get(offset + 1) & 0xFFL) << 8)
                | ((buf.get(offset + 2) & 0xFFL) << 16)
                | ((buf.get(offset + 3) & 0xFFL) << 24);
    }
}
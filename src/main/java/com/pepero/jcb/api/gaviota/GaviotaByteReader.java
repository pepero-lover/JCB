package com.pepero.jcb.api.gaviota;

import java.nio.ByteBuffer;

/**
 * Little-endian unsigned reads, matching gtb-probe.c's {@code fread32()}:
 * it reads 4 raw bytes and ORs them in as {@code p[i] << (i*8)} for i=0..3
 * (byte 0 = least significant), which is exactly a little-endian uint32.
 */
final class GaviotaByteReader {

    private GaviotaByteReader() {}

    static long readU32LE(ByteBuffer buf, int offset) {
        return (buf.get(offset) & 0xFFL)
                | ((buf.get(offset + 1) & 0xFFL) << 8)
                | ((buf.get(offset + 2) & 0xFFL) << 16)
                | ((buf.get(offset + 3) & 0xFFL) << 24);
    }
}
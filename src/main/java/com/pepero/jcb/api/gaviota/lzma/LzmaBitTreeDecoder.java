package com.pepero.jcb.api.gaviota.lzma;

/**
 * Bit-tree decoding helpers, ported from the LZMA SDK. A "bit tree" decodes
 * numBits adaptively-coded bits MSB-first into an integer 0..(2^numBits - 1),
 * each bit's probability model indexed by the tree path taken so far.
 * <p>
 * The "reverse" variant decodes the same kind of tree but LSB-first — used
 * for the low bits of match distances and the alignment bits.
 */
final class LzmaBitTreeDecoder {

    private LzmaBitTreeDecoder() {}

    /** Decode numBits MSB-first from probs[probsOffset .. probsOffset + 2^numBits - 1]. */
    static int decode(LzmaRangeDecoder rc, short[] probs, int probsOffset, int numBits) {
        int m = 1;
        for (int i = 0; i < numBits; i++) {
            m = (m << 1) + rc.decodeBit(probs, probsOffset + m);
        }
        return m - (1 << numBits);
    }

    /** Decode numBits LSB-first (reverse bit tree) from probs[probsOffset ...]. */
    static int reverseDecode(LzmaRangeDecoder rc, short[] probs, int probsOffset, int numBits) {
        int m = 1;
        int symbol = 0;
        for (int i = 0; i < numBits; i++) {
            int bit = rc.decodeBit(probs, probsOffset + m);
            m = (m << 1) + bit;
            symbol |= bit << i;
        }
        return symbol;
    }
}

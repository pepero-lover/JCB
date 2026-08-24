package com.pepero.jcb.api.gaviota.lzma;

/**
 * Range decoder for LZMA, ported from the public-domain LZMA SDK
 * (Igor Pavlov, 7-Zip) decoding algorithm. This is the core arithmetic
 * decoder that all bit-level LZMA decoding (literals, lengths, distances)
 * goes through.
 */
final class LzmaRangeDecoder {

    static final int NUM_BIT_MODEL_TOTAL_BITS = 11;
    static final int NUM_MOVE_BITS = 5;
    static final int PROB_INIT_VAL = (1 << NUM_BIT_MODEL_TOTAL_BITS) / 2; // 1024
    private static final int TOP_VALUE = 1 << 24;

    private final byte[] input;
    private int pos;

    private int range;
    private int code;

    LzmaRangeDecoder(byte[] input, int startOffset) {
        this.input = input;
        this.pos = startOffset;

        // first byte of an LZMA stream must be 0 (matches the reference decoder,
        // which discards it) — we still advance past it the same way.
        this.pos++;

        this.range = 0xFFFFFFFF;
        this.code = 0;
        for (int i = 0; i < 4; i++) {
            code = (code << 8) | nextByte();
        }
    }

    private int nextByte() {
        // out-of-range reads return 0, matching the reference decoder's behavior
        // of tolerating a short tail once the final symbol has been decoded.
        if (pos >= input.length) {
            pos++;
            return 0;
        }
        return input[pos++] & 0xFF;
    }

    private void normalize() {
        // unsigned compare: range < TOP_VALUE
        if ((range ^ 0x80000000) < (TOP_VALUE ^ 0x80000000)) {
            range <<= 8;
            code = (code << 8) | nextByte();
        }
    }

    /** Decode one bit using probability model probs[index], updating it adaptively. */
    int decodeBit(short[] probs, int index) {
        int prob = probs[index] & 0xFFFF;
        // newBound = (range >>> 11) * prob   (unsigned shift, prob fits in 11 bits so no overflow risk)
        int newBound = (range >>> NUM_BIT_MODEL_TOTAL_BITS) * prob;

        int symbol;
        // unsigned compare: code < newBound
        if ((code ^ 0x80000000) < (newBound ^ 0x80000000)) {
            range = newBound;
            probs[index] = (short) (prob + (((1 << NUM_BIT_MODEL_TOTAL_BITS) - prob) >>> NUM_MOVE_BITS));
            symbol = 0;
        } else {
            range -= newBound;
            code -= newBound;
            probs[index] = (short) (prob - (prob >>> NUM_MOVE_BITS));
            symbol = 1;
        }

        normalize();
        return symbol;
    }

    /** Decode numBits raw (non-adaptive, uniform) bits — used for high-order distance bits. */
    int decodeDirectBits(int numBits) {
        int result = 0;
        for (int i = 0; i < numBits; i++) {
            range >>>= 1;
            code -= range;
            // t = 0 if code's sign bit is 0 (i.e. code did not underflow), else -1 (0xFFFFFFFF)
            int t = 0 - (code >>> 31);
            code += range & t;
            normalize();
            result = (result << 1) + (t + 1);
        }
        return result;
    }

    static short[] initProbs(int size) {
        short[] probs = new short[size];
        java.util.Arrays.fill(probs, (short) PROB_INIT_VAL);
        return probs;
    }
}
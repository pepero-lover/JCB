package com.pepero.jcb.api.gaviota.lzma;

/**
 * Core LZMA decoding state machine, ported from the public-domain LZMA SDK
 * (Igor Pavlov, 7-Zip). Decodes exactly {@code outSize} bytes from a
 * compressed stream that starts with the standard "alone" format header
 * (see {@link LzmaProps}) at {@code headerOffset}, immediately followed by
 * the raw LZMA-compressed payload.
 * <p>
 * Since Gaviota blocks are small (at most {@code ENTRIES_PER_BLOCK} bytes)
 * and each block is a fresh, independent LZMA stream (no cross-block
 * dictionary reuse), the entire output buffer doubles as the sliding
 * window — matches are always resolved against bytes already written
 * earlier in this same buffer, so no separate ring buffer is needed.
 */
public final class LzmaDecoder {

    private static final int NUM_STATES = 12;
    private static final int NUM_POS_BITS_MAX = 4;
    private static final int NUM_LEN_TO_POS_STATES = 4;
    private static final int NUM_ALIGN_BITS = 4;
    private static final int END_POS_MODEL_INDEX = 14;
    private static final int NUM_FULL_DISTANCES = 1 << (END_POS_MODEL_INDEX >> 1); // 128
    private static final int MATCH_MIN_LEN = 2;

    private static final int NUM_POS_SLOT_BITS = 6;

    // literal decoder needs 0x300 (=768) probs per context: a plain 8-bit tree (0x100)
    // plus the "matched byte" extension doubling that range (see decodeLiteral()).
    private static final int LITERAL_CODER_SIZE = 0x300;

    public static byte[] decode(byte[] input, int headerOffset, int outSize) {
        LzmaProps props = LzmaProps.parse(input, headerOffset);
        if (props.uncompressedSize != outSize) {
            throw new IllegalStateException(
                    "LZMA header uncompressed size (" + props.uncompressedSize
                            + ") doesn't match expected block size (" + outSize + ")");
        }

        int lc = props.lc;
        int lp = props.lp;
        int pb = props.pb;
        int posMask = (1 << pb) - 1;
        int litPosMask = (1 << lp) - 1;

        LzmaRangeDecoder rc = new LzmaRangeDecoder(input, headerOffset + 13);

        short[] isMatch = LzmaRangeDecoder.initProbs(NUM_STATES << NUM_POS_BITS_MAX);
        short[] isRep = LzmaRangeDecoder.initProbs(NUM_STATES);
        short[] isRepG0 = LzmaRangeDecoder.initProbs(NUM_STATES);
        short[] isRepG1 = LzmaRangeDecoder.initProbs(NUM_STATES);
        short[] isRepG2 = LzmaRangeDecoder.initProbs(NUM_STATES);
        short[] isRep0Long = LzmaRangeDecoder.initProbs(NUM_STATES << NUM_POS_BITS_MAX);

        short[] posSlotProbs = LzmaRangeDecoder.initProbs(NUM_LEN_TO_POS_STATES * (1 << NUM_POS_SLOT_BITS));
        short[] specPos = LzmaRangeDecoder.initProbs(NUM_FULL_DISTANCES - END_POS_MODEL_INDEX);
        short[] alignProbs = LzmaRangeDecoder.initProbs(1 << NUM_ALIGN_BITS);

        LenCoder lenCoder = new LenCoder();
        LenCoder repLenCoder = new LenCoder();

        short[] literalProbs = LzmaRangeDecoder.initProbs(LITERAL_CODER_SIZE << (lc + lp));

        byte[] out = new byte[outSize];
        int nowPos = 0;
        int state = 0;
        int rep0 = 0, rep1 = 0, rep2 = 0, rep3 = 0;

        while (nowPos < outSize) {
            int posState = nowPos & posMask;
            int matchIndex = (state << NUM_POS_BITS_MAX) + posState;

            if (rc.decodeBit(isMatch, matchIndex) == 0) {
                // ---- literal ----
                int prevByte = (nowPos == 0) ? 0 : (out[nowPos - 1] & 0xFF);
                int litState = ((nowPos & litPosMask) << lc) + (prevByte >>> (8 - lc));
                int probsOffset = LITERAL_CODER_SIZE * litState;

                int symbol;
                if (state < 7) {
                    symbol = 1;
                    do {
                        symbol = (symbol << 1) | rc.decodeBit(literalProbs, probsOffset + symbol);
                    } while (symbol < 0x100);
                } else {
                    int matchByte = out[nowPos - rep0 - 1] & 0xFF;
                    symbol = 1;
                    do {
                        int matchBit = (matchByte >>> 7) & 1;
                        matchByte <<= 1;
                        int bit = rc.decodeBit(literalProbs, probsOffset + ((1 + matchBit) << 8) + symbol);
                        symbol = (symbol << 1) | bit;
                        if (matchBit != bit) break;
                    } while (symbol < 0x100);
                    while (symbol < 0x100) {
                        symbol = (symbol << 1) | rc.decodeBit(literalProbs, probsOffset + symbol);
                    }
                }

                out[nowPos++] = (byte) symbol;
                state = updateStateLiteral(state);
                continue;
            }

            // ---- match / rep ----
            int len;
            if (rc.decodeBit(isRep, state) == 1) {
                // rep match
                if (rc.decodeBit(isRepG0, state) == 0) {
                    if (rc.decodeBit(isRep0Long, matchIndex) == 0) {
                        // short rep: single byte, distance = rep0
                        state = updateStateShortRep(state);
                        out[nowPos] = out[nowPos - rep0 - 1];
                        nowPos++;
                        continue;
                    }
                    // else: normal rep match with distance = rep0 (fall through to length decode)
                } else {
                    int dist;
                    if (rc.decodeBit(isRepG1, state) == 0) {
                        dist = rep1;
                    } else {
                        if (rc.decodeBit(isRepG2, state) == 0) {
                            dist = rep2;
                        } else {
                            dist = rep3;
                            rep3 = rep2;
                        }
                        rep2 = rep1;
                    }
                    rep1 = rep0;
                    rep0 = dist;
                }
                len = repLenCoder.decode(rc, posState) + MATCH_MIN_LEN;
                state = updateStateRep(state);
            } else {
                // simple match
                rep3 = rep2;
                rep2 = rep1;
                rep1 = rep0;
                len = lenCoder.decode(rc, posState) + MATCH_MIN_LEN;
                state = updateStateMatch(state);

                int lenState = Math.min(len - MATCH_MIN_LEN, NUM_LEN_TO_POS_STATES - 1);
                int posSlot = LzmaBitTreeDecoder.decode(
                        rc, posSlotProbs, lenState << NUM_POS_SLOT_BITS, NUM_POS_SLOT_BITS);

                if (posSlot >= 4) {
                    int numDirectBits = (posSlot >> 1) - 1;
                    rep0 = (2 | (posSlot & 1)) << numDirectBits;

                    if (posSlot < END_POS_MODEL_INDEX) {
                        rep0 += LzmaBitTreeDecoder.reverseDecode(
                                rc, specPos, rep0 - posSlot - 1, numDirectBits);
                    } else {
                        rep0 += rc.decodeDirectBits(numDirectBits - NUM_ALIGN_BITS) << NUM_ALIGN_BITS;
                        rep0 += LzmaBitTreeDecoder.reverseDecode(rc, alignProbs, 0, NUM_ALIGN_BITS);
                    }
                } else {
                    rep0 = posSlot;
                }

                if (rep0 == 0xFFFFFFFF) {
                    // end-of-stream marker — shouldn't occur since we know outSize exactly,
                    // but guard against it rather than looping forever.
                    break;
                }
            }

            for (int i = 0; i < len && nowPos < outSize; i++) {
                out[nowPos] = out[nowPos - rep0 - 1];
                nowPos++;
            }
        }

        return out;
    }

    private static int updateStateLiteral(int state) {
        if (state < 4) return 0;
        if (state < 10) return state - 3;
        return state - 6;
    }

    private static int updateStateMatch(int state) {
        return state < 7 ? 7 : 10;
    }

    private static int updateStateRep(int state) {
        return state < 7 ? 8 : 11;
    }

    private static int updateStateShortRep(int state) {
        return state < 7 ? 9 : 11;
    }

    /** Length decoder: choice bits select low(3b, 0-7) / mid(3b, 8-15) / high(8b, 16-271) range. */
    private static final class LenCoder {
        private static final int NUM_POS_STATES_MAX = 1 << NUM_POS_BITS_MAX;
        private static final int LOW_BITS = 3;
        private static final int MID_BITS = 3;
        private static final int HIGH_BITS = 8;

        final short[] choice = LzmaRangeDecoder.initProbs(2);
        final short[] low = LzmaRangeDecoder.initProbs(NUM_POS_STATES_MAX << LOW_BITS);
        final short[] mid = LzmaRangeDecoder.initProbs(NUM_POS_STATES_MAX << MID_BITS);
        final short[] high = LzmaRangeDecoder.initProbs(1 << HIGH_BITS);

        int decode(LzmaRangeDecoder rc, int posState) {
            if (rc.decodeBit(choice, 0) == 0) {
                return LzmaBitTreeDecoder.decode(rc, low, posState << LOW_BITS, LOW_BITS);
            }
            if (rc.decodeBit(choice, 1) == 0) {
                return 8 + LzmaBitTreeDecoder.decode(rc, mid, posState << MID_BITS, MID_BITS);
            }
            return 16 + LzmaBitTreeDecoder.decode(rc, high, 0, HIGH_BITS);
        }
    }
}
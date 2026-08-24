package com.pepero.jcb.api.gaviota;

import com.pepero.jcb.api.gaviota.lzma.LzmaDecoder;

/**
 * Ported from gaviota.py's block-zipped handling (inside {@code _tb_probe})
 * and {@code dtm_unpack}/{@code egtb_block_unpack}. Turns the raw bytes read
 * from a table file for one block into an array of DTM distance codes
 * (still packed as prefix|plies&lt;&lt;3 — pass through {@link #unpackDist}
 * to split into ply count + result type).
 */
final class GaviotaBlockDecoder {

    private GaviotaBlockDecoder() {}

    // result/info codes, ported from gaviota.py's tb_DRAW/tb_WMATE/tb_BMATE/tb_FORBID
    static final int I_DRAW = 0;
    static final int I_WMATE = 1;
    static final int I_BMATE = 2;
    static final int I_FORBID = 3;

    private static final int PLYSHIFT = 3;
    private static final int INFOMASK = 7;

    private static final int DICTIONARY_SIZE = 4096;
    private static final int POS_STATE_BITS = 2;   // pb
    private static final int LITERAL_POS_STATE_BITS = 0; // lp
    private static final int LITERAL_CONTEXT_BITS = 3;   // lc

    /**
     * Decompress one block's raw file bytes (as read for length {@code z} from
     * {@code egtb_block_getsize_zipped}) into {@code n} unpacked DTM distance codes.
     *
     * @param zippedBuffer raw bytes read from the file for this block (length z)
     * @param side         0 = white to move, 1 = black to move (this table's stored side)
     * @param n             uncompressed entry count for this block (from egtb_block_getsize)
     * @return n distance codes, already run through dtm_unpack (still in packed
     *         prefix|plies&lt;&lt;3 form — use {@link #unpackDist} to split them)
     */
    static int[] decodeBlock(byte[] zippedBuffer, int side, int n) {
        byte[] full;
        int headerOffset;

        if (zippedBuffer.length > 0 && zippedBuffer[0] == 0) {
            // "plain LZMA is following": bytes[2:] is already a complete,
            // standard LZMA-alone-format stream (real header + payload).
            full = zippedBuffer;
            headerOffset = 2;
        } else {
            // LZMA86: synthesize the standard 13-byte alone-format header
            // (Gaviota's compression params are fixed: pb=2, lp=0, lc=3, dictSize=4096),
            // then the real payload starts at byte 15 of the original buffer.
            byte[] header = new byte[13];
            header[0] = (byte) ((POS_STATE_BITS * 5 + LITERAL_POS_STATE_BITS) * 9 + LITERAL_CONTEXT_BITS);
            for (int i = 0; i < 4; i++) {
                header[1 + i] = (byte) ((DICTIONARY_SIZE >>> (8 * i)) & 0xFF);
            }
            for (int i = 0; i < 8; i++) {
                // NOTE: must shift as long — Java's >>> on an int only honors the low 5 bits
                // of the shift amount (i.e. shifts by 32+ wrap around instead of zeroing out),
                // which silently corrupted the upper size bytes when this used `n` directly.
                header[5 + i] = (byte) (((long) n >>> (8 * i)) & 0xFF);
            }

            int payloadLen = Math.max(0, zippedBuffer.length - 15);
            full = new byte[13 + payloadLen];
            System.arraycopy(header, 0, full, 0, 13);
            System.arraycopy(zippedBuffer, 15, full, 13, payloadLen);
            headerOffset = 0;
        }

        byte[] unpacked = LzmaDecoder.decode(full, headerOffset, n);

        int[] result = new int[n];
        for (int i = 0; i < n; i++) {
            result[i] = dtmUnpack(side, unpacked[i] & 0xFF);
        }
        return result;
    }

    /** @return {plies, resultType} where resultType is one of I_DRAW/I_WMATE/I_BMATE/I_FORBID */
    static int[] unpackDist(int d) {
        return new int[]{d >>> PLYSHIFT, d & INFOMASK};
    }

    /**
     * Ported 1:1 from gaviota.py's dtm_unpack(). Expands one packed byte from
     * the block into the full "prefix | (plies &lt;&lt; 3)" distance code for
     * the given side-to-move (0=white, 1=black).
     */
    private static int dtmUnpack(int stm, int packed) {
        int p = packed;

        if (p == I_DRAW || p == I_FORBID) {
            return p;
        }

        int info = p & 3;
        int store = p >>> 2;

        int moves;
        int plies;
        int prefx;

        if (stm == 0) {
            if (info == I_WMATE) {
                moves = store + 1;
                plies = moves * 2 - 1;
                prefx = info;
            } else if (info == I_BMATE) {
                moves = store;
                plies = moves * 2;
                prefx = info;
            } else if (info == I_DRAW) {
                moves = store + 1 + 63;
                plies = moves * 2 - 1;
                prefx = I_WMATE;
            } else if (info == I_FORBID) {
                moves = store + 63;
                plies = moves * 2;
                prefx = I_BMATE;
            } else {
                plies = 0;
                prefx = 0;
            }
        } else {
            if (info == I_BMATE) {
                moves = store + 1;
                plies = moves * 2 - 1;
                prefx = info;
            } else if (info == I_WMATE) {
                moves = store;
                plies = moves * 2;
                prefx = info;
            } else if (info == I_DRAW) {
                if (store == 63) {
                    // Exception: no position in the 5-man TBs needs to store 63 for
                    // iBMATE. It is then just used to indicate iWMATE.
                    store += 1;
                    moves = store + 63;
                    plies = moves * 2;
                    prefx = I_WMATE;
                } else {
                    moves = store + 1 + 63;
                    plies = moves * 2 - 1;
                    prefx = I_BMATE;
                }
            } else if (info == I_FORBID) {
                moves = store + 63;
                plies = moves * 2;
                prefx = I_WMATE;
            } else {
                plies = 0;
                prefx = 0;
            }
        }

        return prefx | (plies << 3);
    }
}
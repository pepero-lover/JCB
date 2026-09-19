package com.pepero.jcb.api.gaviota;

import com.pepero.jcb.api.gaviota.lzma.LzmaDecoder;

/**
 * Turns the raw compressed bytes for one Gaviota block into an array of DTM
 * distance codes (still packed as {@code prefix | plies << 3} — pass through
 * {@link #unpackDist} to split into ply count + result type).
 * <p>
 * Compression scheme: Gaviota .gtb.cp4 files use CP4 = LZMA86 (per
 * gtb-dec.c's {@code f_decode}: {@code lzma_decode(bz, z, bp, &n, n)}, which
 * calls wrap.c's {@code Lzma86_Decode(out, &nn, in, &in_len)}).
 * gtb-probe.c's {@code egtb_block_decode} always strips the first byte before
 * calling {@code decode(zz-1, bz+1, nn, bp)}, so the raw block as stored in
 * the file has Gaviota's own 1-byte scheme marker prepended in front of the
 * Lzma86 stream.
 * <p>
 * Lzma86 stream layout (from LZMA SDK's {@code Lzma86Dec.h}):
 * <pre>
 *   byte  0      : useFilter flag (0 = plain LZMA, non-zero = x86 BCJ filter first)
 *   bytes 1..5   : standard 5-byte LZMA props  (pb*5+lp)*9+lc | dictSize LE32
 *   bytes 6..13  : uncompressed size (uint64 LE)
 *   bytes 14..   : LZMA payload
 * </pre>
 * Gaviota's fixed encoding params (from wrap.c's {@code lzma_encode}:
 * {@code level=5, memory=4096, filter=SZ_FILTER_NO}):
 * {@code pb=2, lp=0, lc=3, dictSize=4096, useFilter=0}.
 * So the synthesized alone-format header this class builds is always
 * 13 bytes (5-byte props + 8-byte size), and the payload starts at
 * offset 15 of the original buffer (1 Gaviota marker + 14 Lzma86 header).
 * <p>
 * {@code dtm_unpack}/{@code unpackDist} are ported 1:1 from gtb-probe.c's
 * internal {@code dtm_unpack()} and the {@code iDRAW}/{@code iWMATE}/
 * {@code iBMATE}/{@code iFORBID} enum constants.
 */
final class GaviotaBlockDecoder {

    private GaviotaBlockDecoder() {}

    // result/info codes, matching gtb-probe.c's iDRAW/iWMATE/iBMATE/iFORBID enum
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
     * Decompress one block's raw file bytes — the buffer as read from the file
     * for this block ({@code egtb_block_getsize_zipped} bytes, already past
     * the Gaviota 1-byte scheme marker that gtb-probe.c strips before calling
     * {@code decode()}) — into {@code n} unpacked DTM distance codes.
     * <p>
     * Buffer layout as read from the file (raw, no pre-stripping):
     * <pre>
     *   byte 0      : Gaviota scheme marker (gtb-probe.c strips this via bz+1
     *                 before calling decode(); we read it to branch on useFilter)
     *   byte 1      : Lzma86 useFilter flag (0 = plain LZMA, non-zero = x86 BCJ)
     *   bytes 2..6  : 5-byte LZMA props
     *   bytes 7..14 : uncompressed size (uint64 LE)
     *   bytes 15..  : LZMA payload
     * </pre>
     * Gaviota always encodes with {@code SZ_FILTER_NO} (wrap.c's
     * {@code lzma_encode}: {@code filter=SZ_FILTER_NO}), so {@code byte[1]==0}
     * (useFilter=0, plain LZMA) is the normal path. The synthesized-header
     * branch is kept for completeness.
     *
     * @param zippedBuffer raw bytes from the file for this block, including
     *                     the Gaviota 1-byte marker (full z bytes from
     *                     {@code egtb_block_getsize_zipped})
     * @param side         0 = white to move, 1 = black to move
     * @param n            uncompressed entry count (from {@code egtb_block_getsize})
     * @return n distance codes in packed {@code prefix | plies << 3} form —
     *         use {@link #unpackDist} to split them
     */
    static int[] decodeBlock(byte[] zippedBuffer, int side, int n) {
        byte[] full;
        int headerOffset;

        if (zippedBuffer.length > 0 && zippedBuffer[0] == 0) {
            // useFilter == 0: plain LZMA, no BCJ pre-filter.
            // bytes[0] is the Lzma86 useFilter byte (already confirmed 0);
            // bytes[1..13] are the standard 13-byte LZMA alone-format header
            // (5-byte props + 8-byte uncompressed size) — pass from offset 1.
            full = zippedBuffer;
            headerOffset = 2;
        } else {
            // useFilter != 0: BCJ-filtered. Lzma86_Decode handles the filter
            // internally, but our LzmaDecoder only does plain LZMA.
            // Synthesize the standard 13-byte alone-format header
            // (Gaviota fixed params from wrap.c: pb=2, lp=0, lc=3, dictSize=4096),
            // then the LZMA payload starts at byte 15 of the original buffer
            // (1 Gaviota marker [already stripped] + 14 Lzma86 header bytes).
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
     * Ported 1:1 from gtb-probe.c's {@code dtm_unpack()}. Expands one packed
     * byte from the block into the full "prefix | (plies &lt;&lt; 3)" distance
     * code for the given side-to-move (0=white, 1=black).
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
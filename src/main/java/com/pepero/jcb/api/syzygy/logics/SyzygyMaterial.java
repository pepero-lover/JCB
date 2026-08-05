package com.pepero.jcb.api.syzygy.logics;

import java.nio.ByteBuffer;

import static com.pepero.jcb.api.syzygy.logics.SyzygyByteReader.*;

/**
 * Map piece and parse
 */
public class SyzygyMaterial {
    private final int[] whiteCounts; // index 1~6 : P N B R Q K
    private final int[] blackCounts; // index 1~6 : p n b r q k
    private final int totalPieceCount;
    private final boolean hasPawns;
    private final int[] pawnCount;

    private static final char[] pieceArray = new char[]{'P', 'N', 'B', 'R', 'Q', 'K'};

    public static final long[][] binomial = new long[7][64];

    static {
        for (int i = 0; i < 7; i++) {
            for (int j = 0; j < 64; j++) {
                binomial[i][j] = (i > j) ? 0 : subfactor(i, j);
            }
        }
    }

    private SyzygyMaterial(int[] whiteCounts, int[] blackCounts, int totalPieceCount, boolean hasPawns, int[] pawnCount) {
        this.whiteCounts = whiteCounts;
        this.blackCounts = blackCounts;
        this.totalPieceCount = totalPieceCount;
        this.hasPawns = hasPawns;
        this.pawnCount = pawnCount;
    }

    /**
     * Get subtable count
     * <p>
     * if this material has no pawns, king positions can be normalized into
     * one quadrant using full board symmetry, so only 1 subtable is needed.
     * if there are pawns, only left-right mirror symmetry applies (pawns can't
     * be flipped top-to-bottom), so positions are split into 4 subtables
     * by which file (a/b/c/d) the pawn sits on.
     *
     * @return subtable count
     */
    public int getSubTableCount() {
        return hasPawns ? 4 : 1;
    }

    /**
     * Parse piece string (like KRvKB) to SyzygyMaterial
     *
     * @param pieceString piece string like KRRvKQ
     * @return Syzygy Material data
     */
    public static SyzygyMaterial parse(String pieceString) {
        pieceString = pieceString.trim();
        // KRvK
        String[] sidePiece = pieceString.split("v");
        if (sidePiece.length != 2) throw new IllegalArgumentException("There must be 2 piece info aside 'v'!");

        // KR K
        String whitePiece = sidePiece[0].trim();
        String blackPiece = sidePiece[1].trim();

        int[] whitePieceCount = new int[7];
        int[] blackPieceCount = new int[7];
        for (int i = 1; i <= pieceArray.length; i++) {
            char target = pieceArray[i - 1];
            whitePieceCount[i] = (int) whitePiece.chars().filter(c -> c == target).count();
            blackPieceCount[i] = (int) blackPiece.chars().filter(c -> c == target).count();
        }

        int totalPiece = whitePiece.length() + blackPiece.length();
        boolean hasPawns = whitePiece.contains("P") || blackPiece.contains("P");
        int[] pawnCount = new int[2];

        int whitePawnCount = whitePieceCount[1];
        int blackPawnCount = blackPieceCount[1];

        // sort pawn count
        if (blackPawnCount > 0 && (whitePawnCount == 0 || whitePawnCount > blackPawnCount)) {
            pawnCount[0] = blackPawnCount;
            pawnCount[1] = whitePawnCount;
        } else {
            pawnCount[0] = whitePawnCount;
            pawnCount[1] = blackPawnCount;
        }

        return new SyzygyMaterial(whitePieceCount, blackPieceCount, totalPiece, hasPawns, pawnCount);
    }

    // magic(4 bytes) + flags(1 byte) = 5, per-table info starts right after
    private static final int PER_TABLE_INFO_OFFSET = 5;

    /**
     * Parse per-sub-table piece order info from the header bytes.
     * Each sub-table occupies (totalPieceCount + 1 [+1 if both sides have pawns]) bytes,
     * where the first byte is the "order" value and the rest are piece type codes,
     * each byte packing a wtm nibble (low 4 bits) and a btm nibble (high 4 bits).
     *
     * @param header mapped file buffer (must be long enough)
     * @return one SyzygySubTable per sub-table, in file order
     */
    public SyzygySubTable[] parseSubTables(ByteBuffer header) {
        int subTableCount = getSubTableCount();

        // calculate bytes per table
        int bytesPerTable = totalPieceCount + 1 + (hasPawns && pawnCount[1] > 0 ? 1 : 0);

        SyzygySubTable[] result = new SyzygySubTable[subTableCount];

        int offset = PER_TABLE_INFO_OFFSET;
        for (int t = 0; t < subTableCount; t++) {
            // byte[0] of this sub-table = order info (low nibble = wtm, high nibble = btm)
            int orderByte = readU8(header, offset);

            int orderWtm = orderByte & 0x0f;
            int orderBtm = (orderByte >> 4) & 0x0f;

            boolean morePawns = hasPawns && pawnCount[1] > 0;
            int pieceStartOffset = offset + 1 + (morePawns ? 1 : 0);

            int[] wtmPieces = new int[totalPieceCount];
            int[] btmPieces = new int[totalPieceCount];

            for (int i = 0; i < totalPieceCount; i++) {
                int pieceByte = readU8(header, pieceStartOffset + i);
                wtmPieces[i] = pieceByte & 0x0f;
                btmPieces[i] = (pieceByte >> 4) & 0x0f;
            }

            int order2Wtm = 0x0f;
            int order2Btm = 0x0f;
            if (morePawns) {
                int order2Byte = readU8(header, offset + 1);
                order2Wtm = order2Byte & 0x0f;
                order2Btm = (order2Byte >> 4) & 0x0f;
            }

            result[t] = new SyzygySubTable(orderWtm, orderBtm, order2Wtm, order2Btm, wtmPieces, btmPieces);

            // go to the next sub-table
            offset += bytesPerTable;
        }

        return result;
    }

    /**
     * Parse pairs headers
     *
     * @param header      mapped file buffer (must be long enough)
     * @param startOffset start offset (should be even number)
     * @param split       should split or not
     * @param syzygyType  syzygy type
     * @return Syzygy Pairs Header
     */
    public SyzygyPairsHeadersResult parsePairsHeaders(ByteBuffer header, int startOffset, boolean split, SyzygyType syzygyType) {
        int subTableCount = getSubTableCount();
        int sides = split ? 2 : 1;

        SyzygyPairsHeader[][] result = new SyzygyPairsHeader[subTableCount][sides];

        int offset = startOffset;
        for (int t = 0; t < subTableCount; t++) {
            for (int s = 0; s < sides; s++) {
                SyzygyPairsHeader h = readOnePairsHeader(header, offset, syzygyType);
                result[t][s] = h;
                offset += h.totalByteSize();
            }
        }

        return new SyzygyPairsHeadersResult(result, offset);
    }

    /**
     * Parse one pairs header
     *
     * @param header      mapped file buffer
     * @param startOffset start offset
     * @return one pairs header
     */
    public SyzygyPairsHeader readOnePairsHeader(ByteBuffer header, int startOffset, SyzygyType syzygyType) {
        if ((readU8(header, startOffset) & 0x80) == 0x80) {
            return new SyzygyPairsHeader(
                    true, (syzygyType == SyzygyType.WDL) ? readU8(header, startOffset + 1) : 0,
                    readU8(header, startOffset), 0, 0, 0, 0, 0, 0,
                    null
            );
        }

        int maxLen = readU8(header, startOffset + 8);
        int minLen = readU8(header, startOffset + 9);

        int h = maxLen - minLen + 1;

        int flags = readU8(header, startOffset);
        int blockSize = readU8(header, startOffset + 1);
        int idxBits = readU8(header, startOffset + 2);

        int numSyms = readU16(header, startOffset + 10 + 2 * h);

        // calculate symPat offset and copy that slice out as a standalone byte[]
        // (symPat is small — numSyms*3 bytes — so copying it out of the mapped
        // buffer here is fine; everything else stays as offset-based reads
        // directly against the buffer, no full-file copy involved)
        int symPatOffset = startOffset + 12 + 2 * h;
        byte[] symPat = readBytes(header, symPatOffset, 3 * numSyms);

        // raw offset
        int[] rawOffset = new int[h];
        for (int i = 0; i < h; i++) {
            rawOffset[i] = readU16(header, startOffset + 10 + 2 * i);
        }

        return new SyzygyPairsHeader(
                false, 0,
                flags, // flags 1 byte
                blockSize, // block size 1 byte
                idxBits, // idxBits 1 byte
                readU8(header, startOffset + 3), // delta 1 byte
                readU32(header, startOffset + 4), // realNumBlocks 4 byte
                maxLen, // maxLen 1 byte
                minLen, // minLen 1 byte
                SyzygyHuffmanTable.build(symPat, numSyms, rawOffset, minLen) // huffman table
        );
    }

    /**
     * Calculate and return subtable end offset
     *
     * @return subtable end offset
     */
    public int computeSubTablesEndOffset() {
        return PER_TABLE_INFO_OFFSET + getSubTableCount() *
                (totalPieceCount + 1 + (hasPawns && pawnCount[1] > 0 ? 1 : 0));
    }

    /**
     * Calculate and return header start offset
     *
     * @return header start offset
     */
    public int computePairsHeaderStartOffset() {
        int offset = computeSubTablesEndOffset();
        return (offset % 2 == 1) ? offset + 1 : offset;
    }

    /**
     * Calculate Combination nCk
     * <p>
     * Example : subfactor(2,5) = 5_C_2 = 5_P_2 / 2! = 5 * 4 / 2 = 10
     *
     * @param k k value (n_C_'r' <---)
     * @param n n value ('n'<---_C_r)
     * @return result of Combination nCk
     */
    public static long subfactor(long k, long n) {
        if (k == 0) return 1;
        if (k > n - k) {
            k = n - k;
            if (k == 0) return 1;
        }
        long f = n;
        long l = 1;
        for (long i = 1; i < k; i++) {
            f *= (n - i);
            l *= (i + 1);
        }
        return f / l;
    }

    public int[] getWhiteCounts() {
        return whiteCounts;
    }

    public int[] getBlackCounts() {
        return blackCounts;
    }

    public boolean isHasPawns() {
        return hasPawns;
    }

    public int[] getPawnCount() {
        return pawnCount;
    }

    public int getTotalPieceCount() {
        return totalPieceCount;
    }

    /**
     * Get kk evc value
     *
     * @return kk evc value
     */
    public boolean isKkEnc() {
        if (hasPawns) {
            return false;
        }

        int count = 0;
        for (int i = 1; i <= 6; i++) {
            if (whiteCounts[i] == 1) {
                count++;
                if (count > 2) return false;
            }
            if (blackCounts[i] == 1) {
                count++;
                if (count > 2) return false;
            }
        }

        return count == 2;
    }
}
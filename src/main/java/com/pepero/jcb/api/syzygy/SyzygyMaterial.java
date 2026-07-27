package com.pepero.jcb.api.syzygy;

import static com.pepero.jcb.api.syzygy.SyzygyByteReader.*;

/**
 * Map piece and parse
 */
public class SyzygyMaterial {
    private final int totalPieceCount;
    private final boolean hasPawns;
    private final int[] pawnCount;

    private SyzygyMaterial(int totalPieceCount, boolean hasPawns, int[] pawnCount) {
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
        if(sidePiece.length != 2) throw new IllegalArgumentException("There must be 2 piece info aside 'v'!");

        // KR K
        String whitePiece = sidePiece[0].trim();
        String blackPiece = sidePiece[1].trim();

        int totalPiece = whitePiece.length() + blackPiece.length();
        boolean hasPawns = whitePiece.contains("P") || blackPiece.contains("P");
        int[] pawnCount = new int[2];

        long whitePawnCount = whitePiece.chars().filter(c -> c == 'P').count();
        long blackPawnCount = blackPiece.chars().filter(c -> c == 'P').count();

        // sort pawn count
        if(blackPawnCount > 0 && (whitePawnCount == 0 || whitePawnCount > blackPawnCount)) {
            pawnCount[0] = (int) blackPawnCount;
            pawnCount[1] = (int) whitePawnCount;
        } else {
            pawnCount[0] = (int) whitePawnCount;
            pawnCount[1] = (int) blackPawnCount;
        }

        return new SyzygyMaterial(totalPiece, hasPawns, pawnCount);
    }

    // magic(4 bytes) + flags(1 byte) = 5, per-table info starts right after
    private static final int PER_TABLE_INFO_OFFSET = 5;

    /**
     * Parse per-sub-table piece order info from the header bytes.
     * Each sub-table occupies (totalPieceCount + 1 [+1 if both sides have pawns]) bytes,
     * where the first byte is the "order" value and the rest are piece type codes,
     * each byte packing a wtm nibble (low 4 bits) and a btm nibble (high 4 bits).
     *
     * @param header header byte array read from the file (must be long enough)
     * @return one SyzygySubTable per sub-table, in file order
     */
    public SyzygySubTable[] parseSubTables(byte[] header) {
        int subTableCount = getSubTableCount();

        // calculate bytes per table
        int bytesPerTable = totalPieceCount + 1 + (hasPawns && pawnCount[1] > 0 ? 1 : 0);

        SyzygySubTable[] result = new SyzygySubTable[subTableCount];

        int offset = PER_TABLE_INFO_OFFSET;
        for (int t = 0; t < subTableCount; t++) {
            // byte[0] of this sub-table = order info (low nibble = wtm, high nibble = btm)
            int orderByte = header[offset] & 0xff;

            int orderWtm = orderByte & 0x0f;

            // orderBtm not stored separately here since SyzygySubTable only keeps one 'order'
            // field for now — extend later if btm order is needed.

            int[] wtmPieces = new int[totalPieceCount];
            int[] btmPieces = new int[totalPieceCount];

            for (int i = 0; i < totalPieceCount; i++) {
                int pieceByte = header[offset + 1 + i] & 0xff;
                wtmPieces[i] = pieceByte & 0x0f;
                btmPieces[i] = (pieceByte >> 4) & 0x0f;
            }

            result[t] = new SyzygySubTable(orderWtm, wtmPieces, btmPieces);

            // go to the next sub-table
            offset += bytesPerTable;

            // align offset to even number
            if(offset % 2 == 1) offset += 1;

            System.out.println(offset);


        }

        return result;
    }

    /**
     * Parse pairs headers
     *
     * @param header header array (should long enough
     * @param startOffset start offset (should even number)
     * @param split should split or not
     * @param syzygyType syzygy type
     *
     * @return Syzygy Pairs Header
     */
    public SyzygyPairsHeader[][] parsePairsHeaders(byte[] header, int startOffset, boolean split, SyzygyType syzygyType) {
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

        return result;
    }

    /**
     * Parse one pairs header
     *
     * @param header header
     * @param startOffset start offset
     * @return one pairs header
     */
    public SyzygyPairsHeader readOnePairsHeader(byte[] header, int startOffset, SyzygyType syzygyType){
        if((header[startOffset] & 0x80) == 0x80) {
            return new SyzygyPairsHeader(
                    true, (syzygyType == SyzygyType.WDL) ? readU8(header, startOffset + 1) : 0,
                    readU8(header, startOffset),0,0,0,0,0,0,0
            );
        }

        int maxLen = readU8(header, startOffset + 8);
        int minLen = readU8(header, startOffset + 9);

        return new SyzygyPairsHeader(
                false, 0,
                readU8(header, startOffset), // flags 1 byte
                readU8(header, startOffset + 1), // block size 1 byte
                readU8(header, startOffset + 2), // idxBits 1 byte
                readU8(header, startOffset + 3), // delta 1 byte
                readU32(header, startOffset + 4), // realNumBlocks 4 byte
                maxLen, // maxLen 1 byte
                minLen, // minLen 1 byte
                // later, huffman will be added
                readU16(header, startOffset + 10 + 2 * (maxLen - minLen + 1)) // numSyms 2 byte
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
}

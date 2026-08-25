package com.pepero.jcb.core.bitboard;

public class BitBoardUtils {
    // The bitboard type is just 'long'

    // -----------------
    // BIT MANIPULATIONS
    // -----------------


    /**
     * Get amount of bitboard's bits
     * @return amount of this bitboard's bits
     */
    public static int countBits(long bitboard) {
        return Long.bitCount(bitboard);
    }

    /**
     * Get the Least Significant 1st Bit (LSB)
     *
     * @return the Least Significant 1st Bit on this Board
     */
    public static int getLS1BIndex(long bitboard) {
        if (bitboard == 0) return -1;
        return Long.numberOfTrailingZeros(bitboard);
    }

    // set/get/pop macros

    /**
     * Get a bit on Board
     * <p>
     * THIS METHOD DON'T CHANGE THE BITBOARD ON LONG.
     * IF YOU ARE USING IT, MAKE SURE YOU CODE LIKE THIS:
     * <p>
     * bitboard = setBit(bitboard, square);
     * <p>
     * NOT LIKE THIS
     * <p>
     * setBit(bitboard, square);
     *
     * @param square square on bitboard
     * @return edited this bitboard
     */
    public static long setBit(long bitboard, int square){
        bitboard |= (1L << square);
        return bitboard;
    }

    /**
     * Get a bit on Board
     * @param square square in bitboard
     * @return square on bitboard
     */
    public static boolean getBit(long bitboard, int square) {
        return (bitboard & (1L << square)) != 0;
    }

    /**
     * Pop a bit on Board
     * if a bit on Board is 0 it does noting,
     * else, a bit on Board equals 0
     * <p>
     * THIS METHOD DON'T CHANGE THE BITBOARD ON LONG.
     * IF YOU ARE USING IT, MAKE SURE YOU CODE LIKE THIS:
     * <p>
     * bitboard = popBit(bitboard, square);
     * <p>
     * NOT LIKE THIS
     * <p>
     * popBit(bitboard, square);
     * <p>
     * @param square square on bitboard
     * @return edited this bitboard
     */
    public static long popBit(long bitboard, int square){
        bitboard &= ~(1L << square);
        return bitboard;
    }

    // ------
    //   IO
    // ------

    /**
     * Print this bitboard
     */
    public static void printBitBoard(long bitboard) {
        StringBuilder sb = new StringBuilder(256);

        // print offset
        sb.append('\n');

        for (int rank = 0; rank < 8; rank++) {
            // append ranks
            sb.append("  ").append(8 - rank).append("  ");

            for (int file = 0; file < 8; file++) {
                // init square
                int square = (7 - rank) * 8 + file;

                // this checks whether this square is 1 or 0
                long bit = (bitboard >>> square) & 1L;
                sb.append(' ').append(bit);
            }

            // print new line every rank
            sb.append('\n');
        }

        // append files
        sb.append("\n      a b c d e f g h \n\n");

        // append bitboard as number
        sb.append("  Bitboard as unsigned: ").append(Long.toUnsignedString(bitboard)).append("UL\n");
        sb.append("  Bitboard as signed: ").append(bitboard).append("L\n");

        System.out.print(sb);
    }
}

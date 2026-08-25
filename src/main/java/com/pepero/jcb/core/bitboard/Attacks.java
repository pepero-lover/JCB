package com.pepero.jcb.core.bitboard;

import static com.pepero.jcb.core.constant.SideToMove.*;

public class Attacks {
    //  -------
    //  ATTACKS
    //  -------

    /*
          not A file

      8   0 1 1 1 1 1 1 1
      7   0 1 1 1 1 1 1 1
      6   0 1 1 1 1 1 1 1
      5   0 1 1 1 1 1 1 1
      4   0 1 1 1 1 1 1 1
      3   0 1 1 1 1 1 1 1
      2   0 1 1 1 1 1 1 1
      1   0 1 1 1 1 1 1 1

          a b c d e f g h

          not H file

      8   1 1 1 1 1 1 1 0
      7   1 1 1 1 1 1 1 0
      6   1 1 1 1 1 1 1 0
      5   1 1 1 1 1 1 1 0
      4   1 1 1 1 1 1 1 0
      3   1 1 1 1 1 1 1 0
      2   1 1 1 1 1 1 1 0
      1   1 1 1 1 1 1 1 0

          a b c d e f g h

          not HG file

      8   1 1 1 1 1 1 0 0
      7   1 1 1 1 1 1 0 0
      6   1 1 1 1 1 1 0 0
      5   1 1 1 1 1 1 0 0
      4   1 1 1 1 1 1 0 0
      3   1 1 1 1 1 1 0 0
      2   1 1 1 1 1 1 0 0
      1   1 1 1 1 1 1 0 0

          a b c d e f g h

          not AB file

      8   0 0 1 1 1 1 1 1
      7   0 0 1 1 1 1 1 1
      6   0 0 1 1 1 1 1 1
      5   0 0 1 1 1 1 1 1
      4   0 0 1 1 1 1 1 1
      3   0 0 1 1 1 1 1 1
      2   0 0 1 1 1 1 1 1
      1   0 0 1 1 1 1 1 1

          a b c d e f g h

     */

    public static final boolean bishop = true;
    public static final boolean rook = false;

    // not A file constant

    // unsigned value is 18374403900871474942
    public static final long not_a_file = -72340172838076674L;

    // not H file constant

    // unsigned value is the same as this value
    public static final long not_h_file = 9187201950435737471L;

    // not HG file constant

    // unsigned value is the same as this value
    public static final long not_hg_file = 4557430888798830399L;

    // not AB file constant

    // unsigned value is 18229723555195321596
    public static final long not_ab_file = -217020518514230020L;

    // bishop relevancy occupancy bit count for every square on board
    public static final int[] bishop_relevant_bits = {
            6, 5, 5, 5, 5, 5, 5, 6,
            5, 5, 5, 5, 5, 5, 5, 5,
            5, 5, 7, 7, 7, 7, 5, 5,
            5, 5, 7, 9, 9, 7, 5, 5,
            5, 5, 7, 9, 9, 7, 5, 5,
            5, 5, 7, 7, 7, 7, 5, 5,
            5, 5, 5, 5, 5, 5, 5, 5,
            6, 5, 5, 5, 5, 5, 5, 6,
    };

    // rook relevancy occupancy bit count for every square on board
    public static final int[] rook_relevant_bits = {
            12, 11, 11, 11, 11, 11, 11, 12,
            11, 10, 10, 10, 10, 10, 10, 11,
            11, 10, 10, 10, 10, 10, 10, 11,
            11, 10, 10, 10, 10, 10, 10, 11,
            11, 10, 10, 10, 10, 10, 10, 11,
            11, 10, 10, 10, 10, 10, 10, 11,
            11, 10, 10, 10, 10, 10, 10, 11,
            12, 11, 11, 11, 11, 11, 11, 12,
    };

    //  pawn attacks table [side][square]
    //  side depends on white's turn or black's turn
    public static final long[][] pawn_attacks = new long[2][64];

    //  knight attacks table [square]
    //  this doesn't need side because it doesn't matter white turn and black turn
    public static final long[] knight_attacks = new long[64];

    //  knight attacks table [square]
    //  this doesn't need side because it doesn't matter white turn and black turn
    public static final long[] king_attacks = new long[64];

    //  bishop attack masks
    //  this doesn't need side because it doesn't matter white turn and black turn
    public static final long[] bishop_masks = new long[64];

    //  rook attack masks
    //  this doesn't need side because it doesn't matter white turn and black turn
    public static final long[] rook_masks = new long[64];

    // bishop attacks table [square][occupancies]
    public static final long[][] bishop_attacks = new long[64][512];

    // rook attacks table [square][occupancies]
    public static final long[][] rook_attacks = new long[64][4096];

    /**
     * Generate masked pawn attacks
     *
     * @param side   whether who plays move
     * @param square the square where the pawn is
     * @return masked pawn moves
     */
    public static long maskPawnAttacks(int side, int square){
        // result attacks bitboard
        long attacks = 0L;

        // piece bitboard
        long bitboard = 0L;

        // set piece on board
        bitboard = BitBoardUtils.setBit(bitboard, square);

        // white pawns
        if(side == white) {
            // generate pawn attacks

            // Making sure this attacking bit is not overboard,
            // Why is it 7?
            // because 8 is rank change, and -1 is file change
            attacks |= ((bitboard << 7) & not_h_file);


            // Making sure this attacking bit is not overboard,
            // Why is it 9?
            // because 8 is rank change, and 1 is file change
            attacks |= ((bitboard << 9) & not_a_file);
        }

        // black pawns
        else {
            // generate pawn attacks

            // Making sure this attacking bit is not overboard,
            // Why is it 7?
            // because 8 is rank change, and -1 is file change
            attacks |= ((bitboard >>> 7) & not_a_file);


            // Making sure this attacking bit is not overboard,
            // Why is it 9?
            // because 8 is rank change, and 1 is file change
            attacks |= ((bitboard >>> 9) & not_h_file);
        }

        // return attack map
        return attacks;
    }


    /**
     * Generate masked knight attacks
     *
     * @param square the square where the knight is
     * @return masked knight moves
     */
    // this doesn't need the side because it doesn't matter white turn and black turn
    public static long maskKnightAttacks(int square){
        // result attacks bitboard
        long attacks = 0L;

        // piece bitboard
        long bitboard = 0L;

        // set piece on board
        bitboard = BitBoardUtils.setBit(bitboard, square);

        // generate knight attacks
        // (knight directions 17, 15, 10, 6)

        attacks |= (bitboard >>> 17) & not_h_file;
        attacks |= (bitboard >>> 15) & not_a_file;
        attacks |= (bitboard >>> 10) & not_hg_file;
        attacks |= (bitboard >>> 6) & not_ab_file;

        attacks |= (bitboard << 17) & not_a_file;
        attacks |= (bitboard << 15) & not_h_file;
        attacks |= (bitboard << 10) & not_ab_file;
        attacks |= (bitboard << 6) & not_hg_file;

        // return attack map
        return attacks;
    }


    /**
     * Generate masked king attacks
     *
     * @param square the square where the king is
     * @return masked king moves
     */
    // this doesn't need the side because it doesn't matter white turn and black turn
    public static long maskKingAttacks(int square){
        // result attacks bitboard
        long attacks = 0L;

        // piece bitboard
        long bitboard = 0L;

        // set piece on board
        bitboard = BitBoardUtils.setBit(bitboard, square);

        // generate king attacks
        attacks |= (bitboard >>> 8);
        attacks |= (bitboard >>> 1) & not_h_file;
        attacks |= (bitboard >>> 7) & not_a_file;
        attacks |= (bitboard >>> 9) & not_h_file;

        attacks |= (bitboard << 8);
        attacks |= (bitboard << 1) & not_a_file;
        attacks |= (bitboard << 7) & not_h_file;
        attacks |= (bitboard << 9) & not_a_file;

        // return attack map
        return attacks;
    }

    /**
     * Generate masked bishop attacks
     *
     * @param square the square where the bishop is
     * @return masked bishop moves
     */
    // this doesn't need the side because it doesn't matter white turn and black turn
    public static long maskBishopAttacks(int square){
        // result attacks bitboard
        long attacks = 0L;

        // init ranks & files
        int r, f;

        // init target rank & files
        int tr = square / 8;
        int tf = square % 8;

        // mask relevant bishop occupancy bits
        for (r = tr + 1, f = tf + 1; r <= 6 && f <= 6; r++, f++) attacks |= (1L << (r * 8 + f));
        for (r = tr - 1, f = tf + 1; r >= 1 && f <= 6; r--, f++) attacks |= (1L << (r * 8 + f));
        for (r = tr + 1, f = tf - 1; r <= 6 && f >= 1; r++, f--) attacks |= (1L << (r * 8 + f));
        for (r = tr - 1, f = tf - 1; r >= 1 && f >= 1; r--, f--) attacks |= (1L << (r * 8 + f));

        // return attack map
        return attacks;
    }

    /**
     * Generate masked rook attacks
     *
     * @param square the square where the rook is
     * @return masked rook moves
     */
    // this doesn't need the side because it doesn't matter white turn and black turn
    public static long maskRookAttacks(int square){
        // result attacks bitboard
        long attacks = 0L;

        // init ranks & files
        int r, f;

        // init target rank & files
        int tr = square / 8;
        int tf = square % 8;

        // mask relevant rook occupancy bits
        for(r = tr + 1; r <= 6; r++) attacks |= (1L << (r * 8 + tf));
        for(r = tr - 1; r >= 1; r--) attacks |= (1L << (r * 8 + tf));
        for(f = tf + 1; f <= 6; f++) attacks |= (1L << (tr * 8 + f));
        for(f = tf - 1; f >= 1; f--) attacks |= (1L << (tr * 8 + f));

        // return attack map
        return attacks;
    }

    /**
     * Generate bishop attacks on the fly
     *
     * @param square the square where the bishop is
     * @param block the obstacle which interact with the bishop's attack
     * @return bishop attacks on the fly
     */
    // this doesn't need the side because it doesn't matter white turn and black turn
    public static long bishopAttacksOnTheFly(int square, long block){
        // result attacks bitboard
        long attacks = 0L;

        // init ranks & files
        int r, f;

        // init target rank & files
        int tr = square / 8;
        int tf = square % 8;

        // generate bishop attacks
        for (r = tr + 1, f = tf + 1; r <= 7 && f <= 7; r++, f++) {
            attacks |= (1L << (r * 8 + f));
            if(((1L << (r * 8 + f)) & block) != 0) break;
        }
        for (r = tr - 1, f = tf + 1; r >= 0 && f <= 7; r--, f++) {
            attacks |= (1L << (r * 8 + f));
            if(((1L << (r * 8 + f)) & block) != 0) break;
        }
        for (r = tr + 1, f = tf - 1; r <= 7 && f >= 0; r++, f--) {
            attacks |= (1L << (r * 8 + f));
            if(((1L << (r * 8 + f)) & block) != 0) break;
        }
        for (r = tr - 1, f = tf - 1; r >= 0 && f >= 0; r--, f--) {
            attacks |= (1L << (r * 8 + f));
            if(((1L << (r * 8 + f)) & block) != 0) break;
        }

        // return attack map
        return attacks;
    }

    /**
     * Generate rook attacks on the fly
     *
     * @param square the square where the rook is
     * @param block the obstacle which interact with the rook's attack
     * @return rook attacks on the fly
     */
    // this doesn't need the side because it doesn't matter white turn and black turn
    public static long rookAttacksOnTheFly(int square, long block){
        // result attacks bitboard
        long attacks = 0L;

        // init ranks & files
        int r, f;

        // init target rank & files
        int tr = square / 8;
        int tf = square % 8;

        // generate rook attacks
        for(r = tr + 1; r <= 7; r++) {
            attacks |= (1L << (r * 8 + tf));
            if(((1L << (r * 8 + tf)) & block) != 0) break;
        }
        for(r = tr - 1; r >= 0; r--) {
            attacks |= (1L << (r * 8 + tf));
            if(((1L << (r * 8 + tf)) & block) != 0) break;
        }
        for(f = tf + 1; f <= 7; f++) {
            attacks |= (1L << (tr * 8 + f));
            if(((1L << (tr * 8 + f)) & block) != 0) break;
        }
        for(f = tf - 1; f >= 0; f--) {
            attacks |= (1L << (tr * 8 + f));
            if(((1L << (tr * 8 + f)) & block) != 0) break;
        }

        // return attack map
        return attacks;
    }


    /**
     * Initialize leaper pieces attacks
     */
    public static void initLeapersAttacks(){
        // loop over 64 board squares
        for(int square = 0; square < 64; square++){
            // init pawn attacks
            pawn_attacks[white][square] = maskPawnAttacks(white, square);
            pawn_attacks[black][square] = maskPawnAttacks(black, square);

            // init knight attacks
            knight_attacks[square] = maskKnightAttacks(square);

            // init king attacks
            king_attacks[square] = maskKingAttacks(square);
        }
    }

    /**
     * Generates a specific occupancy bitboard configuration for a given index.
     * * This function is a key component of the Magic Bitboards algorithm. It maps a
     * subset of bits (the 'index') onto the corresponding squares defined by the
     * 'attack_mask'. This allows the engine to iterate through all possible
     * permutations of pieces blocking a slider's path.
     *
     * @param index        The integer representing the specific permutation to generate
     * (ranges from 0 to 2^bits_int_mask - 1).
     * @param bitsIntMask The total number of set bits (relevant squares) in the attack_mask.
     * @param attackMask   The bitboard mask containing the potential squares that can be
     * occupied by other pieces.
     * @return             A long bitboard representing the occupancy state for the given index.
     */
    public static long setOccupancy(int index, int bitsIntMask, long attackMask){
        // occupancy map
        long occupancy = 0L;

        // loop over the range of bits within attack mask
        for(int count = 0; count < bitsIntMask; count++){
            // get LS1B index of attack mask
            int square = BitBoardUtils.getLS1BIndex(attackMask);

            // pop LS1B in attack map
            attackMask = BitBoardUtils.popBit(attackMask, square);

            // make sure occupancy is on board
            if((index & (1 << count)) != 0){
                // populate occupancy map
                occupancy |= (1L << square);
            }
        }

        // return occupancy map
        return occupancy;
    }

    /**
     * Get bishop attacks
     *
     * @param square the square where the bishop is
     * @param occupancy obstacles which is on bitboard
     * @return bishop attacks
     */
    public static long getBishopAttacks(int square, long occupancy){
        // get bishop attacks assuming current board occupancy
        occupancy &= bishop_masks[square];
        occupancy *= MagicNumbers.bishop_magic_numbers[square];
        occupancy >>>= 64 - bishop_relevant_bits[square];

        // return bishop attacks
        return bishop_attacks[square][(int) occupancy];
    }

    /**
     * Get rook attacks
     *
     * @param square the square where the rook is
     * @param occupancy obstacles which is on bitboard
     * @return rook attacks
     */
    public static long getRookAttacks(int square, long occupancy){
        // get rook attacks assuming current board occupancy
        occupancy &= rook_masks[square];
        occupancy *= MagicNumbers.rook_magic_numbers[square];
        occupancy >>>= 64 - rook_relevant_bits[square];

        // return rook attacks
        return rook_attacks[square][(int) occupancy];
    }

    /**
     * Get Queen attacks
     *
     * @param square the square where the queen is
     * @param occupancy obstacles which is on bitboard
     * @return queen attacks
     */
    public static long getQueenAttacks(int square, long occupancy){
        // init result attacks bitboard
        long queen_attacks = 0L;

        // init bishop occupancies
        long bishop_occupancy = occupancy;

        // init rook occupancies
        long rook_occupancy = occupancy;

        // get rook attacks assuming current board occupancy
        bishop_occupancy &= bishop_masks[square];
        bishop_occupancy *= MagicNumbers.bishop_magic_numbers[square];
        bishop_occupancy >>>= 64 - bishop_relevant_bits[square];

        // get bishop attacks
        queen_attacks = bishop_attacks[square][(int) bishop_occupancy];

        // get rook attacks assuming current board occupancy
        rook_occupancy &= rook_masks[square];
        rook_occupancy *= MagicNumbers.rook_magic_numbers[square];
        rook_occupancy >>>= 64 - rook_relevant_bits[square];

        // get rook attacks
        queen_attacks |= rook_attacks[square][(int) rook_occupancy];

        // return queen attacks
        return queen_attacks;
    }
}

package com.pepero.jcb.bitboard;

import com.pepero.jcb.util.Random;

import java.util.Arrays;

public class Magics {
    /**
     * Find appropriate magic number
     *
     * @param square the square where the piece is
     * @param relevant_bits the bits which is generated from the piece's way (except the outside squares)
     * @param bishop it distinguishes whether is bishop or rook
     *              (please use 'bishop' and 'rook' variable on Attacks class)
     * <ul>
     * <li>{@code true}: it finds bishop magic number</li>
     * <li>{@code false}: in finds rook magic number</li>
     * </ul>
     * @return discovered magic number
     */
    public static long findMagicNumber(int square, int relevant_bits, boolean bishop){
        // init occupancies
        long[] occupancies = new long[4096];

        // init attack tables
        long[] attacks = new long[4096];

        // init used attacks
        long[] used_attacks = new long[4096];

        // init attack mask for a current piece
        long attack_mask = bishop ? Attacks.maskBishopAttacks(square) : Attacks.maskRookAttacks(square);

        // init occupancy indices
        int occupancy_indices = 1 << relevant_bits;

        // loop over occupancy indices
        for(int index = 0; index < occupancy_indices; index++){
            // init occupancies
            occupancies[index] = Attacks.setOccupancy(index, relevant_bits, attack_mask);

            // init attacks
            attacks[index] = bishop ? Attacks.bishopAttacksOnTheFly(square, occupancies[index]):
                                        Attacks.rookAttacksOnTheFly(square, occupancies[index]);
        }

        // test magic numbers loop
        for(int random_count = 0; random_count < 10000000; random_count++){
            // generate magic number candidate
            long magic_number = Random.generateMagicNumber();

            // skip inappropriate magic numbers
            if(BitBoardUtils.countBits((attack_mask * magic_number) & 0xFF00000000000000L) < 6) continue;

            // init used attacks
            Arrays.fill(used_attacks, 0L);

            // init index & fail flag
            int index;
            boolean fail;

            // test magic index loop
            for(index = 0, fail = false; !fail && index < occupancy_indices; index++){
                // init magic index
                int magic_index = (int) ((occupancies[index] * magic_number) >>> (64 - relevant_bits));

                // if magic index works
                if(used_attacks[magic_index] == 0L){
                    // init used attacks
                    used_attacks[magic_index] = attacks[index];
                } else if(used_attacks[magic_index] != attacks[index]) {
                    // magic index doesn't work
                    fail = true;
                }
            }

            // if magic number works
            if(!fail){
                // return it
                return magic_number;
            }
        }

        // if magic number doesn't work
        System.out.println("   Magic number fails!");
        return 0L;
    }

    /**
     * Init magic numbers
     */
    public static void initMagicNumbers(){
        // update pseudo random number state
        Random.setRandomStateForMagicNumber();

        // loop over 64 board squares
        for(int square = 0;square<64;square++){
            // init rook magic numbers
            MagicNumbers.rook_magic_numbers[square] = findMagicNumber(square,
                    Attacks.rook_relevant_bits[square], Attacks.rook);
        }

        System.out.println();
        System.out.println();

        // loop over 64 board squares
        for(int square = 0;square<64;square++){
            // init rook magic numbers
            MagicNumbers.bishop_magic_numbers[square] = findMagicNumber(square,
                    Attacks.bishop_relevant_bits[square], Attacks.bishop);
        }
    }

    /**
     * Init slider piece's attack tables
     *
     * @param bishop it distinguishes whether is bishop or rook for calculation
     *              (please use 'bishop' and 'rook' variable on Attacks class)
     */
    public static void initSlidersAttacks(boolean bishop){
        // loop over 64 board squares
        for(int square = 0; square < 64; square++){
            // init bishop & rook masks
            Attacks.bishop_masks[square] = Attacks.maskBishopAttacks(square);
            Attacks.rook_masks[square] = Attacks.maskRookAttacks(square);

            // init current mask
            long attack_mask = bishop ? Attacks.bishop_masks[square] : Attacks.rook_masks[square];

            // init relevant occupancy bit count
            int relevant_bits_count = BitBoardUtils.countBits(attack_mask);

            // init occupancy indices
            int occupancy_indices = (1 << relevant_bits_count);

            // loop over occupancy indices
            for(int index = 0; index < occupancy_indices; index++){
                 // bishop
                if(bishop) {
                    // init current occupancy variation
                    long occupancy = Attacks.setOccupancy(index, relevant_bits_count, attack_mask);

                    // init magic index
                    int magic_index = (int) ((occupancy * MagicNumbers.bishop_magic_numbers[square])
                                                >>> (64 - Attacks.bishop_relevant_bits[square]));

                    // init bishop attacks
                    Attacks.bishop_attacks[square][magic_index] = Attacks.bishopAttacksOnTheFly(square, occupancy);
                }

                // rook
                else {
                    // init current occupancy variation
                    long occupancy = Attacks.setOccupancy(index, relevant_bits_count, attack_mask);

                    // init magic index
                    int magic_index = (int) ((occupancy * MagicNumbers.rook_magic_numbers[square])
                                                >>> (64 - Attacks.rook_relevant_bits[square]));

                    // init rook attacks
                    Attacks.rook_attacks[square][magic_index] = Attacks.rookAttacksOnTheFly(square, occupancy);
                }
            }
        }
    }
}

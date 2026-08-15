package com.pepero.jcb.hash;

import com.pepero.jcb.bitboard.BitBoardUtils;
import com.pepero.jcb.constant.BoardSquares;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.GameVariants;
import com.pepero.jcb.util.Random;

import static com.pepero.jcb.constant.BoardSquares.*;
import static com.pepero.jcb.constant.EncodedPieces.*;
import static com.pepero.jcb.constant.SideToMove.*;

public class Zobrist {
    // random piece keys [piece][square]
    public static final long[][] piece_keys = new long[12][64];

    // random enpassant keys [square]
    public static final long[] enpassant_keys = new long[64];

    // random castling keys
    public static final long[] castling_keys = new long[16];

    // random side key
    public static long side_key;

    // for crazy house

    // promoted pawn keys
    public static final long[] promoted_keys = new long[64];

    // pocket key [piece][count]
    public static final long[][] pocket_keys = new long[12][64];

    // for 3 check
    public static long[][] check_count_keys = new long[2][8];

    /**
     * Init random hash keys
     */
    public static void initHashKeys(){
        // update pseudo random number state
        Random.setRandomStateForHashing();

        // loop over piece codes
        for (int piece = P; piece <= k; piece++){
            // loop over board squares
            for (int square = 0; square < 64; square++){
                // init random piece keys
                piece_keys[piece][square] = Random.getRandom64BitsNumber();
            }
        }

        // loop over board squares
        for (int square = 0; square < 64; square++){
            // init random enpassant keys
            enpassant_keys[square] = Random.getRandom64BitsNumber();
        }

        // loop over castling keys
        for (int index = 0; index < 16; index++){
            // init castling keys
            castling_keys[index] = Random.getRandom64BitsNumber();
        }

        // init random side key
        side_key = Random.getRandom64BitsNumber();

        // init promotion keys
        for (int square = 0; square < 64; square++) {
            promoted_keys[square] = Random.getRandom64BitsNumber();
        }

        // init pocket piece key
        for (int piece = P; piece <= k; piece++) {
            for (int square = 0; square < 64; square++) {
                pocket_keys[piece][square] = Random.getRandom64BitsNumber();
            }
        }

        // init check keys
        for (int count = 0; count < 8; count++) {
            check_count_keys[white][count] = Random.getRandom64BitsNumber();
            check_count_keys[black][count] = Random.getRandom64BitsNumber();
        }
    }

    /**
     * Generate "almost" unique position ID aka hash key from scratch
     *
     * @param chessboard chessboard
     *
     * @return Generated position ID aka hash key
     */
    public static long generateHashKey(Chessboard chessboard){
        // final hash key
        long final_key = 0L;

        // temp piece bitboard copy
        long bitboard;

        // loop over piece bitboards
        for (int piece = P; piece <= k; piece++){
            // init piece bitboard copy
            bitboard = chessboard.bitboards[piece];

            // loop over the pieces within a bitboard
            while (bitboard != 0){
                // init square occupied by the piece
                int square = BitBoardUtils.getLS1BIndex(bitboard);

                // hash piece
                final_key ^= piece_keys[piece][square];

                // pop LS1B
                bitboard = BitBoardUtils.popBit(bitboard, square);
            }
        }

        // if enpassant square is on board
        if (chessboard.enpassant != no_sq)
            // hash enpassant
            final_key ^= enpassant_keys[chessboard.enpassant];

        // hash castling rights
        final_key ^= castling_keys[chessboard.castle];

        // hash the side only if black is to move
        if (chessboard.side == black) final_key ^= side_key;

        if (chessboard.gameVariants == GameVariants.CRAZY_HOUSE) {
            // hash promoted piece
            long promoted = chessboard.promoted_pieces;
            while (promoted != 0) {
                int square = BitBoardUtils.getLS1BIndex(promoted);
                final_key ^= promoted_keys[square];
                promoted = BitBoardUtils.popBit(promoted, square);
            }

            // hash pocket piece
            for (int piece = P; piece <= k; piece++) {
                int count = chessboard.pocket[piece];
                if (count > 0) {
                    final_key ^= pocket_keys[piece][count];
                }
            }
        }

        if(chessboard.gameVariants == GameVariants.THREE_CHECK) {
            // hash check count
            final_key ^= check_count_keys[white][chessboard.check_count[white]];
            final_key ^= check_count_keys[black][chessboard.check_count[black]];
        }

        // return generated hash key
        return final_key;
    }
}

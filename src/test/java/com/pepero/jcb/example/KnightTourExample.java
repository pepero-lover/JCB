package com.pepero.jcb.example;

import com.pepero.jcb.core.Initializer;
import com.pepero.jcb.core.bitboard.Attacks;
import com.pepero.jcb.core.bitboard.BitBoardUtils;
import com.pepero.jcb.core.constant.BoardSquares;

public class KnightTourExample {
    // knight starting square
    // stuck when the starting square is a4 or d4 or b7.
    public static int knightStartingSquare = BoardSquares.a1;

    public static void main(String[] args) {
        Initializer.init();

        // current knight position
        int knightSquare = knightStartingSquare;

        // visited mask
        long visitedMask = 0L;

        for(int step = 1; step < 64; step++) {
            int minSquare = -1;
            int minMovingValue = 9;

            // calculate available knight moves and remove visited squares
            long knightMoves = Attacks.knight_attacks[knightSquare] & ~visitedMask;

            // calculate move making the minimal move count
            while (knightMoves != 0L) {
                int movingSquare = BitBoardUtils.getLS1BIndex(knightMoves);

                // calculate the available move count on knight moved square
                int moveCount =
                        BitBoardUtils.countBits(
                                Attacks.knight_attacks[movingSquare] & ~visitedMask
                        );

                if(moveCount < minMovingValue) {
                    minMovingValue = moveCount;
                    minSquare = movingSquare;
                }

                knightMoves = BitBoardUtils.popBit(knightMoves, movingSquare);
            }

            if (minSquare == -1) {
                System.out.println("Stuck at step " + step + "!");
                System.out.println("Started square : " +
                        BoardSquares.square_to_coordinates[knightStartingSquare]
                );
                break;
            }

            // add visited mask on current knight square
            visitedMask = BitBoardUtils.setBit(visitedMask, knightSquare);
            // and set the current knight square to calculated move square
            knightSquare = minSquare;

            BitBoardUtils.printBitBoard(visitedMask);
        }

        // for showing fully filled knight tour result.
        visitedMask = BitBoardUtils.setBit(visitedMask, knightSquare);
        BitBoardUtils.printBitBoard(visitedMask);
    }
}

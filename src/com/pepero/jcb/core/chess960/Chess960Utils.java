package com.pepero.jcb.core.chess960;

import java.util.Random;

public class Chess960Utils {
    /**
     * Generate random chess 960 fen
     *
     * @return random chess 960 fen
     */
    public static String generateRandomFen() {
        char[] rank = new char[8];
        for (int i = 0; i < 8; i++) rank[i] = '-';

        Random rand = new Random();

        // light bishop
        int lightBishop = rand.nextInt(4) * 2;

        // black bishop
        int darkBishop = rand.nextInt(4) * 2 + 1;

        rank[lightBishop] = 'B';
        rank[darkBishop] = 'B';

        // queen
        placePiece(rank, 'Q', rand);

        // knight
        placePiece(rank, 'N', rand);
        placePiece(rank, 'N', rand);

        // place rook and king
        // the king is between rook and rook
        int emptyCount = 0;

        for (int i = 0; i < 8; i++) {
            if (rank[i] == '-') {
                if (emptyCount == 0) {
                    rank[i] = 'R';
                } else if (emptyCount == 1) {
                    rank[i] = 'K';
                } else if (emptyCount == 2) {
                    rank[i] = 'R';
                }
                emptyCount++;
            }
        }

        String whiteRank = new String(rank);

        // black fen
        String blackRank = whiteRank.toLowerCase();

        return blackRank + "/pppppppp/8/8/8/8/PPPPPPPP/" + whiteRank + " w KQkq - 0 1";
    }

    /**
     * Place piece randomly on empty square
     *
     * @param rank first / last rank
     * @param piece piece
     * @param rand Random class
     */
    private static void placePiece(char[] rank, char piece, Random rand) {
        int emptySpaces = 0;
        for (char c : rank) {
            if (c == '-') emptySpaces++;
        }

        int target = rand.nextInt(emptySpaces);
        int currentEmpty = 0;

        for (int i = 0; i < 8; i++) {
            if (rank[i] == '-') {
                if (currentEmpty == target) {
                    rank[i] = piece;
                    return;
                }
                currentEmpty++;
            }
        }
    }
}

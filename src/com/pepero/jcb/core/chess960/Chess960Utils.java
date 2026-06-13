package com.pepero.jcb.core.chess960;

import java.security.SecureRandom;

public class Chess960Utils {
    private static final int[][] KNIGHT_PLACEMENTS = {
            {0, 0}, {0, 1}, {0, 2}, {0, 3},
            {1, 1}, {1, 2}, {1, 3},
            {2, 2}, {2, 3},
            {3, 3}
    };

    private static final SecureRandom sr = new SecureRandom();


    /**
     * Generate random chess 960 fen
     *
     * @return random chess 960 fen
     */
    public static String generateRandom960Fen() {
        return generate960FenByIndex(sr.nextInt(960));
    }

    /**
     * Generate chess 960 fen by index
     *
     * @param index index
     *
     * @return chess 960 fen by index
     */
    public static String generate960FenByIndex(int index) {
        if (index < 0 || index > 959) {
            throw new IllegalArgumentException("Index must be between 0 and 959!");
        }

        char[] rank = new char[8];
        for (int i = 0; i < 8; i++) rank[i] = '-';

        int lightBishop = (index % 4) * 2 + 1;
        rank[lightBishop] = 'B';
        index /= 4;

        int darkBishop = (index % 4) * 2;
        rank[darkBishop] = 'B';
        index /= 4;

        int queenPos = index % 6;
        placePieceAtEmpty(rank, 'Q', queenPos);
        index /= 6;

        int[] kp = KNIGHT_PLACEMENTS[index];
        placePieceAtEmpty(rank, 'N', kp[0]);

        placePieceAtEmpty(rank, 'N', kp[1]);

        placePieceAtEmpty(rank, 'R', 0);
        placePieceAtEmpty(rank, 'K', 0);
        placePieceAtEmpty(rank, 'R', 0);

        String whiteRank = new String(rank);
        String blackRank = whiteRank.toLowerCase();

        // 완성된 배치를 기반으로 초기 FEN 문자열 조립
        return blackRank + "/pppppppp/8/8/8/8/PPPPPPPP/" + whiteRank + " w KQkq - 0 1";
    }

    /**
     * Place piece randomly on empty square
     *
     * @param rank first / last rank
     * @param piece piece
     * @param emptyIndex empty index
     */
    private static void placePieceAtEmpty(char[] rank, char piece, int emptyIndex) {
        int emptyCount = 0;
        for (int i = 0; i < 8; i++) {
            if (rank[i] == '-') {
                if (emptyCount == emptyIndex) {
                    rank[i] = piece;
                    return;
                }
                emptyCount++;
            }
        }
    }
}

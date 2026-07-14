package com.pepero.jcb.perft.flair;

import com.pepero.jcb.bitboard.BitBoardUtils;
import com.pepero.jcb.constant.MoveCache;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.ChessboardUtils;
import com.pepero.jcb.core.Initializer;
import com.pepero.jcb.core.MoveGenerator;

import static com.pepero.jcb.constant.SideToMove.*;

public class PerftFlair {
    public static long nodes;

    public static void clearScreen() {
        System.out.print("\033[H");
        System.out.flush();
    }

    public static void perftDriver(Chessboard chessboard, int depth) {
        if (depth == 0) {
            nodes++;
            return;
        }

        if (nodes % 50000 == 0) {
            clearScreen();
            BitBoardUtils.printBitBoard(chessboard.occupancies[both]);
        }

        int[] moveList = MoveCache.SEARCH_MOVE_SINGLE[chessboard.ply];

        int moveCount = MoveGenerator.generateMoves(chessboard, moveList);

        for (int i = 0; i < moveCount; i++) {
            int move = moveList[i];

            if (MoveGenerator.makeMove(chessboard, move)) {
                perftDriver(chessboard, depth - 1);

                MoveGenerator.unmakeMove(chessboard, move);
            }
        }
    }

    /**
     * Perft Test
     */
    public static void perftTest(Chessboard chessboard, int depth) {
        nodes = 0;

        int[] moveList = MoveCache.SEARCH_MOVE_SINGLE[chessboard.ply];
        int moveCount = MoveGenerator.generateMoves(chessboard, moveList);

        for (int i = 0; i < moveCount; i++) {
            int move = moveList[i];

            if (MoveGenerator.makeMove(chessboard, move)) {
                perftDriver(chessboard, depth - 1);
                MoveGenerator.unmakeMove(chessboard, move);
            }
        }
    }

    public static void main(String[] args) {
        Initializer.init();

        Chessboard chessboard = new Chessboard();

        ChessboardUtils.parseFen(chessboard, Chessboard.start_position);

        perftTest(chessboard, 6);
    }
}

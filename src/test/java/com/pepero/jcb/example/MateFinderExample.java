package com.pepero.jcb.example;

import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.ChessboardUtils;
import com.pepero.jcb.core.MoveGenerator;
import com.pepero.jcb.core.constant.MoveCache;
import com.pepero.jcb.core.encode.EncodeMove;

import java.util.ArrayList;
import java.util.List;

public class MateFinderExample {
    public static void main(String[] args) {
        int mateIn = 4;
        String fen = "r3k2r/pb3pp1/1p2p3/2q5/2Pp1P2/3B1Rp1/PP2Q1P1/R1B3K1 b - - 1 1";

        Chessboard startingBoard = new Chessboard(fen);

        ChessboardUtils.printChessBoard(startingBoard);

        List<Integer> pv = searchOr(startingBoard, mateIn * 2 - 2);

        if (pv == null) {
            System.out.println("Mate not found.");
            System.exit(0);
        }

        for(int moveData : pv) {
            System.out.print(EncodeMove.moveToString(moveData) + " ");
        }
        System.out.println();
    }

    private static List<Integer> searchOr(Chessboard board, int depthLeft) {
        int[] moves = new int[MoveCache.MAX_MOVE_SIZE];
        int moveCount = MoveGenerator.generateMoves(board, moves);

        for (int i = 0; i < moveCount; i++) {
            int move = moves[i];
            MoveGenerator.makeMove(board, move);

            List<Integer> found = null;
            if (ChessboardUtils.isCheckmate(board)) {
                found = new ArrayList<>(List.of(move));
            } else if (depthLeft > 1) {
                List<Integer> sub = searchAnd(board, depthLeft - 1);
                if (sub != null) {
                    List<Integer> pv = new ArrayList<>();
                    pv.add(move);
                    pv.addAll(sub);
                    found = pv;
                }
            }

            MoveGenerator.unmakeMove(board, move);
            if (found != null) return found;
        }
        return null;
    }

    private static List<Integer> searchAnd(Chessboard board, int depthLeft) {
        int[] moves = new int[MoveCache.MAX_MOVE_SIZE];
        int moveCount = MoveGenerator.generateMoves(board, moves);
        if (moveCount == 0) return null;

        List<Integer> longestPv = null;
        for (int i = 0; i < moveCount; i++) {
            int move = moves[i];
            MoveGenerator.makeMove(board, move);
            List<Integer> sub = searchOr(board, depthLeft - 1);
            MoveGenerator.unmakeMove(board, move);

            if (sub == null) return null;

            List<Integer> candidate = new ArrayList<>();
            candidate.add(move);
            candidate.addAll(sub);

            if (longestPv == null || candidate.size() > longestPv.size()) {
                longestPv = candidate;
            }
        }
        return longestPv;
    }
}

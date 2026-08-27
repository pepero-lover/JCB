package com.pepero.jcb.example;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.ChessboardUtils;
import com.pepero.jcb.core.MoveGenerator;
import com.pepero.jcb.core.bitboard.BitBoardUtils;
import com.pepero.jcb.core.constant.MoveCache;
import com.pepero.jcb.core.encode.EncodeMove;

import static com.pepero.jcb.core.constant.EncodedPieces.*;
import static com.pepero.jcb.core.constant.SideToMove.*;

public class SimpleEngine {
    private static int MATE_VALUE = 30000;

    private static int MAX_VALUE = 100000;

    private static int best_move = -1;

    private static int ply = 0;

    private static final int[] PIECE_VALUES = new int[12];
    static {
        PIECE_VALUES[P] = 1;  PIECE_VALUES[p] = -1;
        PIECE_VALUES[N] = 3;  PIECE_VALUES[n] = -3;
        PIECE_VALUES[B] = 3;  PIECE_VALUES[b] = -3;
        PIECE_VALUES[R] = 5;  PIECE_VALUES[r] = -5;
        PIECE_VALUES[Q] = 9;  PIECE_VALUES[q] = -9;
        PIECE_VALUES[K] = 100; PIECE_VALUES[k] = -100;
    }

    public static int evaluate(Chessboard chessboard) {
        if(ChessboardUtils.isCheckmate(chessboard)) {
            return chessboard.side == white ? MATE_VALUE : -MATE_VALUE;
        }
        if(ChessboardUtils.isStaleMate(chessboard)) {
            return 0;
        }
        if(ChessboardUtils.getRepetitionCount(chessboard, 3) == 3) {
            return 0;
        }

        int piece_score = 0;

        for(int piece = P; piece <= k; piece++) {
            piece_score += BitBoardUtils.countBits(chessboard.bitboards[piece])
                    * PIECE_VALUES[piece] * 100;
        }

        return chessboard.side == white ? piece_score : -piece_score;
    }

    public static int negamax(Chessboard chessboard, int depth, int alpha, int beta) {
        // if leaf node, evaluate game state
        if (depth == 0) return evaluate(chessboard);

        int best_sofar = -1;

        int old_alpha = alpha;

        int[] move_list = MoveCache.SEARCH_MOVE_SINGLE[chessboard.ply];
        int move_count = MoveGenerator.generateMoves(chessboard, move_list);

        for (int i = 0; i < move_count; i++) {
            // make move
            MoveGenerator.makeMove(chessboard, move_list[i]);

            ply++;

            // calculate score
            int score = -negamax(chessboard, depth - 1, -beta, -alpha);

            ply--;

            // unmake move
            MoveGenerator.unmakeMove(chessboard, move_list[i]);

            if(score >= beta) {
                return score;   // fail soft beta-cutoff
            }


            if(score > alpha) {
                alpha = score; // alpha acts like max in MiniMax

                // if root move
                if (ply == 0) {
                    // associate best move with the best score
                    best_sofar = move_list[i];
                }
            }
        }

        if (old_alpha != alpha)
            // init best move
            best_move = best_sofar;

        return alpha;
    }

    /**
     * @return first index info is cp score, and second index info is move data.
     */
    public static int[] search(Chessboard chessboard, int depth) {
        ply = 0;
        best_move = -1;
        int score = negamax(chessboard, depth, -MAX_VALUE, MAX_VALUE);

        return new int[]{score, best_move};
    }

    public static void main(String[] args) {
        ChessGame chessGame = ChessGame.startPosition();

        int[] output = search(chessGame.getBoardSnapshot(), 8);

        System.out.println(output[0]);
        System.out.println(EncodeMove.moveToString(output[1]));
    }
}

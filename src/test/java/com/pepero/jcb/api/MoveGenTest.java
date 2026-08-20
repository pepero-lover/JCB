package com.pepero.jcb.api;

import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.ChessboardUtils;
import com.pepero.jcb.core.GameVariants;
import com.pepero.jcb.core.MoveGenerator;
import com.pepero.jcb.encode.EncodeMove;

public class MoveGenTest {
    public static void main(String[] args) {
        Chessboard chessboard = new Chessboard(
                Chessboard.racing_kings_start_position,
                GameVariants.RACING_KINGS
        );

        int[] move_list = new int[255];
        int move_count = MoveGenerator.generateMoves(chessboard, move_list);
        for(int i = 0; i < move_count; i++) {
            System.out.println(EncodeMove.moveToString(move_list[i]));
        }
    }
}

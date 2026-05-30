package com.pepero.jcb.util;

import com.pepero.jcb.constant.BoardSquares;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.MoveGenerator;
import com.pepero.jcb.encode.EncodeMove;

public class PerftDriver {
    // leaf nodes (number of positions reached during the test of the move generator at a given depth
    public static long nodes;

    // perft driver
    public static void perftDriver(Chessboard chessboard, int depth){
        // recursion escape condition
        if(depth == 0){
            // increment nodes count (count reached positions)
            nodes++;
            return;
        }

        // create move list instance
        int[] move_list = new int[255];

        // generate moves
        int count = MoveGenerator.generateMoves(chessboard,move_list);

        // loop over generated moves
        for(int move_count = 0; move_count < count; move_count++){
            // make move
            boolean isLegal = MoveGenerator.makeMove(chessboard ,move_list[move_count], MoveGenerator.ALL_MOVES);
            if(!isLegal)
                // skip to the next move
                continue;

            // call perft driver recursively
            perftDriver(chessboard, depth - 1);

            // take back
            chessboard.takeBack();
        }
    }

    // perft test
    public static void perft_test(Chessboard chessboard, int depth){
        System.out.println("\n    Performance test    \n");

        // create move list instance
        int[] move_list = new int[255];

        // generate moves
        int count = MoveGenerator.generateMoves(chessboard,move_list);

        // init start time
        long startTime = TimeUtils.getTimeNt();

        // loop over generated moves
        for(int move_count = 0; move_count < count; move_count++){
            int move = move_list[move_count];

            // make move
            if(!MoveGenerator.makeMove(chessboard ,move, MoveGenerator.ALL_MOVES))
                // skip to the next move
                continue;

            // cumulative nodes
            long cumulative_nodes = nodes; // noes

            // call perft driver recursively
            perftDriver(chessboard, depth - 1);

            // old nodes
            long old_nodes = nodes - cumulative_nodes;

            // take back
            chessboard.takeBack();

            // print move
            StringBuilder sb = new StringBuilder();

            sb.append("    move: ");

            sb.append(BoardSquares.square_to_coordinates[EncodeMove.getMoveSource(move)]);
            sb.append(BoardSquares.square_to_coordinates[EncodeMove.getMoveTarget(move)]);
            sb.append(EncodeMove.promoted_pieces.get(EncodeMove.getMovePromoted(move)) != null ?
                    EncodeMove.promoted_pieces.get(EncodeMove.getMovePromoted(move)) : "");

            sb.append("  nodes: ").append(old_nodes);

            System.out.println(sb);
        }

        // print results
        System.out.println("\n\n    Depth: " + depth);
        System.out.println("    Nodes: " + nodes);
        System.out.println("     Time: " + (TimeUtils.getTimeNt() - startTime) / 1_000_000 + " ms ( + " +
                (TimeUtils.getTimeNt() - startTime) % 1_000_000 + " ns)");
    }
}

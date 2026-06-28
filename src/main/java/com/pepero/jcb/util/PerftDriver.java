package com.pepero.jcb.util;

import com.pepero.jcb.constant.MoveCache;
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
        int[] move_list = new int[MoveCache.MAX_MOVE_SIZE];

        // generate moves
        int count = MoveGenerator.generateMoves(chessboard,move_list);

        // loop over generated moves
        for(int move_count = 0; move_count < count; move_count++){
            // make move
            boolean isLegal = MoveGenerator.makeMove(chessboard, move_list[move_count]);
            if(!isLegal)
                // skip to the next move
                continue;

            // call perft driver recursively
            perftDriver(chessboard, depth - 1);

            MoveGenerator.unmakeMove(chessboard, move_list[move_count]);

            // build hash key for the updated position (after move is made) from scratch
            //long hash_from_scratch = Zobrist.generateHashKey(chessboard);

            // in case if the hash key built from scratch doesn't match
            // the one that was incrementally updated, we interrupt execution
            /*if(chessboard.hash_key != hash_from_scratch){
                System.out.println("\n\n Take back \n");
                System.out.println("move: " + EncodeMove.getMoveString(move_list[move_count]));
                ChessBoardUtils.printChessBoard(chessboard);
                System.out.println("hash key should be: " + Long.toHexString(hash_from_scratch));
                new Scanner(System.in).nextLine();
            }*/
        }
    }

    // perft test
    public static void perftTest(Chessboard chessboard, int depth){
        System.out.println("\n    Performance test    \n");

        // reset nodes count
        nodes = 0;

        // create move list instance
        int[] move_list = new int[MoveCache.MAX_MOVE_SIZE];

        // generate moves
        int count = MoveGenerator.generateMoves(chessboard,move_list);

        // init start time
        long startTime = TimeUtils.getTimeNt();

        // loop over generated moves
        for(int move_count = 0; move_count < count; move_count++){
            int move = move_list[move_count];

            // make move
            if(!MoveGenerator.makeMove(chessboard ,move))
                // skip to the next move
                continue;

            // cumulative nodes
            long cumulative_nodes = nodes; // noes

            // call perft driver recursively
            perftDriver(chessboard, depth - 1);

            // old nodes
            long old_nodes = nodes - cumulative_nodes;

            // take back
            MoveGenerator.unmakeMove(chessboard, move_list[move_count]);

            // print move
            StringBuilder sb = new StringBuilder();

            sb.append("    move: ");

            sb.append(EncodeMove.moveToString(move));

            sb.append("  nodes: ").append(old_nodes);

            System.out.println(sb);
        }

        // init end time
        long endTime = TimeUtils.getTimeNt();

        // calculate duration
        long durationNs = endTime - startTime;
        long durationMs = durationNs / 1_000_000;

        // calculate nodes per second (NPS)
        long nps = 0;
        if (durationNs > 0) {
            nps = (long) ((double) nodes / ((double) durationNs / 1_000_000_000.0));
        }

        // print results
        System.out.println("\n\n    Depth: " + depth);
        System.out.println("    Nodes: " + nodes);
        System.out.println("     Time: " + durationMs + " ms ( + " + (durationNs % 1_000_000) + " ns)");
        System.out.printf("      NPS: %,d (%.2f MNPS)\n", nps, (double) nps / 1_000_000.0);
    }
}
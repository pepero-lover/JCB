package com.pepero.jcb.api.syzygy;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.encode.EncodeMove;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static com.pepero.jcb.constant.EncodedPieces.P;
import static com.pepero.jcb.constant.EncodedPieces.p;

public class SyzygyBestMoveExample {

    public static void main(String[] args) throws IOException {
        Path syzygyDir = Path.of("syzygy/");
        SyzygyTablebase tb = new SyzygyTablebase(syzygyDir);

        ChessGame game = new ChessGame("8/8/4r3/3k4/8/8/2Q5/7K w - - 0 1");

        while (true) {
            MoveInfo bestMove = findBestMove(tb, game);

            game.makeMove(bestMove);
            game.printBoard();
        }
    }

    public static MoveInfo findBestMove(SyzygyTablebase tb, ChessGame game) throws IOException {
        List<MoveInfo> legalMoves = game.getLegalMoves();

        MoveInfo bestMove = null;
        int bestOurWdl = -1;
        int bestOurDtz = Integer.MAX_VALUE;

        for (MoveInfo move : legalMoves) {
            int encoded = move.originEncodedData();
            boolean zeroing = EncodeMove.getMoveCapture(encoded)
                    || EncodeMove.getMovePiece(encoded) == P
                    || EncodeMove.getMovePiece(encoded) == p;

            ChessGame child = new ChessGame(game);
            child.makeMove(move);

            int childWdl = child.probeSyzygyWdl(tb);
            int ourWdl = 4 - childWdl;

            int ourDtz = zeroing ? 1 : 1 + child.probeSyzygyDtz(tb);

            boolean better;
            if (bestMove == null) {
                better = true;
            } else if (ourWdl != bestOurWdl) {
                better = ourWdl > bestOurWdl;
            } else if (ourWdl > 2) {
                better = ourDtz < bestOurDtz;
            } else if (ourWdl < 2) {
                better = ourDtz > bestOurDtz;
            } else {
                better = false;
            }

            if (better) {
                bestMove = move;
                bestOurWdl = ourWdl;
                bestOurDtz = ourDtz;
            }
        }

        if (bestMove == null) {
            throw new IllegalStateException(
                    "No legal moves — position is checkmate or stalemate, nothing for the tablebase to pick");
        }

        return bestMove;
    }
}
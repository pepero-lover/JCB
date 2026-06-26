package com.pepero.jcb.api.exception;

import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.ChessboardUtils;

public class IllegalMoveException extends RuntimeException {
    public IllegalMoveException(String move, String fen) {
        super("Illegal move detected! Move : " + move + ", FEN : " + fen);
        ChessboardUtils.printChessBoard(new Chessboard(fen));
    }
}

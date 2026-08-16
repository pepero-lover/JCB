package com.pepero.jcb.api.exception;

import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.ChessboardUtils;

public class ConvertMoveException extends RuntimeException {
    public ConvertMoveException(String cause, String move, Chessboard chessboard) {
        super("Converting move failed! Cause : " + cause + ", Move : " + move + ", FEN : " +
                ChessboardUtils.getFen(chessboard));
    }

    public ConvertMoveException(String cause, String move) {
        super("Converting move failed! Cause : " + cause + ", Move : " + move);
    }

    public ConvertMoveException(String cause) {
        super("Converting move failed! Cause : " + cause);
    }
}

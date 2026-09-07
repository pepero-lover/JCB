package com.pepero.jcb.api.exception;

import com.pepero.jcb.api.exception.type.ConvertErrorType;
import com.pepero.jcb.api.parse.ConvertType;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.ChessboardUtils;

public class ConvertMoveException extends RuntimeException {
    private final ConvertErrorType errorType;
    private final ConvertType convertType;
    private String occurredMove;
    private String occurredFen;
    private String sequenceContext;

    public ConvertMoveException(String cause, String move, Chessboard chessboard,
                                ConvertType convertType, ConvertErrorType errorType) {
        super("Converting move failed! Cause : " + cause + ", Move : " + move + ", FEN : " +
                ChessboardUtils.getFen(chessboard));
        this.errorType = errorType;
        this.occurredMove = move;
        this.convertType = convertType;
        this.occurredFen = ChessboardUtils.getFen(chessboard);
    }

    public ConvertMoveException(String cause, String move,
                                ConvertType convertType, ConvertErrorType errorType) {
        super("Converting move failed! Cause : " + cause + ", Move : " + move);
        this.errorType = errorType;
        this.convertType = convertType;
        this.occurredMove = move;
    }

    public ConvertMoveException(String cause,
                                ConvertType convertType, ConvertErrorType type) {
        super("Converting move failed! Cause : " + cause);
        this.convertType = convertType;
        this.errorType = type;
    }

    /**
     * Get error type
     */
    public ConvertErrorType getErrorType() {
        return errorType;
    }

    /**
     * Get convert type
     */
    public ConvertType getConvertType() {
        return convertType;
    }

    /**
     * Get occurred move <b>(can be null!)</b>
     */
    public String getOccurredMove() {
        return occurredMove;
    }

    /**
     * Get occurred fen <b>(can be null!)</b>
     */
    public String getOccurredFen() {
        return occurredFen;
    }

    /**
     * Attach context about where in a move sequence this exception occurred.
     *
     * @param moveIndex index of the move that failed within the sequence
     * @param fullSequence the full move sequence string that was being processed
     * @return this exception, with the context attached
     */
    public ConvertMoveException withSequenceContext(int moveIndex, String fullSequence) {
        this.sequenceContext = "Failed at move #" + (moveIndex + 1) + " in sequence \"" + fullSequence + "\"";
        return this;
    }

    /**
     * Get the sequence context, if this exception occurred while processing a
     * move sequence <b>(can be null!)</b>
     */
    public String getSequenceContext() {
        return sequenceContext;
    }

    @Override
    public String getMessage() {
        String message = super.getMessage();
        return sequenceContext == null ? message : message + " (" + sequenceContext + ")";
    }
}
package com.pepero.jcb.api.exception.convert;

import com.pepero.jcb.api.exception.type.ConvertErrorType;
import com.pepero.jcb.api.parse.ConvertType;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.ChessboardUtils;

import java.util.List;

public class ConvertMoveException extends RuntimeException {
    private final ConvertErrorType errorType;
    private final ConvertType convertType;
    private String occurredMove;
    private String occurredFen;
    private String sequenceContext;
    private String pgnParseContext;
    private Integer ply;

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
     * Attach the absolute ply count at which this exception occurred
     * (not the index within a move sequence &mdash; see {@link #withSequenceContext}).
     *
     * @param ply ply count at the point of failure
     * @return this exception, with the context attached
     */
    public ConvertMoveException withPly(int ply) {
        this.ply = ply;
        return this;
    }

    /**
     * Get the sequence context, if this exception occurred while processing a
     * move sequence <b>(can be null!)</b>
     */
    public String getSequenceContext() {
        return sequenceContext;
    }

    /**
     * Attach context about where in a parsed PGN move tree this exception occurred
     * (which ply/move number, and — if inside a variation — the LAN move path from
     * the mainline root down to the branch point).
     *
     * @param variationParents LAN move list from root to the variation's branch point,
     *                          or an empty list if this occurred on the mainline
     * @param fullMove the full move number at the point of failure
     * @param ply the ply count at the point of failure
     * @return this exception, with the context attached
     */
    public ConvertMoveException withPgnParsePosition(List<String> variationParents, int fullMove, int ply) {
        StringBuilder sb = new StringBuilder("PGN parse position: move ")
                .append(fullMove).append(", ply ").append(ply);
        if (variationParents == null || variationParents.isEmpty()) {
            sb.append(" (mainline)");
        } else {
            sb.append(", parents: ").append(variationParents);
        }
        this.pgnParseContext = sb.toString();
        return this;
    }

    /**
     * Get the PGN parse position context, if this exception occurred while parsing
     * a PGN move tree <b>(can be null!)</b>
     */
    public String getPgnParseContext() {
        return pgnParseContext;
    }

    @Override
    public String getMessage() {
        String message = super.getMessage();
        if (ply != null) message += " (Ply : " + ply + ")";
        if (sequenceContext != null) message += " (" + sequenceContext + ")";
        if (pgnParseContext != null) message += " [" + pgnParseContext + "]";
        return message;
    }
}
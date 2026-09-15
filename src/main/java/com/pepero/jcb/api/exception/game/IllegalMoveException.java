package com.pepero.jcb.api.exception.game;

import java.util.List;

public class IllegalMoveException extends RuntimeException {
    private String sequenceContext;
    private String pgnParseContext;
    private Integer ply;

    public IllegalMoveException(String move, String fen) {
        super("Illegal move detected! Move : " + move + ", FEN : " + fen);
    }

    public IllegalMoveException(String message) {
        super(message);
    }

    /**
     * Attach context about where in a move sequence this exception occurred.
     *
     * @param moveIndex index of the move that failed within the sequence
     * @param fullSequence the full move sequence string that was being processed
     * @return this exception, with the context attached
     */
    public IllegalMoveException withSequenceContext(int moveIndex, String fullSequence) {
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

    /**
     * Attach context about where in a parsed PGN move tree this exception occurred
     * (which ply/move number, and if inside a variation, the LAN move path from
     * the mainline root down to the branch point.)
     *
     * @param variationParents LAN move list from root to the variation's branch point.
     * @param fullMove the full move number at the point of failure
     * @param ply the ply count at the point of failure
     */
    public IllegalMoveException withPgnParsePosition(List<String> variationParents, int fullMove, int ply) {
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
     * Attach the absolute ply count at which this exception occurred
     * (not the index within a move sequence &mdash; see {@link #withSequenceContext}).
     *
     * @param ply ply count at the point of failure
     * @return this exception, with the context attached
     */
    public IllegalMoveException withPly(int ply) {
        this.ply = ply;
        return this;
    }

    /**
     * Get the ply count at the point of failure, if attached <b>(can be null!)</b>
     */
    public Integer getPly() {
        return ply;
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
package com.pepero.jcb.api.exception;

public class IllegalMoveException extends RuntimeException {
    private String sequenceContext;

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

    @Override
    public String getMessage() {
        String message = super.getMessage();
        return sequenceContext == null ? message : message + " (" + sequenceContext + ")";
    }
}
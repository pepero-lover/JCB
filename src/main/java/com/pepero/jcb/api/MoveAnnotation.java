package com.pepero.jcb.api;

/**
 * Move annotation for pgn annotation <br>
 * If you want to change annotation, use {@link ChessGame#setCurrentMoveCal(String)}, {@link ChessGame#setCurrentMoveClockMilliSeconds(long)}, etc.
 */
public class MoveAnnotation {
    String eval;
    String csl;
    String cal;
    String comment;
    String nag;
    String clk;
    String timeStamp;

    public String getEval() { return eval; }
    public String getCsl() { return csl; }
    public String getCal() { return cal; }
    public String getComment() { return comment; }
    public String getNag() { return nag; }
    public String getClk() { return clk; }
    public String getTimeStamp() { return timeStamp; }

    public boolean isEmpty() {
        return eval == null && csl == null && cal == null
                && comment == null && nag == null && clk == null && timeStamp == null;
    }
}
package com.pepero.jcb.api.parse.pgn;

public class MoveAnnotation {
    public String eval; // engine eval data
    public String csl; // square light data
    public String cal; // arrow light data

    public String comment; // comment data
    public String nag; // nag data like $2 (?), $3 (!!)

    public String clk; // clock data
    public String timeStamp; // time stamp (time elapsed)

    public boolean isEmpty() { return eval == null && csl == null && cal == null; }
}
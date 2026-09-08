package com.pepero.jcb.core;

/**
 * What's the difference between {@link #LICHESS} and {@link #FAIRY_STOCKFISH}? : <p>
 * If dialect is {@link FENDialect#LICHESS} and the variant is 3 check, the fen is going to be <br>
 * {@code "... - 3+3 0 1"} and the 3+3 is white's and black's remaining check count. <br>
 * If dialect is {@link FENDialect#FAIRY_STOCKFISH} and the variant is 3 check, the fen is going to be <br>
 * {@code "... - 0 1 +0+0"} and the +0+0 is white's and black's checked count
 */
public enum FENDialect {
    // getFen() dialects

    // 3 Check
    // LICHESS : ... - 3+3 0 1
    // FAIRY_STOCKFISH ... - 0 1 +0+0

    LICHESS,
    FAIRY_STOCKFISH
}

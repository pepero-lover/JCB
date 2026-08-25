package com.pepero.jcb.api.perft;

/**
 * Perft move result for storing perft result for each move. <br>
 * <a href="https://chessprogramming.org/Perft#divide">About dividing</a>
 *
 * @param moveStr move string LAN (or UCI)
 * @param nodes calculated nodes count on this move
 */
public record PerftMoveResult(
            String moveStr,
            long nodes
    ) { }
package com.pepero.jcb.api.perft;

import java.util.List;

/**
 * The result data when the {@link PerftDriver}'s function finished
 *
 * @param moveResults move result for each move. (dividing) <a href="https://chessprogramming.org/Perft#divide">About dividing</a>
 * @param totalNodes total calculated nodes count
 * @param time elapsed time (ms)
 * @param nps nodes per second
 */
public record PerftResult(
        List<PerftMoveResult> moveResults,
        long totalNodes,
        long time,
        long nps
) {
}

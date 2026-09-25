package com.pepero.jcb.api.dto;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.SyzygyAnalyzer;
import com.pepero.jcb.api.syzygy.SyzygyTablebase;

/**
 * Syzygy move dto for ranking move on {@link SyzygyAnalyzer#findBestMove(ChessGame, SyzygyTablebase)}
 *
 * @param move move info data
 * @param ourWdl our wdl (win draw loss) data
 * @param distance distance to zeroing (DTZ)
 * @param zeroing is this move zeroing
 * @param mate is this move checkmating the opponent's king
 */
public record SyzygyMoveDTO(
        MoveInfo move, int ourWdl, int distance, boolean zeroing, boolean mate
) {
    @Override
    public String toString() {
        return move.toLanString() +
                ", wdl = " + ourWdl +
                ", distance to zero = " + distance +
                ", zeroing = " + zeroing;
    }
}

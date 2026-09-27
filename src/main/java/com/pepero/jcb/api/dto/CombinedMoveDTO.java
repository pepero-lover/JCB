package com.pepero.jcb.api.dto;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.CombinedAnalyzer;
import com.pepero.jcb.api.gaviota.GaviotaTablebase;
import com.pepero.jcb.api.syzygy.SyzygyTablebase;

/**
 * Syzygy move dto for ranking move on {@link CombinedAnalyzer#findRankedMoves(ChessGame, SyzygyTablebase, GaviotaTablebase)}
 *
 * @param move move info data
 * @param wdl wdl (win draw loss) data
 * @param dtz distance to zeroing (DTZ)
 * @param zeroing is this move zeroing
 * @param mate is this move checkmating the opponent's king
 * @param dtm distance to mate (DTM). Can be null if the position's total piece count is more than 5, or variant is not standard.
 */
public record CombinedMoveDTO(
        MoveInfo move,
        int wdl,
        int dtz,
        boolean zeroing,
        boolean mate,
        Integer dtm
) {
    @Override
    public String toString() {
        return move.toLanString() +
                ", wdl = " + wdl +
                ", distance to zero = " + dtz +
                ((dtm != null) ? ", distance to mate = " + dtm : "");
    }
}
package com.pepero.jcb.api.dto;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.SyzygyAnalyzer;
import com.pepero.jcb.api.gaviota.GaviotaTablebase;
import com.pepero.jcb.api.syzygy.SyzygyTablebase;

/**
 * Syzygy move dto for ranking move on {@link SyzygyAnalyzer#findBestMove(ChessGame, SyzygyTablebase)}
 *
 * @param move move info data
 * @param ourWdl our wdl (win draw loss) data
 * @param distance distance to zeroing (DTZ)
 */
public record SyzygyMoveDTO(
        MoveInfo move, int ourWdl, int distance
) {
    @Override
    public String toString() {
        return "SyzygyMoveDTO{" +
                "move=" + move +
                ", ourWdl=" + ourWdl +
                ", distance=" + distance +
                '}';
    }
}

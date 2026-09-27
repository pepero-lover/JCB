package com.pepero.jcb.api.dto;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.GaviotaAnalyzer;
import com.pepero.jcb.api.gaviota.GaviotaTablebase;

/**
 * Gaviota move dto for ranking move on {@link GaviotaAnalyzer#findRankedMoves(ChessGame, GaviotaTablebase)}
 *
 * @param move move info data
 * @param ourWdl our wdl (win draw loss) data
 * @param distance distance to mate (DTM)
 */
public record GaviotaMoveDTO(MoveInfo move, int ourWdl, int distance) {
    @Override
    public String toString() {
        return move.toLanString() +
                ", wdl = " + ourWdl +
                ", distance to mate = " + distance;
    }
}
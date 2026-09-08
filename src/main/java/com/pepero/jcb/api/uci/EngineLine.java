package com.pepero.jcb.api.uci;

import com.pepero.jcb.api.parse.ConvertStringMoveUtils;

/**
 * Engine line data for storing analysis data
 *
 * @param depth engine depth data
 * @param pvNumber pv number data
 * @param score score data
 * @param pv LAN (or UCI) pv data
 * @param sanPv san pv data
 * @param isBound is upperbound, lowerbound string
 */
public record EngineLine(int depth, int pvNumber, EngineCp score, String pv, String sanPv, boolean isBound) {
    /**
     * @return {from square, to square} of the best move in this PV, for arrow rendering.
     *         e.g. "d2d3 f7f5" -> {"d2", "d3"}
     */
    public String[] bestMoveSquares() {
        return ConvertStringMoveUtils.parseLanSquares(pv.trim().split("\\s+")[0]);
    }

    @Override
    public String toString() {
        return "EngineLine{" +
                "depth=" + depth +
                ", pvNumber=" + pvNumber +
                ", score='" + score + '\'' +
                ", pv='" + pv + '\'' +
                ", sanPv='" + sanPv + '\'' +
                ", isBound=" + isBound +
                '}';
    }
}
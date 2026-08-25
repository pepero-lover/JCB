package com.pepero.jcb.api.uci;

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
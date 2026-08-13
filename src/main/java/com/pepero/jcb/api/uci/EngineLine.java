package com.pepero.jcb.api.uci;

public record EngineLine(int depth, int pvNumber, String score, String pv, String sanPv, boolean isBound) {
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
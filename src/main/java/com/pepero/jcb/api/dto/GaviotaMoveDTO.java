package com.pepero.jcb.api.dto;

public record GaviotaMoveDTO(MoveInfo move, int ourWdl, int distance) {
    @Override
    public String toString() {
        return "SyzygyMoveDTO{" +
                "move=" + move +
                ", ourWdl=" + ourWdl +
                ", distance=" + distance +
                '}';
    }
}
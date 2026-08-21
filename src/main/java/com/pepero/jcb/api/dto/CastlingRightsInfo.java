package com.pepero.jcb.api.dto;

public record CastlingRightsInfo(
        boolean whiteKingSide, boolean whiteQueenSide,
        boolean blackKingSide, boolean blackQueenSide
) {
    public boolean hasAny() {
        return whiteKingSide || whiteQueenSide || blackKingSide || blackQueenSide;
    }
}
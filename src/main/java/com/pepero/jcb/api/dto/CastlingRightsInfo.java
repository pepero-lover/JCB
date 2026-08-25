package com.pepero.jcb.api.dto;

import com.pepero.jcb.api.ChessGame;

/**
 * Castling rights info dto <br>
 * Used on {@link ChessGame#getCastlingRights()}
 */
public record CastlingRightsInfo(
        boolean whiteKingSide, boolean whiteQueenSide,
        boolean blackKingSide, boolean blackQueenSide
) {
    public boolean hasWhiteAny() {
        return whiteKingSide || whiteQueenSide;
    }

    public boolean hasBlackAny() {
        return blackKingSide || blackQueenSide;
    }

    public boolean hasAny() {
        return whiteKingSide || whiteQueenSide || blackKingSide || blackQueenSide;
    }
}
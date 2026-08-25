package com.pepero.jcb.api.dto;

import com.pepero.jcb.api.parse.pgn.MoveAnnotation;

/**
 * Move data DTO
 *
 * @param id node uuid
 * @param fen the position after this move fen
 * @param moveData move data
 * @param annotation move annotation
 */
public record MoveDataDTO(
        long id,
        String fen,
        MoveInfo moveData,
        MoveAnnotation annotation
) {
    @Override
    public String toString() {
        if(moveData == null) return "";
        return moveData.toString();
    }
}

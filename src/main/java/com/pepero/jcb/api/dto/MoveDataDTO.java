package com.pepero.jcb.api.dto;

import com.pepero.jcb.api.MoveAnnotation;

/**
 * Move data DTO
 *
 * @param id node uuid
 * @param ply ply (move clock but starting at 0, and increases 1 per a move)
 * @param fullMovePly full move ply (full move clock for fen full move but increases 1 per a move)
 * @param san san move string
 * @param fen the position after this move fen
 * @param moveData move data
 * @param annotation move annotation
 */
public record MoveDataDTO(
        long id,
        int ply,
        int fullMovePly,
        String san,
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

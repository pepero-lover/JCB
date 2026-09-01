package com.pepero.jcb.api.dto;

import java.util.List;

/**
 * Storing Move tree data DTO
 *
 * @param id uuid of this move data
 * @param moveData played move data
 * @param ply the distance between this node and the root node
 * @param fullMovePly full move number but increases 1 per a move (on FEN, increases only black has moved)
 * @param children children data (index 0 is mainline, other are variation)
 * @param san san move string
 * @param annotation annotation for pgn
 */
public record MoveNodeDTO(
        long id,
        int ply,
        int fullMovePly,
        MoveInfo moveData,
        List<MoveNodeDTO> children,
        String san,
        MoveAnnotationDTO annotation
) { }
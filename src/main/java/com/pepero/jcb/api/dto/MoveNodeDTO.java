package com.pepero.jcb.api.dto;

import java.util.List;

public record MoveNodeDTO(
        long id,
        MoveInfo moveData,
        List<MoveNodeDTO> children,
        String san,
        MoveAnnotationDTO annotation
) { }
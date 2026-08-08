package com.pepero.jcb.api.analyze;

import com.pepero.jcb.api.enums.Square;

import java.util.List;

public record TacticFinding(
        TacticType type,
        TacticSeverity severity,
        Square primarySquare,
        List<Square> targetSquares
) {}

package com.pepero.jcb.api.analyze;

import com.pepero.jcb.api.enums.Square;

import java.util.List;

public record TacticFinding(
        TacticType type,
        Square primarySquare,
        List<Square> targetSquares
) {}

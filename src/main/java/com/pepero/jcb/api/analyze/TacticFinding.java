package com.pepero.jcb.api.analyze;

import com.pepero.jcb.api.enums.Square;

import java.util.List;

/**
 * Storing found tactic data
 *
 * @param type tactic type like {@link TacticType#FORK}, {@link TacticType#PIN}.
 * @param severity if this tactic should be solved right now, the severity is {@link TacticSeverity#IMMEDIATE},
 *                 otherwise, the severity is {@link TacticSeverity#LATENT}
 * @param primarySquare the primary square (if knight forked on e4 square, the primary square is e4.)
 * @param targetSquares the target squares (if knight forked on e4 square and the black king is on c5 and the black queen
 *                      is on g5, the target square is c5, g5.)
 */
public record TacticFinding(
        TacticType type,
        TacticSeverity severity,
        Square primarySquare,
        List<Square> targetSquares
) {}

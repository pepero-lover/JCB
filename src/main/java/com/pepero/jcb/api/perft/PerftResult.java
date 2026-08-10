package com.pepero.jcb.api.perft;

import java.util.List;

public record PerftResult(
        List<PerftMoveResult> moveResults,
        long totalNodes,
        long time,
        long nps
) {
}

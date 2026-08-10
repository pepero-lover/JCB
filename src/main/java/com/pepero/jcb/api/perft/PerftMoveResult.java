package com.pepero.jcb.api.perft;

public record PerftMoveResult(
            String moveStr,
            long nodes
    ) { }
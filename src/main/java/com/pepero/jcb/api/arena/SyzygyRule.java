package com.pepero.jcb.api.arena;

public record SyzygyRule(
        int maxPieceCount,      // adjust if the position's piece count is less than or equal to this piece count 
        int moveCount           // adjust if during this full move count and the result is the same
) {}
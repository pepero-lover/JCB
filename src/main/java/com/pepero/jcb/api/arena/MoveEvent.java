package com.pepero.jcb.api.arena;

public record MoveEvent(
        String fen,
        String moveLan,
        String moveSan,
        int roundNumber,
        boolean whiteToMove,
        long timeSpentMs,
        long whiteTimeLeftMs,
        long blackTimeLeftMs
) {}
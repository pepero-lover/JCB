package com.pepero.jcb.api.dto;

public record TablebaseResult(
        String wdl, // win loss draw
        int dtz, // distance to zero
        String bestMoveSan, // best move (san)
        String bestMoveLan // best move (lan)
) {}
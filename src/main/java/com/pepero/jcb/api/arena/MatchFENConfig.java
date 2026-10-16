package com.pepero.jcb.api.arena;

public record MatchFENConfig(
        String fenWhenEngine1White,
        String fenWhenEngine1Black
) {
    public static MatchFENConfig fixed(String fen) {
        return new MatchFENConfig(fen, fen);
    }
}
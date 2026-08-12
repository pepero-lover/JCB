package com.pepero.jcb.api.arena;

public record FENSettingConfig(
        String fenWhenEngine1White,
        String fenWhenEngine1Black
) {
    public static FENSettingConfig fixed(String fen) {
        return new FENSettingConfig(fen, fen);
    }
}
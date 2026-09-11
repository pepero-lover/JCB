package com.pepero.jcb.api.pgn;

import com.pepero.jcb.api.ChessGame;

import java.util.Map;

public record PGNGameStub(Map<String, String> headers, String rawPGN) {
    /**
     * Lazily parses movetext into a full ChessGame — only call when you actually need moves.
     */
    public ChessGame toChessGame() {
        return ChessGame.fromPGN(rawPGN);
    }
}

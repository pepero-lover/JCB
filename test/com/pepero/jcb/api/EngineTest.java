package com.pepero.jcb.api;

import com.pepero.jcb.api.arena.EngineArena;
import com.pepero.jcb.api.arena.MatchConfig;
import com.pepero.jcb.api.uci.UCIEngineWrapper;

public class EngineTest {
    public static void main(String[] args) {
        UCIEngineWrapper stockfish = new UCIEngineWrapper("engines/stockfish/stockfish.exe", 100, null);
        UCIEngineWrapper myEngine = new UCIEngineWrapper("engines/stockfish/stockfish.exe", 100, null);

        MatchConfig config = new MatchConfig.Builder()
                .openingBook("opening/gm2001.bin")
                .depthLimit(10)
                .build();

        while (true) {
            EngineArena arena = new EngineArena(new ChessGame(), stockfish, myEngine, config);

            try {
                arena.startMatch();
            } catch (Exception e) {
                stockfish.close();
                myEngine.close();
                throw new RuntimeException(e);
            }
        }
    }
}

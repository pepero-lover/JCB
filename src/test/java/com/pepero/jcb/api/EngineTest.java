package com.pepero.jcb.api;

import com.pepero.jcb.api.arena.EngineArena;
import com.pepero.jcb.api.arena.MatchConfig;
import com.pepero.jcb.api.uci.UCIEngineWrapper;

public class EngineTest {
    public static void main(String[] args) {
        UCIEngineWrapper engine1 = new UCIEngineWrapper("engine1/path/engine.exe", 100, null);
        UCIEngineWrapper engine2 = new UCIEngineWrapper("engine2/path/engine.exe", 100, null);

        MatchConfig config = new MatchConfig.Builder()
                .openingBook("opening/path/opening.bin")
                .depthLimit(10)
                .build();

        for(int i = 0; i < 10; i++) {
            EngineArena arena = new EngineArena(new ChessGame(), engine1, engine2, config);

            try {
                arena.startMatch();
            } catch (Exception e) {
                engine1.close();
                engine2.close();
                throw new RuntimeException(e);
            }
        }
    }
}
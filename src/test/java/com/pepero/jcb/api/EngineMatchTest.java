package com.pepero.jcb.api;

import com.pepero.jcb.api.arena.EngineArena;
import com.pepero.jcb.api.arena.EngineWinner;
import com.pepero.jcb.api.arena.MatchConfig;
import com.pepero.jcb.api.dto.MatchResult;
import com.pepero.jcb.api.enums.GameResult;
import com.pepero.jcb.api.uci.UCIEngineWrapper;

import java.io.File;

public class EngineMatchTest {
    public static void main(String[] args) {
        String enginePath = new File("engine/stockfish-18.exe").getAbsolutePath();

        int win = 0;
        int draw = 0;
        int lose = 0;

        try (
                UCIEngineWrapper engine1 = new UCIEngineWrapper(
                        new ProcessBuilder(enginePath),
                        100,
                        null
                );
                UCIEngineWrapper engine2 = new UCIEngineWrapper(
                        new ProcessBuilder(enginePath),
                        100,
                        null
                )
        ) {
            MatchConfig config = new MatchConfig.Builder()
                    .openingBook("engine/opening.bin")
                    .randomBookMove(false)
                    .timeControl(100, 0)
                    .build();

            EngineArena arena = new EngineArena(
                    engine1,
                    engine2,
                    config
            );

            for (int i = 0; i < 300; i++) {
                MatchResult matchResult = arena.startMatch();

                if(matchResult.engineWinner() == EngineWinner.ENGINE1) {
                    win++;
                } else if(matchResult.engineWinner() == EngineWinner.DRAW) {
                    draw++;
                } else {
                    lose++;
                }

                System.out.println(matchResult.pgn());
                System.out.println();

                arena.swapEngine();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        System.out.println(win);
        System.out.println(draw);
        System.out.println(lose);
    }
}
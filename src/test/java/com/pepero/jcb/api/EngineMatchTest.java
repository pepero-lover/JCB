package com.pepero.jcb.api;

import com.pepero.jcb.api.arena.*;
import com.pepero.jcb.api.dto.MatchResult;
import com.pepero.jcb.api.enums.GameResult;
import com.pepero.jcb.api.uci.UCIEngineWrapper;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.ChessboardUtils;

import java.io.File;

public class EngineMatchTest {
    public static void main(String[] args) {
        String engine1Path = new File("engine/stockfish-18.exe").getAbsolutePath();
        String engine2Path = new File("engine/Peperobot_Cpp.exe").getAbsolutePath();

        int win = 0;
        int draw = 0;
        int lose = 0;

        try (
                UCIEngineWrapper engine1 = new UCIEngineWrapper(
                        new ProcessBuilder(engine1Path),
                        100,
                        null
                );
                UCIEngineWrapper engine2 = new UCIEngineWrapper(
                        new ProcessBuilder(engine2Path),
                        100,
                        null
                )
        ) {
            engine1.setOptionSync("Skill Level","15");

            MatchConfig config = new MatchConfig.Builder()
                    .openingBook("engine/opening.bin")
                    .randomBookMove(false)
                    .engine1Config(new EngineConfig())
                    .build();

            EngineArena arena = new EngineArena(
                    config
            );

            arena.setArenaListener(new EngineArena.ArenaListener() {
                @Override
                public void onMovePlayed(MoveEvent event) {

                }

                @Override
                public void onMatchFinished(MatchFinishedEvent event) {

                }
            });

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
                System.out.println(matchResult.engineWinner());
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
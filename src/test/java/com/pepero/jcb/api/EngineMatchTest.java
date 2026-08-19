package com.pepero.jcb.api;

import com.pepero.jcb.api.arena.*;
import com.pepero.jcb.api.dto.MatchResult;
import com.pepero.jcb.core.GameVariants;

import java.io.File;
import java.util.List;
import java.util.Map;

public class EngineMatchTest {
    public static void main(String[] args) {
        String engine1Path = new File("engines/stockfish/stockfish.exe").getAbsolutePath();
        String engine2Path = new File("engines/stockfish/stockfish.exe").getAbsolutePath();

        String folder = new File("engines/stockfish/").getAbsolutePath();

        try {
            EngineConfig engine1Config = new EngineConfig(
                    "Stockfish 18",
                    engine1Path,
                    folder,
                    List.of(),
                    EngineConfig.Protocol.UCI,
                    Map.of(),
                    new EngineLimit(1000, 100)
            );

            EngineConfig engine2Config = new EngineConfig(
                    "Stockfish 18",
                    engine2Path,
                    folder,
                    List.of(),
                    EngineConfig.Protocol.UCI,
                    Map.of(),
                    new EngineLimit(1000, 100)
            );

            MatchConfig config = new MatchConfig.Builder()
                    .openingBook("opening/gm2001.bin")
                    .drawRule(new AdjudicationRule(
                            40,
                            16,
                            0,
                            20
                    ))
                    .resignRule(new AdjudicationRule(
                            40,
                            16,
                            700,
                            50
                    ))
                    .engine1Config(engine1Config)
                    .engine2Config(engine2Config)
//                    .fenSetting(new FENSettingConfig(
//                            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKB1R w KQkq - 0 1",
//                            "rnbqkb1r/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
//                            ))
                    .totalGames(10)
                    .concurrency(4)
                    .build();

            ArenaRunner arena = new ArenaRunner(config);
            MatchStatistics result = arena.run(new ArenaRunner.RunnerListener() {
                @Override
                public void onGameFinished(int roundNumber, MatchResult result, MatchStatistics runningStats) {
                    System.out.println("ROUND " + roundNumber);
                    System.out.println(result.pgn());
                    System.out.println(result.engineWinner() + " WON");
                    System.out.println(result.reason());
                    System.out.println("Total game completed : " + runningStats.getTotalCompleted());
                    System.out.println();
                }

                @Override
                public void onGameFailed(int roundNumber, Throwable cause) {
                    try {
                        throw cause;
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            System.out.println();
            System.out.println();
            System.out.println(result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
package com.pepero.jcb.api;

import com.pepero.jcb.api.arena.*;
import com.pepero.jcb.api.dto.MatchResult;
import com.pepero.jcb.api.syzygy.SyzygyTablebase;
import com.pepero.jcb.core.GameVariants;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;

public class EngineMatchTest {
    public static void main(String[] args) {
        String engine1Path = new File("engine/stockfish.exe").getAbsolutePath();
        String engine2Path = new File("engine/stockfish.exe").getAbsolutePath();

        String folder = new File("engine/").getAbsolutePath();

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
                    .openingBook("engine/opening.bin")
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
//                    .syzygyRule(new SyzygyRule(
//                            5,
//                            10
//                    ))
//                    .syzygyTablebase(new SyzygyTablebase(Path.of("syzygy/")))

                    .engine1Config(engine1Config)
                    .engine2Config(engine2Config)
//                    .fenSetting(new FENSettingConfig(
//                            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKB1R w KQkq - 0 1",
//                            "rnbqkb1r/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
//                            ))
                    .totalGames(10)
                    .concurrency(4)
                    .showClk(true)
                    .showEval(false)
                    .showPv(false)
                    .build();

            CountDownLatch doneLatch = new CountDownLatch(1);

            ArenaRunner runner = new ArenaRunner(config);
            Thread runnerThread = new Thread(() -> runner.run(new ArenaRunner.RunnerListener() {
                @Override
                public void onGameFinished(int roundNumber, MatchResult result, MatchStatistics runningStats) {
                    String sb = "ROUND " + roundNumber + "\n" +
                            result.pgn() + "\n" +
                            result.engineWinner() + " WON\n" +
                            result.reason() + "\n" +
                            "Total game completed : " + runningStats.getTotalCompleted() + "\n\n";

                    System.out.print(sb);
                }

                @Override
                public void onGameFailed(int roundNumber, Throwable cause) {
                    try {
                        throw cause;
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public void onAllGamesFinished(MatchStatistics finalStats, boolean stoppedEarly) {
                    System.out.println(stoppedEarly ? "Aborted midway" : "All games finished successfully");
                    System.out.println("Final stats: " + finalStats);
                    doneLatch.countDown();
                }
            }));
            runnerThread.start();

            System.out.println("To stop, type 'stop' and Enter");
            Thread inputThread = new Thread(() -> {
                Scanner sc = new Scanner(System.in);
                while (sc.hasNextLine()) {
                    if (sc.nextLine().trim().equalsIgnoreCase("stop")) {
                        runner.stop();
                        break;
                    }
                }
            });

            inputThread.setDaemon(true);
            inputThread.start();

            doneLatch.await();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
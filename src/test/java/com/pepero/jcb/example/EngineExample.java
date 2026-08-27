package com.pepero.jcb.example;

import com.pepero.jcb.api.arena.*;
import com.pepero.jcb.api.arena.MatchResult;

import java.io.File;
import java.util.List;
import java.util.Map;

public class EngineExample {
    public static void main(String[] args) {
        // Specify the executable paths for the engines.
        String engine1Path = new File("engine/stockfish").getAbsolutePath();
        String engine2Path = new File("engine/stockfish").getAbsolutePath();

        // Specify the engines' working directory.
        String folder = new File("engine/").getAbsolutePath();

        try {
            // Create the configuration for Engine 1.
            EngineConfig engine1Config = new EngineConfig(
                    "Stockfish 18", // Display name of the engine
                    engine1Path, // Engine executable path
                    folder, // Working directory
                    List.of(), // Engine args
                    EngineConfig.Protocol.UCI, // Protocol type
                    Map.of(), // Option settings
                    new EngineLimit(10) // Engine limits (time control and depth settings)
                    // Here we only use a depth of 10, but if you want a time control instead,
                    // you could use new EngineLimit(10_000, 300) for a 10+0.3 setup
                    // (10000 ms base time, 300 ms Fischer increment).
            );

            // Create the configuration for Engine 2.
            EngineConfig engine2Config = new EngineConfig(
                    "Stockfish 18",
                    engine2Path,
                    folder,
                    List.of(),
                    EngineConfig.Protocol.UCI,
                    Map.of(),
                    new EngineLimit(10)
            );

            // Create the match configuration.
            // openingBook() auto-detects the file extension:
            //   .bin -> Polyglot opening book (queried move by move)
            //   .epd -> EPD opening book (a fixed starting position per game)
            MatchConfig config = new MatchConfig.Builder()
                    .openingBook("engine/opening.bin") // You can set an opening book.
                    .repeatOpening(true) // Play each opening twice, swapping colors.
                    .totalGames(10) // Total number of games to play
                    .concurrency(1) // Number of threads to use (only 1 here)
                    .engine1Config(engine1Config) // Pull in Engine 1's configuration
                    .engine2Config(engine2Config) // Pull in Engine 2's configuration
                    .build();

            // Create the match runner class.
            ArenaRunner arena = new ArenaRunner(config);

            // Start the arena match.
            // As a listener, we'll export the PGN whenever a game finishes.
            MatchStatistics statistics = arena.run(new ArenaRunner.RunnerListener() {
                @Override
                public void onGameFinished(int roundNumber, MatchResult result, MatchStatistics runningStats) {
                    System.out.println("Round " + roundNumber);
                    System.out.println("RESULT : " + result.result() + "(" + result.engineWinner() + ")");
                    System.out.println("PGN : ");
                    System.out.println(result.pgn());
                    System.out.println();
                }
            });

            System.out.println("Engine 1 WDL");
            System.out.println("WIN  :  " + statistics.getEngine1Wins());
            System.out.println("DRAW :  " + statistics.getDraws());
            System.out.println("LOSE :  " + statistics.getEngine2Wins());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

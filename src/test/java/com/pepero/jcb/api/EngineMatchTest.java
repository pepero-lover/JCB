package com.pepero.jcb.api;

import com.pepero.jcb.api.arena.*;
import com.pepero.jcb.api.dto.MatchResult;
import com.pepero.jcb.api.enums.GameResult;
import com.pepero.jcb.api.uci.UCIEngineWrapper;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.ChessboardUtils;

import java.io.File;
import java.util.List;
import java.util.Map;

public class EngineMatchTest {
    public static void main(String[] args) {
        String engine1Path = new File("engine/stockfish").getAbsolutePath();
        String engine2Path = new File("engine/stockfish").getAbsolutePath();

        String folder = new File("engine/").getAbsolutePath();

        try {
            EngineConfig engine1Config = new EngineConfig(
                    "Stockfish 18",
                    engine1Path,
                    folder,
                    List.of(),
                    EngineConfig.Protocol.UCI,
                    Map.of(),
                    new EngineLimit(10)
            );

            EngineConfig engine2Config = new EngineConfig(
                    "Stockfish 18",
                    engine2Path,
                    folder,
                    List.of(),
                    EngineConfig.Protocol.UCI,
                    Map.of(),
                    new EngineLimit(10)
            );

            MatchConfig config = new MatchConfig.Builder()
                    .openingBook("engine/opening.bin")
                    .randomBookMove(false)
                    .drawRule(new AdjudicationRule(
                            40,
                            16,
                            0,
                            10
                    ))
                    .resignRule(new AdjudicationRule(
                            40,
                            16,
                            700,
                            50
                    ))
                    .engine1Config(engine1Config)
                    .engine2Config(engine2Config)
                    .totalGames(10)
                    .concurrency(4)
                    .build();

            ArenaRunner arena = new ArenaRunner(config);
            arena.run(new ArenaRunner.RunnerListener() {
                @Override
                public void onGameFinished(int roundNumber, MatchResult result, MatchStatistics runningStats) {
                    System.out.println(result.pgn());
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
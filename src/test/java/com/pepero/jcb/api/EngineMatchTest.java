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

        int win = 0;
        int draw = 0;
        int lose = 0;

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
                    .engine1Config(engine1Config)
                    .engine2Config(engine2Config)
                    .build();

            EngineArena arena = new EngineArena(config);

            for (int i = 1; i <= 300; i++) {
                MatchResult matchResult = arena.startMatch(i);

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
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        System.out.println(win);
        System.out.println(draw);
        System.out.println(lose);
    }
}
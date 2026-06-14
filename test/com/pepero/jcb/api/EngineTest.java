package com.pepero.jcb.api;

import com.pepero.jcb.api.arena.EngineArena;
import com.pepero.jcb.api.arena.MatchConfig;
import com.pepero.jcb.api.arena.MiniOpeningBook;
import com.pepero.jcb.api.uci.UCIEngineWrapper;
import com.pepero.jcb.util.TimeUtils;

import java.util.List;

public class EngineTest {
    public static void main(String[] args) {
        List<String> openings = MiniOpeningBook.getStandardOpenings();

        UCIEngineWrapper stockfish = new UCIEngineWrapper("engines/stockfish/stockfish.exe", 100, null);
        UCIEngineWrapper myEngine = new UCIEngineWrapper("engines/stockfish/stockfish.exe", 100, null);

        int matchNumber = 1;

        long startTime = TimeUtils.getTimeMs();

        for(String fen : openings) {
            long startMatchTime = TimeUtils.getTimeMs();

            ChessGame chessGame = new ChessGame(fen);

            MatchConfig config = new MatchConfig.Builder()
                    .depthLimit(10)
                    .build();

            EngineArena arena = new EngineArena(chessGame, stockfish, myEngine, config);

            try {
                arena.startMatch();
                System.out.println("[Match " + matchNumber + "] finished.");
                System.out.println("PGN : ");
                System.out.println();
                System.out.println(chessGame.getPGN());
                System.out.println();
                System.out.println("Match Time : " + (TimeUtils.getTimeMs() - startMatchTime));
            } catch (Exception e) {
                System.out.println(chessGame);
                stockfish.close();
                myEngine.close();
                throw new RuntimeException(e);
            }
        }

        System.out.println("All Time : " + (TimeUtils.getTimeMs() - startTime));

        stockfish.close();
        myEngine.close();
    }
}

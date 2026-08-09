package com.pepero.jcb.api.arena;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.dto.MatchResult;
import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.api.enums.GameResult;
import com.pepero.jcb.api.parse.ConvertStringMoveUtils;
import com.pepero.jcb.api.uci.UCIEngineWrapper;
import com.pepero.jcb.api.book.PolyglotHashUtils;

public class EngineArena {

    private MatchConfig matchConfig;
    private UCIEngineFactory factory;

    public interface ArenaListener {
        void onMovePlayed(MoveEvent event);
        void onMatchFinished(MatchFinishedEvent event);
    }

    private ArenaListener listener;

    public EngineArena(UCIEngineFactory factory, MatchConfig config) {
        this.factory = factory;
        this.matchConfig = config;
    }

    public void setArenaListener(ArenaListener listener) {
        this.listener = listener;
    }

    /**
     * Start Engine match
     *
     * @param roundNumber round number (1 ~ inf)
     * @return Match result
     */
    public MatchResult startMatch(int roundNumber) {
        ChessGame chessGame = ChessGame.startPosition();

        UCIEngineWrapper engine1 = factory.spawn(matchConfig.getEngine1Config());
        UCIEngineWrapper engine2 = factory.spawn(matchConfig.getEngine2Config());

        EngineLimit engine1Limit = matchConfig.getEngine1Config().limit();
        EngineLimit engine2Limit = matchConfig.getEngine2Config().limit();

        ChessClock clock = new ChessClock(
                engine1Limit.timeControlMs(),
                engine1Limit.incrementMs(),
                engine2Limit.timeControlMs(),
                engine2Limit.incrementMs()
        );

        while (chessGame.getGameoverReason() == GameOverReason.NOTGAMEOVER) {

        }

        return null;
    }
}
package com.pepero.jcb.api.arena;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.dto.MatchResult;
import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.api.enums.GameResult;
import com.pepero.jcb.api.parse.ConvertStringMoveUtils;
import com.pepero.jcb.api.uci.UCIEngineWrapper;
import com.pepero.jcb.api.book.PolyglotHashUtils;

public class EngineArena {
    private ChessGame chessGame;
    private UCIEngineWrapper engine1;
    private UCIEngineWrapper engine2;
    private MatchConfig matchConfig;

    private boolean isEngine1White = true;

    // round number
    private int roundNumber = 1;

    public interface ArenaListener {
        void onMovePlayed(MoveEvent event);
        void onMatchFinished(MatchFinishedEvent event);
    }

    private ArenaListener listener;

    public EngineArena(UCIEngineWrapper engine1, UCIEngineWrapper engine2, MatchConfig config) {
        this.chessGame = ChessGame.startPosition();
        this.engine1 = engine1;
        this.engine2 = engine2;
        this.matchConfig = config;
    }

    public void setArenaListener(ArenaListener listener) {
        this.listener = listener;
    }

    /**
     * Swap engine white and black
     */
    public void swapEngine() {
        isEngine1White = !isEngine1White;
    }

    /**
     * Start Engine match
     *
     * @return Match result
     */
    public MatchResult startMatch() {
        return startMatch(roundNumber++);
    }

    /**
     * Start Engine match
     *
     * @param roundNumber round number (1 ~ inf)
     * @return Match result
     */
    public MatchResult startMatch(int roundNumber) {
        UCIEngineWrapper whiteEngine = isEngine1White ? engine1 : engine2;
        UCIEngineWrapper blackEngine = isEngine1White ? engine2 : engine1;

        MatchConfig.EngineLimit whiteLimit = isEngine1White ? matchConfig.getEngine1Limit() : matchConfig.getEngine2Limit();
        MatchConfig.EngineLimit blackLimit = isEngine1White ? matchConfig.getEngine2Limit() : matchConfig.getEngine1Limit();

        ChessClock clock = new ChessClock(
                whiteLimit.timeControlMs(), whiteLimit.incrementMs(),
                blackLimit.timeControlMs(), blackLimit.incrementMs()
        );

        while (chessGame.getGameoverReason() == GameOverReason.NOTGAMEOVER) {
            boolean isWhiteTurn = chessGame.getTurn();
            UCIEngineWrapper currentEngine = isWhiteTurn ? whiteEngine : blackEngine;

            long currentWTime = clock.getWhiteTimeMs();
            long currentBTime = clock.getBlackTimeMs();
            int currentDepthLimit = isWhiteTurn ? whiteLimit.depthLimit() : blackLimit.depthLimit();

            String bestMoveLan = null;
            long timeSpent;

            if (matchConfig.hasOpeningBook()) {
                long currentHash = chessGame.getPolyglotHash();

                if (matchConfig.isRandomBookMove()) {
                    bestMoveLan = matchConfig.getOpeningBook().pickRandomMove(currentHash);
                } else {
                    bestMoveLan = matchConfig.getOpeningBook().pickSequentialMove(currentHash, roundNumber);
                }
            }

            if (bestMoveLan != null) {
                timeSpent = 0;
            }
            else {
                long startTime = System.currentTimeMillis();

                bestMoveLan = currentEngine.startAnalysisSync(
                        chessGame,
                        currentDepthLimit,
                        currentWTime,
                        currentBTime,
                        whiteLimit.incrementMs(),
                        blackLimit.incrementMs(),
                        matchConfig.getMultiPv()
                );

                timeSpent = System.currentTimeMillis() - startTime;
            }

            clock.spendTime(isWhiteTurn, timeSpent);

            if (whiteLimit.hasTimeLimit() && clock.isTimeUp(isWhiteTurn)) {
                chessGame.forceEndGameExternal(isWhiteTurn ? GameResult.BLACK_WON : GameResult.WHITE_WON,
                        GameOverReason.TIMEOVER);

                break;
            }

            String san = "";
            if(listener != null) san = chessGame.toSan(bestMoveLan);

            chessGame.makeMove(bestMoveLan);

            if (listener != null) {
                listener.onMovePlayed(new MoveEvent(
                        chessGame.getFEN(),
                        bestMoveLan,
                        san,
                        roundNumber,
                        chessGame.isWhiteTurn(),
                        timeSpent,
                        clock.getWhiteTimeMs(),
                        clock.getBlackTimeMs()
                ));
            }
        }

        MatchResult result = new MatchResult(
                chessGame.getGameResult(),
                resolveEngineWinner(chessGame.getGameResult()),
                chessGame.isGameOver(),
                chessGame.getPGN()
        );

        if (listener != null) {
            listener.onMatchFinished(new MatchFinishedEvent(
                    chessGame.getGameResult(),
                    resolveEngineWinner(chessGame.getGameResult()),
                    chessGame.getGameoverReason(),
                    chessGame.getPGN(),
                    chessGame.getFEN()
            ));
        }

        chessGame = ChessGame.startPosition();

        return result;
    }

    private EngineWinner resolveEngineWinner(GameResult gameResult) {
        if (gameResult == GameResult.DRAW) {
            return EngineWinner.DRAW;
        }
        boolean whiteWon = (gameResult == GameResult.WHITE_WON);
        boolean engine1Won = whiteWon == isEngine1White;
        return engine1Won ? EngineWinner.ENGINE1 : EngineWinner.ENGINE2;
    }
}
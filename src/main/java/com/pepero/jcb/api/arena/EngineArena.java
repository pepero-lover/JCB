package com.pepero.jcb.api.arena;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.dto.MatchResult;
import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.api.enums.GameResult;
import com.pepero.jcb.api.uci.UCIEngineWrapper;
import com.pepero.jcb.api.book.PolyglotHashUtils;

public class EngineArena {
    private ChessGame chessGame;
    private UCIEngineWrapper engine1;
    private UCIEngineWrapper engine2;
    private MatchConfig matchConfig;

    private boolean isEngine1White = true;

    public EngineArena(ChessGame chessGame, UCIEngineWrapper engine1, UCIEngineWrapper engine2, MatchConfig config) {
        this.chessGame = chessGame;
        this.engine1 = engine1;
        this.engine2 = engine2;
        this.matchConfig = config;
    }

    public void swapEngine() {
        isEngine1White = !isEngine1White;
    }

    public MatchResult startMatch() {
        return startMatch(1);
    }

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

            chessGame.makeMove(bestMoveLan);
        }

        return new MatchResult(
                chessGame.getGameResult(),
                chessGame.isGameOver(),
                chessGame.getPGN()
        );
    }
}
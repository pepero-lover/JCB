package com.pepero.jcb.api.arena;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.book.PolyglotBookReader;
import com.pepero.jcb.api.dto.MatchResult;
import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.api.enums.GameResult;
import com.pepero.jcb.api.exception.EngineArenaException;
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

    public EngineArena(MatchConfig config) {
        this.factory = new ProcessUCIEngineFactory();
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

        boolean isEngine1White = roundNumber % 2 == 1;

        try(
                UCIEngineWrapper engine1 = factory.spawn(matchConfig.getEngine1Config());
                UCIEngineWrapper engine2 = factory.spawn(matchConfig.getEngine2Config())) {

            EngineLimit engine1Limit = matchConfig.getEngine1Config().limit();
            EngineLimit engine2Limit = matchConfig.getEngine2Config().limit();

            EngineLimit whiteLimit = (isEngine1White ? engine1Limit : engine2Limit);
            EngineLimit blackLimit = (isEngine1White ? engine2Limit : engine1Limit);

            ChessClock clock = new ChessClock(
                    whiteLimit.timeControlMs(),
                    whiteLimit.incrementMs(),
                    blackLimit.timeControlMs(),
                    blackLimit.incrementMs()
            );

            PolyglotBookReader bookReader = matchConfig.getOpeningBook();

            while (chessGame.getGameoverReason() == GameOverReason.NOTGAMEOVER) {
                if(matchConfig.hasOpeningBook()) {
                    long polyglotHash = chessGame.getPolyglotHash();
                    String move;
                    if(matchConfig.isRandomBookMove()) {
                        move = bookReader.pickRandomMove(polyglotHash);
                    } else {
                        int openingSeed = matchConfig.isRepeatOpening() ? (roundNumber + 1) / 2 : roundNumber;
                        move = bookReader.pickSequentialMove(polyglotHash, openingSeed);
                    }
                    if(move != null) {
                        String san = chessGame.toSan(move);

                        chessGame.makeMove(move);
                        if(listener != null) {
                            listener.onMovePlayed(new MoveEvent(
                                    chessGame.getFEN(),
                                    move,
                                    san,
                                    roundNumber,
                                    chessGame.isWhiteTurn(),
                                    0,
                                    clock.getWhiteTimeMs(),
                                    clock.getBlackTimeMs()
                            ));
                        }
                        continue;
                    }
                }

                boolean whiteTurn = chessGame.isWhiteTurn();
                boolean isEngine1Turn = whiteTurn == isEngine1White;
                UCIEngineWrapper currentEngine = isEngine1Turn ? engine1 : engine2;
                EngineLimit currentLimit = isEngine1Turn ? engine1Limit : engine2Limit;

                long startTime = System.currentTimeMillis();

                String bestMove = currentEngine.startAnalysisSync(chessGame,
                        currentLimit.depthLimit(),
                        clock.getWhiteTimeMs(),
                        clock.getBlackTimeMs(),
                        clock.getWhiteIncMs(),
                        clock.getBlackIncMs(),
                        1
                );

                long stopTime = System.currentTimeMillis();

                long spentTime = stopTime - startTime;
                clock.spendTime(whiteTurn, spentTime);

                if (currentLimit.hasTimeLimit() && clock.isTimeUp(whiteTurn)) {
                    chessGame.forceEndGameExternal(whiteTurn ?
                            GameResult.BLACK_WON : GameResult.WHITE_WON,
                            GameOverReason.TIMEOVER);
                    break;
                }

                if(bestMove != null) {
                    String san = chessGame.toSan(bestMove);

                    chessGame.makeMove(bestMove);
                    if(listener != null) {
                        listener.onMovePlayed(new MoveEvent(
                                chessGame.getFEN(),
                                bestMove,
                                san,
                                roundNumber,
                                chessGame.isWhiteTurn(),
                                spentTime,
                                clock.getWhiteTimeMs(),
                                clock.getBlackTimeMs()
                        ));
                    }
                } else {
                    throw new EngineArenaException("Best move not found!");
                }
            }
        }

        GameResult result = chessGame.getGameResult();
        GameOverReason reason = chessGame.getGameoverReason();
        EngineWinner winner = EngineWinner.UNKNOWN;
        String pgn = chessGame.getPGN();
        if(result == GameResult.WHITE_WON) {
            winner = isEngine1White ? EngineWinner.ENGINE1 : EngineWinner.ENGINE2;
        } else if(result == GameResult.DRAW) {
            winner = EngineWinner.DRAW;
        } else if(result == GameResult.BLACK_WON){
            winner = isEngine1White ? EngineWinner.ENGINE2 : EngineWinner.ENGINE1;
        }

        if(listener != null) {
            listener.onMatchFinished(new MatchFinishedEvent(
                    result,
                    winner,
                    reason,
                    pgn,
                    chessGame.getFEN()
            ));
        }

        return new MatchResult(
                result,
                winner,
                reason,
                pgn
            );
    }
}
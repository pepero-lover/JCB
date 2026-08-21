package com.pepero.jcb.api.arena;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.SyzygyAnalyzer;
import com.pepero.jcb.api.book.PolyglotBookReader;
import com.pepero.jcb.api.dto.MatchResult;
import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.api.enums.GameResult;
import com.pepero.jcb.api.exception.EngineArenaException;
import com.pepero.jcb.api.uci.EngineLine;
import com.pepero.jcb.api.uci.UCIEngineWrapper;
import com.pepero.jcb.core.ChessboardUtils;
import com.pepero.jcb.core.chess960.Chess960Utils;

import java.io.IOException;
import java.util.*;

public class EngineArena {

    private final MatchConfig matchConfig;
    private final UCIEngineFactory factory;

    private int[] chess960Position = new int[960];

    public interface ArenaListener {
        void onMovePlayed(MoveEvent event);
        void onMatchFinished(MatchFinishedEvent event);
    }

    private ArenaListener listener;

    public EngineArena(MatchConfig config) {
        this.factory = new ProcessUCIEngineFactory();
        this.matchConfig = config;

        if(config.isChess960()) {
            int[] positions = new int[960];
            for (int i = 0; i < 960; i++) positions[i] = i;
            Random rnd = new Random(config.getSeed());
            for (int i = 959; i > 0; i--) {
                int j = rnd.nextInt(i + 1);
                int tmp = positions[i];
                positions[i] = positions[j];
                positions[j] = tmp;
            }
            this.chess960Position = positions;
        }
    }

    /**
     * Get random chess 960 position index with roundNum(seed)
     *
     * @param roundNum round number (used as random seed)
     * @return chess 960 position index
     */
    private int getPositionIndex(int roundNum) {
        if(matchConfig.isRepeatOpening()) {
            int pairIndex = (roundNum - 1) / 2;
            return chess960Position[pairIndex % 960];
        } else {
            return chess960Position[(roundNum - 1) % 960];
        }
    }

    public void setArenaListener(ArenaListener listener) {
        this.listener = listener;
    }

    /**
     * Start Engine match
     *
     * @param roundNumber round number (1 ~ inf)
     * @param token cancellation token (for aborting game)
     * @return Match result
     */
    public MatchResult startMatch(int roundNumber, CancellationToken token) {
        boolean isEngine1White = roundNumber % 2 == 1;

        ChessGame chessGame;
        if(matchConfig.isChess960()) {
            if(matchConfig.hasFENSetting()) {
                chessGame = ChessGame.fromFEN(isEngine1White
                        ? matchConfig.fenSettingConfig().fenWhenEngine1White()
                        : matchConfig.fenSettingConfig().fenWhenEngine1Black(),
                        true);
            } else {
                chessGame = ChessGame.fromFEN(
                        Chess960Utils.generate960FenByIndex(getPositionIndex(roundNumber)),
                        true
                );
            }
        } else {
            if(matchConfig.hasFENSetting()) {
                chessGame = ChessGame.fromFEN(isEngine1White
                                ? matchConfig.fenSettingConfig().fenWhenEngine1White()
                                : matchConfig.fenSettingConfig().fenWhenEngine1Black(),
                        matchConfig.getVariants());
            } else {
                chessGame = ChessGame.startPosition(matchConfig.getVariants());
            }
        }

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

            chessGame.setHeader("White", isEngine1White ?
                    matchConfig.getEngine1Config().name() : matchConfig.getEngine2Config().name());
            chessGame.setHeader("Black", isEngine1White ?
                    matchConfig.getEngine2Config().name() : matchConfig.getEngine1Config().name());
            chessGame.setHeader("Round", String.valueOf(roundNumber));
            if(whiteLimit.hasTimeLimit() && blackLimit.hasTimeLimit()) {
                String time = String.valueOf(whiteLimit.timeControlMs() / 1000.);
                String increment = String.valueOf(whiteLimit.incrementMs() / 1000.);

                chessGame.setHeader("TimeControl", time + "+" + increment);
            }

            PolyglotBookReader bookReader = matchConfig.getOpeningBook();

            GameResult winningSide = GameResult.UNKNOWN;

            int drawAdjCount = 0;
            int resignAdjCount = 0;

            GameResult syzygyWinningSide = GameResult.UNKNOWN;
            int syzygyAdjCount = 0;

            while (chessGame.getGameoverReason() == GameOverReason.NOTGAMEOVER) {
                if (token != null && token.isCancelled()) {
                    chessGame.adjudication(GameResult.ABORTED);
                    break;
                }

                boolean whiteTurn = chessGame.isWhiteTurn();
                if(matchConfig.hasOpeningBook()) {
                    long polyglotHash = chessGame.getPolyglotHash();
                    String move;

                    int effectiveRound = matchConfig.isRepeatOpening() ? (roundNumber + 1) / 2 : roundNumber;
                    int openingSeed = Objects.hash(matchConfig.getSeed(), effectiveRound);

                    move = bookReader.pickSequentialMove(polyglotHash, openingSeed);

                    if(move != null) {
                        String san = chessGame.toSan(move);

                        clock.spendTime(whiteTurn, 0);
                        chessGame.makeMove(move);
                        if(matchConfig.isShowClk()) {
                            long time = (whiteTurn ? clock.getWhiteTimeMs() : clock.getBlackTimeMs());
                            chessGame.setCurrentMoveClockMilliSeconds(
                                    time
                            );
                            chessGame.setTimeStamp("0");
                        }
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

                boolean isEngine1Turn = whiteTurn == isEngine1White;
                UCIEngineWrapper currentEngine = isEngine1Turn ? engine1 : engine2;
                EngineLimit currentLimit = isEngine1Turn ? engine1Limit : engine2Limit;

                if (token != null) {
                    token.setActiveEngine(currentEngine);
                }

                long startTime = System.currentTimeMillis();

                String bestMove = currentEngine.startAnalysisSync(chessGame,
                        currentLimit.depthLimit(),
                        clock.getWhiteTimeMs(),
                        clock.getBlackTimeMs(),
                        clock.getWhiteIncMs(),
                        clock.getBlackIncMs(),
                        1,
                        whiteTurn ? clock.getWhiteTimeMs() / 1000 + 1 : clock.getBlackTimeMs() / 1000 + 1
                );

                long stopTime = System.currentTimeMillis();

                if (token != null) {
                    token.setActiveEngine(null);
                }

                if (token != null && token.isCancelled()) {
                    chessGame.adjudication(GameResult.ABORTED);
                    break;
                }

                long spentTime = stopTime - startTime;
                clock.spendTime(whiteTurn, spentTime);

                if (currentLimit.hasTimeLimit() && clock.isTimeUp(whiteTurn)) {
                    chessGame.timeOver(whiteTurn);
                    break;
                }
                int whiteCp = currentEngine.getCurrentCp();
                int moverCp = whiteTurn ? whiteCp : -whiteCp;

                int fullMove = chessGame.getFullMove();

                if(matchConfig.getDrawRule() != null) {
                    AdjudicationRule drawRule = matchConfig.getDrawRule();
                    if(fullMove >= drawRule.minMoveNumber()) {
                        boolean adjust = drawRule.isWithinThreshold(moverCp, false);
                        if(adjust) drawAdjCount++;
                        else drawAdjCount = 0;

                        if(drawAdjCount >= drawRule.moveCount()) {
                            chessGame.adjudication(GameResult.DRAW);
                            break;
                        }
                    }
                }
                if(matchConfig.getResignRule() != null) {
                    AdjudicationRule resignRule = matchConfig.getResignRule();
                    if(fullMove >= resignRule.minMoveNumber()) {
                        boolean isExtreme = Math.abs(whiteCp)
                                >= resignRule.scoreThresholdCP() + resignRule.scoreToleranceCP();
                        GameResult currentWinner = whiteCp > 0 ? GameResult.WHITE_WON : GameResult.BLACK_WON;

                        if (isExtreme && (winningSide == GameResult.UNKNOWN || winningSide == currentWinner)) {
                            resignAdjCount++;
                            winningSide = currentWinner;
                        } else {
                            resignAdjCount = 0;
                            winningSide = GameResult.UNKNOWN;
                        }

                        if (resignAdjCount >= resignRule.moveCount()) {
                            chessGame.adjudication(currentWinner);
                            break;
                        }
                    }
                }
                if (matchConfig.hasSyzygyAdjudication()) {
                    SyzygyRule rule = matchConfig.getSyzygyRule();
                    int pieceCount = chessGame.getPieceCount();
                    if (pieceCount <= rule.maxPieceCount() && !chessGame.hasCastling()) {
                        int probeResult = SyzygyAnalyzer.probeWdl(chessGame, matchConfig.getSyzygyTablebase());

                        if (probeResult == 0) {
                            syzygyAdjCount++;
                            syzygyWinningSide = GameResult.DRAW;

                            if (syzygyAdjCount >= rule.moveCount()) {
                                chessGame.adjudication(GameResult.DRAW);
                                break;
                            }
                        } else if (Math.abs(probeResult) == 2) {
                            boolean sideToMoveWinning = probeResult > 0;
                            GameResult currentSyzygyWinner = whiteTurn == sideToMoveWinning
                                    ? GameResult.WHITE_WON : GameResult.BLACK_WON;

                            if (syzygyWinningSide == GameResult.UNKNOWN || syzygyWinningSide == currentSyzygyWinner) {
                                syzygyAdjCount++;
                            } else {
                                syzygyAdjCount = 1;
                            }
                            syzygyWinningSide = currentSyzygyWinner;

                            if (syzygyAdjCount >= rule.moveCount()) {
                                chessGame.adjudication(currentSyzygyWinner);
                                break;
                            }
                        } else {
                            syzygyAdjCount = 0;
                            syzygyWinningSide = GameResult.UNKNOWN;
                        }
                    }
                }

                if(bestMove != null) {
                    String san = chessGame.makeMoveReturningSan(bestMove);

                    EngineLine currentEngineLine = currentEngine.getCurrentFirstEngineLine();
                    if(currentEngineLine != null) {
                        if(matchConfig.isShowEval()) {
                            chessGame.setCurrentMoveEval(currentEngineLine.score().toString());
                        }
                        if(matchConfig.isShowPv()) {
                            chessGame.setCurrentMoveComment(currentEngineLine.sanPv());
                        }
                        if(matchConfig.isShowClk()) {
                            long time = (whiteTurn ? clock.getWhiteTimeMs() : clock.getBlackTimeMs());
                            chessGame.setCurrentMoveClockMilliSeconds(
                                    time
                            );
                            chessGame.setTimeStamp(String.valueOf(spentTime % 1000 / 100));
                        }
                    }
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
        } catch (IOException e) {
            throw new RuntimeException(e);
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
        } else if(result == GameResult.ABORTED) {
            winner = EngineWinner.ABORTED;
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
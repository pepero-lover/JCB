package com.pepero.jcb.api;

import com.pepero.jcb.api.analyze.ChessTacticUtils;
import com.pepero.jcb.api.analyze.TacticAnalyzer;
import com.pepero.jcb.api.analyze.TacticFinding;
import com.pepero.jcb.api.book.PolyglotHashUtils;
import com.pepero.jcb.api.dto.*;
import com.pepero.jcb.api.enums.*;
import com.pepero.jcb.api.event.ChessGameListener;
import com.pepero.jcb.api.exception.*;
import com.pepero.jcb.api.exception.type.FENErrorType;
import com.pepero.jcb.api.parse.ConvertStringMoveUtils;
import com.pepero.jcb.api.parse.FENValidator;
import com.pepero.jcb.api.parse.pgn.MoveAnnotation;
import com.pepero.jcb.api.parse.pgn.PGNLexer;
import com.pepero.jcb.api.parse.pgn.PGNUtils;
import com.pepero.jcb.api.parse.pgn.TokenType;
import com.pepero.jcb.api.syzygy.SyzygyTablebase;
import com.pepero.jcb.bitboard.BitBoardUtils;
import com.pepero.jcb.constant.BoardSquares;
import com.pepero.jcb.constant.MoveCache;
import com.pepero.jcb.core.*;
import com.pepero.jcb.encode.EncodeMove;
import com.pepero.jcb.api.perft.PerftDriver;
import com.pepero.jcb.api.perft.PerftResult;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.pepero.jcb.constant.EncodedPieces.*;
import static com.pepero.jcb.constant.SideToMove.black;
import static com.pepero.jcb.constant.SideToMove.white;
import static com.pepero.jcb.core.MoveGenerator.ILLEGAL_MOVE;
import static com.pepero.jcb.core.MoveGenerator.generateMoves;

public class ChessGame {
    // start position constant
    public static final String START_POSITION = Chessboard.start_position;



    // Chessboard class
    private final Chessboard chessboard;

    // For variation mode
    private class MoveNode {
        final long id;
        final MoveNode parent;
        final List<MoveNode> children = new ArrayList<>();
        final MoveInfo moveData;
        String san;

        MoveAnnotation annotation = null;

        // for external
        GameResult terminalResult = null;
        GameOverReason terminalReason = null;

        // cache game state
        boolean isStateEvaluated = false;
        GameResult calculatedResult = GameResult.UNKNOWN;
        GameOverReason calculatedReason = GameOverReason.NOTGAMEOVER;

        MoveNode() {
            this.id = nodeCounter.incrementAndGet();
            this.moveData = null;
            this.parent = null;
        }

        MoveNode(MoveInfo moveData, MoveNode parent) {
            this.id = nodeCounter.incrementAndGet();
            this.moveData = moveData;
            this.parent = parent;
        }

        public MoveAnnotation getAnnotation() {
            if (this.annotation == null) {
                this.annotation = new MoveAnnotation();
            }
            return this.annotation;
        }

        @Override
        public String toString() {
            String dataStr = (moveData == null) ? "ROOT" : moveData.toString();
            return dataStr + " -> " + children;
        }
    }

    private static final int[] PIECE_VALUES = new int[12];
    static {
        PIECE_VALUES[P] = 1;  PIECE_VALUES[p] = -1;
        PIECE_VALUES[N] = 3;  PIECE_VALUES[n] = -3;
        PIECE_VALUES[B] = 3;  PIECE_VALUES[b] = -3;
        PIECE_VALUES[R] = 5;  PIECE_VALUES[r] = -5;
        PIECE_VALUES[Q] = 9;  PIECE_VALUES[q] = -9;
        PIECE_VALUES[K] = 100; PIECE_VALUES[k] = -100;
    }


    private static final int MAX_PGN_NODE_COUNT = 2048;
    private final AtomicLong nodeCounter = new AtomicLong(0L);

    private final Map<Long, MoveNode> nodeCache = new LinkedHashMap<>(16, 0.75f, true);

    private boolean autoChangeGameOver = true;

    // multi-thread safe lock
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();

    private MoveNode moveHistoryRoot = new MoveNode();

    private MoveNode currentNode = moveHistoryRoot;


    // game variables

    private int[] initialPieceCounts = new int[12];

    private LinkedHashMap<String, String> headers = new LinkedHashMap<>();

    private GameResult gameResult = GameResult.UNKNOWN;
    private GameOverReason gameoverReason = GameOverReason.NOTGAMEOVER;

    private String startPositionFEN;

    private final CopyOnWriteArrayList<ChessGameListener> listeners = new CopyOnWriteArrayList<>();

    // for pgn parsing pattern
    private static final Pattern CLK_PATTERN = Pattern.compile("\\[%clk\\s+([^\\]]+)\\]");
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("\\[%timestamp\\s+([^\\]]+)\\]");
    private static final Pattern EVAL_PATTERN = Pattern.compile("\\[%eval\\s+([^\\]]+)\\]");
    private static final Pattern CSL_PATTERN = Pattern.compile("\\[%csl\\s+([^\\]]+)\\]");
    private static final Pattern CAL_PATTERN = Pattern.compile("\\[%cal\\s+([^\\]]+)\\]");

    /**
     * Initialize position with FEN string
     *
     * @param pgn pgn string
     */
    public static ChessGame fromPGN(String pgn) {
        ChessGame result = new ChessGame(false, GameVariants.STANDARD);
        result.loadPGN(pgn);
        return result;
    }

    /**
     * Initialize position with FEN string
     *
     * @param fen fen string
     * @param gameVariants game variants ( standard, crazyhouse ... )
     *
     * @throws FENConvertException - if converting fen string failed
     */
    public static ChessGame fromFEN(String fen, GameVariants gameVariants) {
        return new ChessGame(fen, false, gameVariants);
    }

    /**
     * Initialize position with FEN string
     *
     * @param fen fen string
     * @param isChess960 is Chess 960 variant
     * @param gameVariants game variants ( standard, crazyhouse ... )
     *
     * @throws FENConvertException - if converting fen string failed
     */
    public static ChessGame fromFEN(String fen, boolean isChess960, GameVariants gameVariants) {
        return new ChessGame(fen, isChess960, gameVariants);
    }

    /**
     * Initialize position with FEN string
     *
     * @param fen fen string
     * @param isChess960 is Chess 960 variant
     *
     * @throws FENConvertException - if converting fen string failed
     */
    public static ChessGame fromFEN(String fen, boolean isChess960) {
        return new ChessGame(fen, isChess960, GameVariants.STANDARD);
    }

    /**
     * Initialize position with FEN string
     *
     * @param fen fen string
     *
     * @throws FENConvertException - if converting fen string failed
     */
    public static ChessGame fromFEN(String fen) {
        return fromFEN(fen, GameVariants.STANDARD);
    }

    /**
     * Initialize position to start position
     */
    public static ChessGame startPosition() {
        return new ChessGame(false, GameVariants.STANDARD);
    }

    /**
     * Initialize position to start position
     *
     * @param isChess960 is Chess 960 variant
     */
    public static ChessGame startPosition(boolean isChess960) {
        return new ChessGame(isChess960, GameVariants.STANDARD);
    }

    /**
     * Initialize position to start position
     *
     * @param gameVariants game variants ( standard, crazyhouse ... )
     */
    public static ChessGame startPosition(GameVariants gameVariants) {
        return new ChessGame(false, gameVariants);
    }

    /**
     * Initialize position to start position
     *
     * @param isChess960 is Chess 960 variant
     * @param gameVariants game variants ( standard, crazyhouse ... )
     */
    public static ChessGame startPosition(boolean isChess960, GameVariants gameVariants) {
        return new ChessGame(isChess960, gameVariants);
    }

    /**
     * Lightweight copy constructor
     * Warning : This doesn't copy event listener and history tree but the position of this ChessGame
     *
     * @param other ChessGame class to copy
     */
    public static ChessGame lightWeightCopy(ChessGame other) {
        return new ChessGame(other);
    }

    /**
     * Get default start position
     *
     * @param gameVariants game variant
     * @return default start position
     */
    private static String getDefaultStartPosition(GameVariants gameVariants) {
        return switch (gameVariants) {
            case HORDE -> Chessboard.horde_start_position;
            case RACING_KINGS -> Chessboard.racing_kings_start_position;
            case ANTICHESS -> Chessboard.antichess_start_position;
            default -> Chessboard.start_position;
        };
    }

    /**
     * Initialize position with FEN string
     *
     * @param fen fen string
     * @param isChess960 is Chess 960 variant
     * @param gameVariants game variants ( standard, crazyhouse ... )
     *
     * @throws FENConvertException - if converting fen string failed
     */
    private ChessGame(String fen, boolean isChess960, GameVariants gameVariants) {
        FENValidator.validateString(fen, isChess960, gameVariants);

        chessboard = new Chessboard();
        startPositionFEN = fen;

        chessboard.isChess960 = isChess960;
        chessboard.gameVariants = gameVariants;

        try {
            ChessboardUtils.parseFen(this.chessboard, fen);
        } catch (Exception e) {
            throw new FENConvertException("Could not parse the fen.", FENErrorType.UNKNOWN);
        }

        FENValidator.validateLogicalState(chessboard, gameVariants);

        nodeCache.put(moveHistoryRoot.id, moveHistoryRoot);

        calculateInitialPieces(fen);
    }

    /**
     * Initialize position to start position
     *
     * @param isChess960 is Chess 960 variant
     * @param gameVariants game variants ( standard, crazyhouse ... )
     */
    private ChessGame(boolean isChess960, GameVariants gameVariants) {
        this.chessboard = new Chessboard();
        this.chessboard.isChess960 = isChess960;
        this.chessboard.gameVariants = gameVariants;

        String startFen = getDefaultStartPosition(gameVariants);
        ChessboardUtils.parseFen(this.chessboard, startFen);

        startPositionFEN = startFen;

        nodeCache.put(moveHistoryRoot.id, moveHistoryRoot);

        calculateInitialPieces(this.getFEN());
    }

    /**
     * Lightweight copy constructor
     * Warning : This doesn't copy event listener and history tree but the position of this ChessGame
     *
     * @param other ChessGame class to copy
     */
    private ChessGame(ChessGame other) {
        other.readLock.lock();
        writeLock.lock();
        try {
            this.chessboard = new Chessboard(other.chessboard);
            this.startPositionFEN = other.startPositionFEN;
            this.autoChangeGameOver = other.autoChangeGameOver;

            System.arraycopy(other.initialPieceCounts, 0, this.initialPieceCounts, 0, 12);

            this.moveHistoryRoot = new MoveNode();
            this.currentNode = this.moveHistoryRoot;
            this.nodeCache.put(this.moveHistoryRoot.id, this.moveHistoryRoot);

            setDefaultHeaders();
        } finally {
            other.readLock.unlock();
            writeLock.unlock();
        }
    }

    /**
     * Get this position's turn
     *
     * @return true if it's white turn, otherwise, false
     */
    public boolean isWhiteTurn() {
        readLock.lock();
        try {
            return chessboard.side == white;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get FEN on this ChessGame
     * @return fen (lichess dialect for 3-check variant)
     */
    public String getFEN() {
        readLock.lock();
        try {
            return ChessboardUtils.getFen(this.chessboard);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get FEN on this ChessGame
     *
     * @param dialect FEN dialect (affects 3-check variant output format)
     * @return fen
     */
    public String getFEN(FENDialect dialect) {
        readLock.lock();
        try {
            return ChessboardUtils.getFen(this.chessboard, dialect);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Make move for internal make move methods
     *
     * @param encodedMove encoded move data
     * @param originalMoveString original move string (null allowed)
     */
    private void internalMakeMove(int encodedMove, String originalMoveString) {
        MoveInfo moveData;
        GameResult gameResult = GameResult.UNKNOWN;
        boolean historyChanged;

        writeLock.lock();
        try {
            if(!ChessboardUtils.isLegalMove(this.chessboard, encodedMove)) {
                String errorStr = (originalMoveString != null) ? originalMoveString : new MoveInfo(encodedMove).toLanString();
                throw new IllegalMoveException(errorStr, this.getFEN());
            }

            if(chessboard.gameVariants == GameVariants.STANDARD) {
                MoveGenerator.makeStandardMove(this.chessboard, encodedMove);
            } else {
                MoveGenerator.makeMove(this.chessboard, encodedMove);
            }

            moveData = new MoveInfo(encodedMove);
            historyChanged = addMoveHistory(moveData);

            if(autoChangeGameOver) {
                gameResult = evaluateGameState(currentNode);
            }
        } finally {
            writeLock.unlock();
        }

        notifyMoveMade(moveData);
        if (gameResult != GameResult.UNKNOWN) {
            notifyGameOver(this.gameResult, this.gameoverReason);
        }
        if(historyChanged) {
            notifyHistoryChanged();
        }
    }

    /**
     * Make move for internal make move methods (not locking multi-thread)
     *
     * @param encodedMove encoded move data
     * @param originalMoveString original move string (null allowed)
     */
    private void internalMakeMoveLocked(int encodedMove, String originalMoveString) {
        MoveInfo moveData;
        GameResult gameResult = GameResult.UNKNOWN;
        boolean historyChanged;

        if(!ChessboardUtils.isLegalMove(this.chessboard, encodedMove)) {
            String errorStr = (originalMoveString != null) ? originalMoveString : new MoveInfo(encodedMove).toLanString();
            throw new IllegalMoveException(errorStr, this.getFEN());
        }

        if(chessboard.gameVariants == GameVariants.STANDARD) {
            MoveGenerator.makeStandardMove(this.chessboard, encodedMove);
        } else {
            MoveGenerator.makeMove(this.chessboard, encodedMove);
        }

        moveData = new MoveInfo(encodedMove);
        historyChanged = addMoveHistory(moveData);

        if(autoChangeGameOver) {
            gameResult = evaluateGameState(currentNode);
        }

        notifyMoveMade(moveData);
        if (gameResult != GameResult.UNKNOWN) {
            notifyGameOver(this.gameResult, this.gameoverReason);
        }
        if(historyChanged) {
            notifyHistoryChanged();
        }
    }

    /**
     * Make move on this ChessGame (LAN MOVE)
     *
     * @param moveString move like e2e4, e7e5 (LAN move string)
     *
     * @throws IllegalMoveException - if move is illegal move
     * @throws ConvertMoveException - if move data is not correct
     */
    public void makeMove(String moveString) {
        writeLock.lock();
        try {
            int encodedMove = ConvertStringMoveUtils.parseLanToEncodedMove(this.chessboard, moveString);
            internalMakeMoveLocked(encodedMove, moveString);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Make a move on this ChessGame (San string)
     *
     * @param sanString san string
     *
     * @throws IllegalMoveException - if move is illegal move
     * @throws ConvertMoveException - if move data is not correct
     */
    public void makeMoveSan(String sanString) {
        makeMove(toLanString(sanString));
    }

    /**
     * Make a move on this ChessGame (ENCODED MOVE)
     *
     * @param encodedMove encoded move
     *
     * @throws IllegalMoveException - if move is illegal move
     * @throws ConvertMoveException - if move data is not correct
     */
    public void makeMove(int encodedMove) {
        internalMakeMove(encodedMove, null);
    }

    /**
     * Make moves on this ChessGame (San string)
     *
     * @param sanString san string like "e4 e5 Nf3 Nc6"
     *
     * @throws IllegalMoveException - if move is illegal move
     * @throws ConvertMoveException - if move data is not correct
     */
    public void makeMoveSanAll(String sanString) {
        writeLock.lock();
        try {
            sanString = sanString.trim();
            String[] sanStrings = sanString.split(" ");

            Chessboard tempChessboard = new Chessboard(this.chessboard);

            for (String san : sanStrings) {
                int encodedMove = ConvertStringMoveUtils.sanToMoveData(tempChessboard, san);
                if(!MoveGenerator.isLegalMove(tempChessboard, encodedMove)) {
                    throw new IllegalMoveException(san,
                            ChessboardUtils.getFen(tempChessboard));
                }
                MoveGenerator.makeMove(tempChessboard, encodedMove);
            }

            for (String san : sanStrings) {
                makeMoveSan(san);
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Make moves on this ChessGame
     *
     * @param lanString san string like "e2e4 e7e5 g1f3 b8c6"
     *
     * @throws IllegalMoveException - if move is illegal move
     * @throws ConvertMoveException - if move data is not correct
     */
    public void makeMoveAll(String lanString) {
        writeLock.lock();
        try {
            lanString = lanString.trim();
            String[] lanStrings = lanString.split(" ");

            Chessboard tempChessboard = new Chessboard(this.chessboard);

            for (String lan : lanStrings) {
                int encodedMove = ConvertStringMoveUtils.parseLanToEncodedMove(tempChessboard, lan);
                if(!MoveGenerator.isLegalMove(tempChessboard, encodedMove)) {
                    throw new IllegalMoveException(lan,
                            ChessboardUtils.getFen(tempChessboard));
                }
                MoveGenerator.makeMove(tempChessboard, encodedMove);
            }

            for (String lan : lanStrings) {
                makeMove(lan);
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Make move for internal make move raw methods
     *
     * @param encodedMove encoded move data
     */
    private void internalMakeMoveRaw(int encodedMove) {
        writeLock.lock();
        try {
            if(!ChessboardUtils.isLegalMove(this.chessboard, encodedMove)) {
                throw new IllegalMoveException(EncodeMove.moveToString(encodedMove),
                        this.getFEN());
            }

            if(chessboard.gameVariants == GameVariants.STANDARD) {
                MoveGenerator.makeStandardMove(this.chessboard, encodedMove);
            } else {
                MoveGenerator.makeMove(this.chessboard, encodedMove);
            }
        } finally {
            writeLock.unlock();
        }
    }


    /**
     * Make move for internal make move raw methods (not locking multi-thread)
     *
     * @param encodedMove encoded move data
     */
    private void internalMakeMoveRawLocked(int encodedMove) {
        if(!ChessboardUtils.isLegalMove(this.chessboard, encodedMove)) {
            throw new IllegalMoveException(EncodeMove.moveToString(encodedMove),
                    this.getFEN());
        }

        if(chessboard.gameVariants == GameVariants.STANDARD) {
            MoveGenerator.makeStandardMove(this.chessboard, encodedMove);
        } else {
            MoveGenerator.makeMove(this.chessboard, encodedMove);
        }
    }

    /**
     * Try to make move on this ChessGame without throwing an exception
     *
     * @param moveInfo move info
     *
     * @return true if the move was legal and applied, false otherwise
     */
    public boolean tryMakeMoveRaw(MoveInfo moveInfo) {
        try {
            makeMoveRaw(moveInfo);
            return true;
        } catch (IllegalMoveException | ConvertMoveException e) {
            return false;
        }
    }

    /**
     * Try to make move on this ChessGame without throwing an exception
     *
     * @param encoded_move encoded move
     *
     * @return true if the move was legal and applied, false otherwise
     */
    public boolean tryMakeMoveRaw(int encoded_move) {
        try {
            makeMoveRaw(encoded_move);
            return true;
        } catch (IllegalMoveException | ConvertMoveException e) {
            return false;
        }
    }

    /**
     * Try to make move on this ChessGame without throwing an exception
     *
     * @param lanMove move string like "e2e4", "e7e5"
     *
     * @return true if the move was legal and applied, false otherwise
     */
    public boolean tryMakeMoveRaw(String lanMove) {
        try {
            makeMoveRaw(lanMove);
            return true;
        } catch (IllegalMoveException | ConvertMoveException e) {
            return false;
        }
    }

    /**
     * Make move on this ChessGame <br>
     * <b>Warning : This raw method doesn't update history, call listener, and update game over variable. </b>
     *
     * @param moveInfo move info
     */
    public void makeMoveRaw(MoveInfo moveInfo) {
        internalMakeMoveRaw(moveInfo.originEncodedData());
    }

    /**
     * Make move on this ChessGame <br>
     * <b>Warning : This raw method doesn't update history, call listener, and update game over variable. </b>
     *
     * @param moveString lan move string
     */
    public void makeMoveRaw(String moveString) {
        int encodedMove = ConvertStringMoveUtils.parseLanToEncodedMove(this.chessboard, moveString);
        internalMakeMoveRaw(encodedMove);
    }

    /**
     * Make move on this ChessGame <br>
     * <b>Warning : This raw method doesn't update history, call listener, and update game over variable. </b>
     *
     * @param encodedMove encoded move
     */
    public void makeMoveRaw(int encodedMove) {
        internalMakeMoveRaw(encodedMove);
    }

    /**
     * Unmake move on this ChessGame <br>
     * <b>Warning : This method doesn't update history, call listener, and update game over variable. </b>
     *
     * @param moveInfo move info
     */
    public void unmakeMoveRaw(MoveInfo moveInfo) {
        unmakeMoveRaw(moveInfo.originEncodedData());
    }

    /**
     * Unmake move on this ChessGame <br>
     * <b>Warning : This method doesn't update history, call listener, and update game over variable. </b>
     *
     * @param encodedMove encoded move
     */
    public void unmakeMoveRaw(int encodedMove) {
        writeLock.lock();
        try {
            MoveGenerator.unmakeMove(this.chessboard, encodedMove);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Make move on this ChessGame (Source square, Target square, Promotion Type)
     *
     * @param sourceSquare Source square (you can make square on BoardSquares.java)
     * @param targetSquare Target square (you can make square on BoardSquares.java)
     * @param promotionType Promotion type like queen, rook, bishop and knight (PieceType.QUEEN, PieceType.ROOK ... )
     *
     * @throws IllegalMoveException - if move is illegal move
     * @throws ConvertMoveException - if move data is not correct
     */
    public void makeMove(Square sourceSquare, Square targetSquare, PieceType promotionType) {
        Objects.requireNonNull(sourceSquare, "The source square can not be null!");
        Objects.requireNonNull(targetSquare, "The target square can not be null!");
        Objects.requireNonNull(promotionType, "The promotion type can not be null!");

        if(promotionType != PieceType.NONE && promotionType != PieceType.QUEEN && promotionType != PieceType.ROOK &&
                promotionType != PieceType.BISHOP && promotionType != PieceType.KNIGHT) {
            throw new IllegalMoveException("Promotion Piece type is unknown! please use like PieceType.QUEEN, PieceType.ROOK", this.getFEN());
        }

        int encodedMove;
        String errorString = BoardSquares.square_to_coordinates[sourceSquare.getIndex()]
                + BoardSquares.square_to_coordinates[targetSquare.getIndex()]
                + ((promotionType != PieceType.NONE) ? String.valueOf(ChessboardUtils.promotion_pieces[promotionType.getPieceType()]) : "");

        readLock.lock();
        try {
            int isLegal = MoveGenerator.isLegalMove(this.chessboard, sourceSquare.getIndex(), targetSquare.getIndex(),
                    promotionType.getPieceType());

            if(isLegal == ILLEGAL_MOVE){
                throw new IllegalMoveException(errorString, ChessboardUtils.getFen(chessboard));
            }

            encodedMove = ConvertStringMoveUtils.parseMoveDataToEncodedMove(
                    this.chessboard, sourceSquare.getIndex(), targetSquare.getIndex(), promotionType.getPieceType()
            );
        } finally {
            readLock.unlock();
        }

        internalMakeMove(encodedMove, errorString);
    }

    /**
     * Make move on this ChessGame (Source square, Target square)
     *
     * @param sourceSquare Source square (you can make square on BoardSquares.java)
     * @param targetSquare Target square (you can make square on BoardSquares.java)
     *
     * @throws IllegalMoveException - if move is illegal move
     * @throws ConvertMoveException - if move data is not correct
     */
    public void makeMove(Square sourceSquare, Square targetSquare) {
        makeMove(sourceSquare, targetSquare, PieceType.NONE);
    }

    /**
     * Make move on this ChessGame (MoveInfo)
     *
     * @param moveInfo MoveInfo class
     *
     * @throws IllegalMoveException - if move is illegal move
     */
    public void makeMove(MoveInfo moveInfo) {
        internalMakeMove(moveInfo.originEncodedData(), moveInfo.toLanString());
    }

    /**
     * Try to make move on this ChessGame without throwing an exception (LAN MOVE)
     *
     * @param moveString move like e2e4, e7e5 (LAN move string)
     *
     * @return true if the move was legal and applied, false otherwise
     */
    public boolean tryMakeMove(String moveString) {
        try {
            makeMove(moveString);
            return true;
        } catch (IllegalMoveException | ConvertMoveException e) {
            return false;
        }
    }

    /**
     * Try to make a move on this ChessGame without throwing an exception (San string)
     *
     * @param sanString san string
     *
     * @return true if the move was legal and applied, false otherwise
     */
    public boolean tryMakeMoveSan(String sanString) {
        try {
            makeMoveSan(sanString);
            return true;
        } catch (IllegalMoveException | ConvertMoveException e) {
            return false;
        }
    }

    /**
     * Try to make a move on this ChessGame without throwing an exception (ENCODED MOVE)
     *
     * @param encodedMove encoded move
     *
     * @return true if the move was legal and applied, false otherwise
     */
    public boolean tryMakeMove(int encodedMove) {
        try {
            makeMove(encodedMove);
            return true;
        } catch (IllegalMoveException | ConvertMoveException e) {
            return false;
        }
    }

    /**
     * Try to make moves on this ChessGame without throwing an exception (San string)
     * <p>
     * If a move in the middle of the string is illegal, the moves made before it stay applied.
     *
     * @param sanString san string like "e4 e5 Nf3 Nc6"
     *
     * @return true if all moves were legal and applied, false if it stopped partway through
     */
    public boolean tryMakeMoveSanAll(String sanString) {
        try {
            makeMoveSanAll(sanString);
            return true;
        } catch (IllegalMoveException | ConvertMoveException e) {
            return false;
        }
    }

    /**
     * Try to make moves on this ChessGame without throwing an exception (Lan string)
     * <p>
     * If a move in the middle of the string is illegal, the moves made before it stay applied.
     *
     * @param lanString lan string like "e2e4 e7e5 g1f3 b8c6"
     *
     * @return true if all moves were legal and applied, false if it stopped partway through
     */
    public boolean tryMakeMoveAll(String lanString) {
        try {
            makeMoveAll(lanString);
            return true;
        } catch (IllegalMoveException | ConvertMoveException e) {
            return false;
        }
    }

    /**
     * Try to make move on this ChessGame without throwing an exception (Source square, Target square, Promotion Type)
     *
     * @param sourceSquare Source square (you can make square on BoardSquares.java)
     * @param targetSquare Target square (you can make square on BoardSquares.java)
     * @param promotionType Promotion type like queen, rook, bishop and knight (PieceType.QUEEN, PieceType.ROOK ... )
     *
     * @return true if the move was legal and applied, false otherwise
     */
    public boolean tryMakeMove(Square sourceSquare, Square targetSquare, PieceType promotionType) {
        try {
            makeMove(sourceSquare, targetSquare, promotionType);
            return true;
        } catch (IllegalMoveException | ConvertMoveException e) {
            return false;
        }
    }

    /**
     * Try to make move on this ChessGame without throwing an exception (Source square, Target square)
     *
     * @param sourceSquare Source square (you can make square on BoardSquares.java)
     * @param targetSquare Target square (you can make square on BoardSquares.java)
     *
     * @return true if the move was legal and applied, false otherwise
     */
    public boolean tryMakeMove(Square sourceSquare, Square targetSquare) {
        return tryMakeMove(sourceSquare, targetSquare, PieceType.NONE);
    }

    /**
     * Try to make move on this ChessGame without throwing an exception (MoveInfo)
     *
     * @param moveInfo MoveInfo class
     *
     * @return true if the move was legal and applied, false otherwise
     */
    public boolean tryMakeMove(MoveInfo moveInfo) {
        try {
            makeMove(moveInfo);
            return true;
        } catch (IllegalMoveException | ConvertMoveException e) {
            return false;
        }
    }

    /**
     * Unmake previous move on this ChessGame
     *
     * @return unmade move info
     *
     * @throws EmptyMoveUndoException - if move history is empty and unmake move
     * @throws MoveNotFoundException - if the current node is not found (Only for variation mode)
     */
    public MoveInfo unmakeMove() {
        MoveInfo moveInfo;
        GameResult gameResult = GameResult.UNKNOWN;

        writeLock.lock();
        try {
            if (!canUndo()) throw new EmptyMoveUndoException();

            moveInfo = currentNode.moveData;
            currentNode = currentNode.parent;

            MoveGenerator.unmakeMove(chessboard, moveInfo.originEncodedData());

            if(autoChangeGameOver) {
                gameResult = evaluateGameState(currentNode);
            }
        } finally {
            writeLock.unlock();
        }

        notifyMoveUnmade(moveInfo);
        if (gameResult != GameResult.UNKNOWN) {
            notifyGameOver(this.gameResult, this.gameoverReason);
        }

        return moveInfo;
    }

    /**
     * Remake (redo) move on this ChessGame
     *
     * @return remade move info
     * <p>
     * Example : <br> e2e4 e7e5 (d7d5) g1f3 and pointer is e2e4
     * and remakeMove(), and pointer is now e7e5. <br>
     *
     * @throws EmptyMoveRedoException - if redo history is empty and remake move
     */
    public MoveInfo remakeMove() {
        return remakeMove(0);
    }

    /**
     * Remake (redo) move on this ChessGame (with Variation index) <br>
     *
     * @param variationIndex variation index (if 0, goes main line)
     *
     * @return remade move info
     * <p>
     * Example : <br> e2e4 e7e5 (d7d5) g1f3 and pointer is e2e4
     * and remakeMove(1), and pointer is now d7d5. <br>
     * if remakeMove(0), pointer is now e7e5. <br>
     *
     * @throws EmptyMoveRedoException - if redo history is empty and remake move
     */
    public MoveInfo remakeMove(int variationIndex) {
        MoveInfo moveInfo;
        GameResult gameResult = GameResult.UNKNOWN;

        writeLock.lock();
        try {
            if (!canRedo()) throw new EmptyMoveRedoException();
            if(currentNode.children.size() <= variationIndex) throw new VariationNotFoundException();

            currentNode = currentNode.children.get(variationIndex);
            moveInfo = currentNode.moveData;

            if(chessboard.gameVariants == GameVariants.STANDARD) {
                MoveGenerator.makeStandardMove(this.chessboard, moveInfo.originEncodedData());
            } else {
                MoveGenerator.makeMove(this.chessboard, moveInfo.originEncodedData());
            }

            if(autoChangeGameOver) {
                gameResult = evaluateGameState(currentNode);
            }
        } finally {
            writeLock.unlock();
        }

        notifyMoveRemade(moveInfo);
        if (gameResult != GameResult.UNKNOWN) {
            notifyGameOver(this.gameResult, this.gameoverReason);
        }

        return moveInfo;
    }

    /**
     * Get whether this position can undo
     *
     * @return whether this position can undo
     */
    public boolean canUndo() {
        readLock.lock();
        try {
            return currentNode != moveHistoryRoot;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get whether this position can redo
     *
     * @return whether this position can redo
     *
     * @throws MoveNotFoundException if the current node is not found
     */
    public boolean canRedo() {
        readLock.lock();
        try {
            return !currentNode.children.isEmpty();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Remake (redo) move on this ChessGame (with Variation index) <br>
     *
     * @param variationIndex variation index (if 0, goes main line)
     *
     * @return whether this position can redo
     *
     * @throws MoveNotFoundException - if current node (move) not found
     */
    public boolean canRedo(int variationIndex) {
        readLock.lock();
        try {
            if (currentNode == null) throw new MoveNotFoundException();
            return currentNode.children.size() > variationIndex;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Go forward on history (mainline)
     *
     * @return forwarded move info (if forwarding move failed, returns null)
     */
    public MoveInfo goForward() {
        if (canRedo()) return remakeMove();
        return null;
    }

    /**
     * Go backward on history (mainline)
     *
     * @return undid move info (if undoing move failed, returns null)
     */
    public MoveInfo goBackward() {
        if (canUndo()) return unmakeMove();
        return null;
    }

    /**
     * Get previous moves
     * <p>
     * Example : <br>
     * <b>e2e4 e7e5 g1f3 ( b1c3 <- pointer) b8c6 ) g8f6 </b>
     * and the result is <b>e2e4 e7e5 b1c3</b>
     *
     * @return previous moves
     */
    public List<MoveInfo> getMoveHistory() {
        readLock.lock();
        try {
            List<MoveInfo> result = new ArrayList<>();
            MoveNode current = currentNode;
            while (current != null && current.moveData != null) {
                result.add(current.moveData);
                current = current.parent;
            }
            Collections.reverse(result);
            return Collections.unmodifiableList(result);
        } finally {
            readLock.unlock();
        }
    }


    /**
     * Get last move to string squares
     * <p>
     * Examples : <br>
     * the last move is e2e4 and the return is String[]{"e2","e4"}
     *
     * @return Get last move (string[])
     */
    public String[] getLastMoveSquares() {
        readLock.lock();
        try {
            if(!canUndo()) return new String[0];

            try {
                MoveInfo lastMove = getLastMove();

                String source = lastMove.sourceSquare().toString().toLowerCase();
                String target = lastMove.targetSquare().toString().toLowerCase();

                return new String[] { source, target };

            } catch (MoveNotFoundException e) {
                return new String[0];
            }
        } finally {
            readLock.unlock();
        }
    }

    public MoveType getLastMoveType() {
        readLock.lock();

        try {
            MoveInfo lastMove = getLastMove();

            if(lastMove.capture()) return MoveType.CAPTURE;
            if(lastMove.castling()) return MoveType.CASTLING;
            if(lastMove.promotionPiece() != PieceType.NONE) return MoveType.PROMOTION;
            if(lastMove.enpassant()) return MoveType.ENPASSANT;
            return MoveType.NORMAL;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get the last (previous) move
     *
     * @return the last (previous) move
     *
     * @throws MoveNotFoundException if the current node is not found (Only for variation mode)
     */
    public MoveInfo getLastMove() {
        readLock.lock();
        try {
            if(currentNode == null) throw new MoveNotFoundException();

            return currentNode.moveData;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get white turn
     *
     * @return white turn
     */
    public boolean getTurn() {
        readLock.lock();
        try {
            return this.chessboard.side == white;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get whether this move legal move (not crazy house. for moving piece)
     *
     * @param source source square
     * @param target target square
     * @return whether this move legal move
     */
    public boolean canDropPiece(Square source, Square target) {
        readLock.lock();
        try {
            int sourceIndex = source.getIndex();
            int targetIndex = target.getIndex();

            int[] move_list = MoveCache.CHESSGAME_MOVE_CACHE.get();
            int move_count = MoveGenerator.generateMoves(chessboard, move_list);

            for(int i = 0; i < move_count; i++) {
                int move = move_list[i];
                if(EncodeMove.getMoveSource(move) == sourceIndex
                        && EncodeMove.getMoveTarget(move) == targetIndex) {
                    return true;
                }
            }

            return false;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Make drop move (crazy house)
     *
     * @param pieceType dropping piece type
     * @param targetSquare target square
     */
    public void makeDropMove(PieceType pieceType, Square targetSquare) {
        Objects.requireNonNull(pieceType, "Piece type cannot be null!");
        Objects.requireNonNull(targetSquare, "Target square cannot be null!");

        writeLock.lock();
        try {
            int encodedMove = MoveGenerator.isLegalDrop(this.chessboard, targetSquare.getIndex(), pieceType.getPieceType());

            if (encodedMove == ILLEGAL_MOVE) {
                throw new IllegalMoveException(EncodeMove.moveToString(encodedMove), getFEN());
            }

            makeMove(new MoveInfo(encodedMove));
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Get whether this move is a promotion move
     *
     * @param source source square
     * @param target target square
     * @return whether this move is a promotion move
     */
    public boolean shouldPromotion(Square source, Square target) {
        readLock.lock();
        try {
            int sourceIndex = source.getIndex();
            int targetIndex = target.getIndex();

            if (BitBoardUtils.getBit(chessboard.bitboards[p], sourceIndex) &&
                    targetIndex >= 0 && targetIndex <= 7) {
                return true;
            }

            if (BitBoardUtils.getBit(chessboard.bitboards[P], sourceIndex) &&
                    targetIndex >= 56 && targetIndex <= 63) {
                return true;
            }

            return false;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Init initial piece count
     *
     * @param fen this chess game fen
     */
    private void calculateInitialPieces(String fen) {
        int spaceIndex = fen.indexOf(' ');
        int limit = (spaceIndex != -1) ? spaceIndex : fen.length();

        for (int i = 0; i < limit; i++) {
            char c = fen.charAt(i);
            switch (c) {
                case 'P' -> initialPieceCounts[P]++;
                case 'N' -> initialPieceCounts[N]++;
                case 'B' -> initialPieceCounts[B]++;
                case 'R' -> initialPieceCounts[R]++;
                case 'Q' -> initialPieceCounts[Q]++;
                case 'K' -> initialPieceCounts[K]++;

                case 'p' -> initialPieceCounts[p]++;
                case 'n' -> initialPieceCounts[n]++;
                case 'b' -> initialPieceCounts[b]++;
                case 'r' -> initialPieceCounts[r]++;
                case 'q' -> initialPieceCounts[q]++;
                case 'k' -> initialPieceCounts[k]++;
            }
        }
    }

    /**
     * Get captured piece <br>
     * You can also get pocket data on CrazyHouse variant
     *
     * @param isWhite if white, returns black captured piece. if black, returns white captured piece.
     * @return captured piece
     */
    public Map<PieceType, Integer> getCapturedPieces(boolean isWhite) {
        readLock.lock();
        try {
            Map<PieceType, Integer> captured = new EnumMap<>(PieceType.class);
            if(getGameVariants() == GameVariants.CRAZY_HOUSE) {
                if (isWhite) {
                    captured.put(PieceType.PAWN, chessboard.pocket[p]);
                    captured.put(PieceType.KNIGHT, chessboard.pocket[n]);
                    captured.put(PieceType.BISHOP, chessboard.pocket[b]);
                    captured.put(PieceType.ROOK, chessboard.pocket[r]);
                    captured.put(PieceType.QUEEN, chessboard.pocket[q]);
                } else {
                    captured.put(PieceType.PAWN, chessboard.pocket[P]);
                    captured.put(PieceType.KNIGHT, chessboard.pocket[N]);
                    captured.put(PieceType.BISHOP, chessboard.pocket[B]);
                    captured.put(PieceType.ROOK, chessboard.pocket[R]);
                    captured.put(PieceType.QUEEN, chessboard.pocket[Q]);
                }
            } else {
                if (isWhite) {
                    captured.put(PieceType.PAWN,
                            initialPieceCounts[p] - BitBoardUtils.countBits(chessboard.bitboards[p]));
                    captured.put(PieceType.KNIGHT,
                            initialPieceCounts[n] - BitBoardUtils.countBits(chessboard.bitboards[n]));
                    captured.put(PieceType.BISHOP,
                            initialPieceCounts[b] - BitBoardUtils.countBits(chessboard.bitboards[b]));
                    captured.put(PieceType.ROOK,
                            initialPieceCounts[r] - BitBoardUtils.countBits(chessboard.bitboards[r]));
                    captured.put(PieceType.QUEEN,
                            initialPieceCounts[q] - BitBoardUtils.countBits(chessboard.bitboards[q]));
                } else {
                    captured.put(PieceType.PAWN,
                            initialPieceCounts[P] - BitBoardUtils.countBits(chessboard.bitboards[P]));
                    captured.put(PieceType.KNIGHT,
                            initialPieceCounts[N] - BitBoardUtils.countBits(chessboard.bitboards[N]));
                    captured.put(PieceType.BISHOP,
                            initialPieceCounts[B] - BitBoardUtils.countBits(chessboard.bitboards[B]));
                    captured.put(PieceType.ROOK,
                            initialPieceCounts[R] - BitBoardUtils.countBits(chessboard.bitboards[R]));
                    captured.put(PieceType.QUEEN,
                            initialPieceCounts[Q] - BitBoardUtils.countBits(chessboard.bitboards[Q]));
                }
            }
            captured.values().removeIf(count -> count <= 0);

            return captured;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get piece score (For GUI showing / material comparison)
     *
     * @return piece score
     */
    public int getPieceScore() {
        readLock.lock();
        try {
            int piece_score = 0;

            for(int piece = P; piece <= k; piece++) {
                piece_score += BitBoardUtils.countBits(this.chessboard.bitboards[piece])
                        * PIECE_VALUES[piece];
            }

            // crazy house pocket
            if (this.chessboard.gameVariants == GameVariants.CRAZY_HOUSE) {
                piece_score += this.chessboard.pocket[P] * 1;
                piece_score += this.chessboard.pocket[N] * 3;
                piece_score += this.chessboard.pocket[B] * 3;
                piece_score += this.chessboard.pocket[R] * 5;
                piece_score += this.chessboard.pocket[Q] * 9;

                piece_score -= this.chessboard.pocket[p] * 1;
                piece_score -= this.chessboard.pocket[n] * 3;
                piece_score -= this.chessboard.pocket[b] * 3;
                piece_score -= this.chessboard.pocket[r] * 5;
                piece_score -= this.chessboard.pocket[q] * 9;
            }

            return piece_score;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get piece type on square
     * if not found, returns NONE
     *
     * @param square square
     * @return piece type
     */
    public Piece getPieceOnSquare(Square square){
        readLock.lock();
        try {
            int piece_type = ChessboardUtils.getPieceTypeOnSquare(this.chessboard, square.getIndex());

            return Piece.fromIndex(piece_type);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get legal moves on this chess game
     *
     * @return legal moves
     */
    public List<MoveInfo> getLegalMoves() {
        writeLock.lock();
        try {
            int[] move_list = MoveCache.CHESSGAME_MOVE_CACHE.get();
            int move_count = generateMoves(chessboard, move_list);
            List<MoveInfo> result = new ArrayList<>(move_count);

            for (int count = 0; count < move_count; count++){
                int encodedMove = move_list[count];
                result.add(new MoveInfo(encodedMove));
            }
            return result;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Generate moves only one source square
     * <p>
     * Example : chessboard = start pos, square = e2, returns e2e3, e2e4
     *
     * @return generated move
     */
    public List<MoveInfo> getLegalMovesForSource(Square square) {
        Objects.requireNonNull(square, "Source Square is null!");

        writeLock.lock();
        try {
            int[] move_list = MoveCache.CHESSGAME_MOVE_CACHE.get();
            int move_count = generateMoves(chessboard, move_list);
            List<MoveInfo> result = new ArrayList<>(move_count);

            for (int count = 0; count < move_count; count++){
                int encodedMove = move_list[count];
                if(EncodeMove.getMoveSource(encodedMove) != square.getIndex()) continue;
                result.add(new MoveInfo(encodedMove));
            }
            return result;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Generate moves only one target square
     * <p>
     * Example : chessboard = start pos, square = e4, returns e2e3, e2e4
     *
     * @return generated move
     */
    public List<MoveInfo> getLegalMovesForTarget(Square square) {
        Objects.requireNonNull(square, "Source Square is null!");

        writeLock.lock();
        try {
            int[] move_list = MoveCache.CHESSGAME_MOVE_CACHE.get();
            int move_count = generateMoves(chessboard, move_list);
            List<MoveInfo> result = new ArrayList<>(move_count);

            for (int count = 0; count < move_count; count++){
                int encodedMove = move_list[count];
                if(EncodeMove.getMoveTarget(encodedMove) != square.getIndex()) continue;
                result.add(new MoveInfo(encodedMove));
            }
            return result;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Get board state (Map)(square)(piece)
     *
     * @return board state (Map)
     */
    public Map<Square, Piece> getBoardStateMap() {
        readLock.lock();
        try {
            Map<Square, Piece> result = new EnumMap<>(Square.class);

            for(Square square : Square.values()) {
                Piece piece = getPieceOnSquare(square);
                if (piece != Piece.NONE) {
                    result.put(square, piece);
                }
            }

            return Collections.unmodifiableMap(result);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get whether this square is empty
     *
     * @param square square
     * @return whether this square is empty
     */
    public boolean isEmpty(Square square) {
        readLock.lock();
        try {
            return getPieceOnSquare(square) == Piece.NONE;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get whether the king is under attack
     *
     * @return whether the king is under attack
     */
    public boolean isCheck() {
        readLock.lock();
        try {
            return ChessboardUtils.isCheck(this.chessboard);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get checking piece (max size is 2)
     *
     * @return checking piece
     */
    public List<Square> getChecker() {
        readLock.lock();
        try {
            int checkers = ChessboardUtils.getChecker(chessboard);
            int firstAttacker = checkers & 0x3f;
            int secondAttacker = (checkers >> 6) & 0x3f;
            boolean hasFirst = ((checkers >> 12) & 1) == 1;
            boolean hasSecond = ((checkers >> 13) & 1) == 1;

            if(hasSecond) {
                return List.of(
                        Square.fromIndex(firstAttacker),
                        Square.fromIndex(secondAttacker)
                );
            } else if(hasFirst) {
                return List.of(
                        Square.fromIndex(firstAttacker)
                );
            } else {
                return List.of();
            }
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get whether this square is attacked
     *
     * @param square square
     * @param side attacking side (if true, white is attacking, black otherwise)
     * @return true if square is attacked, false otherwise
     */
    public boolean isSquareAttacked(Square square, boolean side) {
        Objects.requireNonNull(square, "Square cannot be null!");
        readLock.lock();
        try {
            return MoveGenerator.isSquareAttacked(this.chessboard, square.getIndex(), side ? white : black);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get whether this position is checkmate
     *
     * @return whether this position is checkmate
     */
    public boolean isCheckmate() {
        readLock.lock();
        try {
            return ChessboardUtils.isCheckmate(this.chessboard);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get whether this position is stalemate
     *
     * @return whether this position is stalemate
     */
    public boolean isStalemate() {
        readLock.lock();
        try {
            return ChessboardUtils.isStaleMate(this.chessboard);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get whether white or black have been checked three times
     *
     * @return whether white or black have been checked three times
     * @throws VariantNotMatchException if this ChessGame isn't Three check variant
     */
    public boolean isThreeChecked() {
        if(chessboard.gameVariants != GameVariants.THREE_CHECK) throw new VariantNotMatchException(
                "The variant should be three check!"
        );

        readLock.lock();
        try {
            int white_checked = chessboard.check_count[white];
            int black_checked = chessboard.check_count[black];

            return white_checked >= 3 || black_checked >= 3;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get whether this position's white/black king gone to the hill
     *
     * @return whether this position's white/black king gone to the hill
     * @throws VariantNotMatchException if this ChessGame isn't King of the hill variant
     */
    public boolean isKingGoneToHill() {
        if(chessboard.gameVariants != GameVariants.KING_OF_THE_HILL) throw new VariantNotMatchException(
                "The variant should be king of the hill!"
        );

        readLock.lock();
        try {
            return ChessboardUtils.isKingGoneToHill(chessboard);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get whether this horde position's white pieces is all gone (black won)
     *
     * @return whether this horde position's white pieces is all gone
     * @throws VariantNotMatchException if this ChessGame isn't Horde variant
     */
    public boolean isHordePiecesGone() {
        if(chessboard.gameVariants != GameVariants.HORDE) throw new VariantNotMatchException(
                "The variant should be horde!"
        );

        readLock.lock();
        try {
            return ChessboardUtils.isHordePiecesGone(chessboard);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get whether this antichess position overed
     *
     * @return whether this antichess position overed
     * @throws VariantNotMatchException if this ChessGame isn't AntiChess variant
     */
    public boolean isAntiChessOver() {
        if(chessboard.gameVariants != GameVariants.ANTICHESS) throw new VariantNotMatchException(
                "The variant should be antichess!"
        );

        readLock.lock();
        try {
            return ChessboardUtils.isAntiChessOver(chessboard);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get whether this atomic position overed
     *
     * @return whether this atomic position overed
     */
    public boolean isAtomicOver() {
        if(chessboard.gameVariants != GameVariants.ATOMIC) throw new VariantNotMatchException(
                "The variant should be atomic!"
        );

        readLock.lock();
        try {
            return ChessboardUtils.isAtomicOver(chessboard);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get whether racing kings is over (on racing kings variant)
     *
     * @return whether racing kings is over
     * @throws VariantNotMatchException if this ChessGame isn't racing kings variant
     */
    public boolean isKingRaceOver() {
        if(chessboard.gameVariants != GameVariants.RACING_KINGS) throw new VariantNotMatchException(
                "The variant should be racing kings!"
        );

        readLock.lock();
        try {
            return ChessboardUtils.getGameResultForRacingKings(chessboard) != ChessboardUtils.ONGOING_VALUE;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get whether this position allows claiming a draw by threefold repetition
     *
     * @return whether threefold repetition draw can be claimed
     */
    public boolean canClaimThreefoldRepetition() {
        readLock.lock();
        try {
            return ChessboardUtils.getRepetitionCount(this.chessboard, 3) >= 3;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get whether this position is fivefold repetition
     *
     * @return whether this position is fivefold repetition
     */
    public boolean isFivefoldRepetition() {
        readLock.lock();
        try {
            // zobrist hash
            return ChessboardUtils.getRepetitionCount(this.chessboard, 5) >= 5;
        } finally {
            readLock.unlock();
        }
    }

    public boolean isInsufficientMaterial() {
        readLock.lock();
        try {
            if (chessboard.gameVariants == GameVariants.ANTICHESS
                    || chessboard.gameVariants == GameVariants.ATOMIC
                    || chessboard.gameVariants == GameVariants.THREE_CHECK
                    || chessboard.gameVariants == GameVariants.KING_OF_THE_HILL
                    || chessboard.gameVariants == GameVariants.RACING_KINGS
                    || chessboard.gameVariants == GameVariants.HORDE) {
                return false;
            }

            if (this.chessboard.gameVariants == GameVariants.CRAZY_HOUSE) {
                int totalPocketPieces = 0;
                for (int piece = P; piece <= k; piece++) totalPocketPieces += this.chessboard.pocket[piece];
                if (totalPocketPieces > 0) return false;
            }

            if(chessboard.bitboards[P] != 0 || chessboard.bitboards[p] != 0) return false;
            if(chessboard.bitboards[R] != 0 || chessboard.bitboards[r] != 0) return false;
            if(chessboard.bitboards[Q] != 0 || chessboard.bitboards[q] != 0) return false;

            int white_knight = BitBoardUtils.countBits(chessboard.bitboards[N]);
            int black_knight = BitBoardUtils.countBits(chessboard.bitboards[n]);

            int white_bishop = BitBoardUtils.countBits(chessboard.bitboards[B]);
            int black_bishop = BitBoardUtils.countBits(chessboard.bitboards[b]);

            int white_minor = white_knight + white_bishop;
            int black_minor = black_knight + black_bishop;

            if (white_minor + black_minor <= 1) return true;

            if (white_bishop == 1 && black_bishop == 1) {
                long LIGHT_SQUARES = 0x55AA55AA55AA55AAL;

                boolean isWhiteBishopOnLight = (chessboard.bitboards[B] & LIGHT_SQUARES) != 0;
                boolean isBlackBishopOnLight = (chessboard.bitboards[b] & LIGHT_SQUARES) != 0;

                return isWhiteBishopOnLight == isBlackBishopOnLight;
            }

            return false;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get whether this position is seventy-five moves draw
     *
     * @return whether this position is seventy-five moves draw
     */
    public boolean isSeventyFiveMoves() {
        readLock.lock();
        try {
            return chessboard.half_ply >= 150;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get whether this position can be claimed fifty moves draw
     *
     * @return whether this position can be claimed fifty moves draw
     */
    public boolean canClaimFiftyMoves() {
        readLock.lock();
        try {
            return chessboard.half_ply >= 100;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get whether this position can be claimed draw
     *
     * @return whether this position can be claimed draw
     */
    public boolean canClaimDraw() {
        readLock.lock();

        try {
            return canClaimFiftyMoves() || canClaimThreefoldRepetition();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get claimable draw reason <br>
     * like 50 moves draw claim, threefold draw claim
     *
     * @return claimable draw reason
     */
    public GameOverReason getClaimableDrawReason() {
        readLock.lock();

        try {
            if (canClaimThreefoldRepetition()) return GameOverReason.THREEFOLD_CLAIM;
            if (canClaimFiftyMoves()) return GameOverReason.FIFTYMOVES_CLAIM;
            return GameOverReason.NOTGAMEOVER;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get whether this game overed. <br>
     * If not, return <b>GameOverReason.NOTGAMEOVER</b>.
     *
     * @param includeClaimableDraws include claimable draws like 50 moves draw claim, threefold repetition claim
     *
     * @return game over reason (if not, return GameOverReason.NOTGAMEOVER)
     */
    public GameOverReason isGameOver(boolean includeClaimableDraws) {
        readLock.lock();

        try {
            if(chessboard.gameVariants == GameVariants.ANTICHESS) {
                if(isAntiChessOver()) return GameOverReason.ANTICHESS;
            }
            if(chessboard.gameVariants == GameVariants.ATOMIC) {
                if(isAtomicOver()) return GameOverReason.ATOMIC;
            }
            if(chessboard.gameVariants == GameVariants.THREE_CHECK) {
                if(isThreeChecked()) return GameOverReason.THREE_CHECK;
            }
            if(chessboard.gameVariants == GameVariants.KING_OF_THE_HILL) {
                if(isKingGoneToHill()) return GameOverReason.KING_OF_THE_HILL;
            }
            if(chessboard.gameVariants == GameVariants.HORDE) {
                if(isHordePiecesGone()) return GameOverReason.HORDE;
            }
            if(chessboard.gameVariants == GameVariants.RACING_KINGS) {
                if(isKingRaceOver()) return GameOverReason.KING_RACE;
            }

            if (includeClaimableDraws) {
                if (canClaimThreefoldRepetition()) return GameOverReason.THREEFOLD_CLAIM;
                if (canClaimFiftyMoves()) return GameOverReason.FIFTYMOVES_CLAIM;
            }

            if(chessboard.gameVariants != GameVariants.ANTICHESS) {
                boolean inCheck = isCheck();

                if (inCheck) {
                    if (isCheckmate()) return GameOverReason.CHECKMATE;
                } else {
                    if (isStalemate()) return GameOverReason.STALEMATE;
                }
            }

            if(isFivefoldRepetition()) return GameOverReason.FIVEFOLD;
            if(isSeventyFiveMoves()) return GameOverReason.SEVENTYFIVE_MOVES;
            if(isInsufficientMaterial()) return GameOverReason.INSUFFICIENTMATERIAL;

            return GameOverReason.NOTGAMEOVER;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get whether this game overed. <br>
     * If not, return <b>GameOverReason.NOTGAMEOVER</b>.
     * <br>
     * this includes claimable draws like fifty moves draw claim, threefold repetition draw claim.
     *
     *
     * @return game over reason (if not, return GameOverReason.NOTGAMEOVER)
     */
    public GameOverReason isGameOver() {
        return isGameOver(true);
    }

    /**
     * Convert LAN (like e2e4 e7e5 g1f3) to SAN (like e4 e5 Nf3)
     *
     * @param lanMove LAN move
     * @return converted SAN move
     */
    public String toSan(String lanMove){
        readLock.lock();
        try {
            Chessboard tempBoard = new Chessboard(chessboard);
            return ConvertStringMoveUtils.translateLanSequence(tempBoard, lanMove).trim();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Convert move data to SAN (like e4 e5 Nf3)
     *
     * @param moveData move data
     * @return converted SAN move
     */
    public String toSan(List<MoveInfo> moveData){
        readLock.lock();
        try {
            Chessboard tempBoard = new Chessboard(chessboard);

            StringBuilder sb = new StringBuilder();
            for(MoveInfo moveInfo : moveData) {
                sb.append(moveInfo.toString()).append(" ");
            }

            return ConvertStringMoveUtils.translateLanSequence(tempBoard, sb.toString()).trim();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Translate SAN string to LAN string
     *
     * @param san SAN move
     * @return Translated string result
     */
    public String toLanString(String san) {
        Chessboard tempBoard;
        readLock.lock();
        try {
            tempBoard = new Chessboard(this.chessboard);
            return ConvertStringMoveUtils.toLanString(tempBoard, san);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Translate SAN string to MoveInfo
     *
     * @param san SAN move
     * @return Translated move data result
     */
    public MoveInfo toLanMoveData(String san) {
        Chessboard tempBoard;
        readLock.lock();
        try {
            tempBoard = new Chessboard(this.chessboard);
            return new MoveInfo(ConvertStringMoveUtils.sanToMoveData(tempBoard, san));
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get 3 check 'check count' <br>
     * first index is white's checked count, <br>
     * second index is black's checked count.
     *
     * @return checked count for each white/black
     * @throws VariantNotMatchException if variant isn't three check
     */
    public int[] getCheckCount() {
        readLock.lock();
        try {
            if (chessboard.gameVariants != GameVariants.THREE_CHECK)
                throw new VariantNotMatchException("This method should be called on three check variant ChessGame!");
            return new int[]{chessboard.check_count[white], chessboard.check_count[black]};
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get 3 check 'white's checked count' <br>
     *
     * @return checked count for white
     * @throws VariantNotMatchException if variant isn't three check
     */
    public int getWhiteCheckedCount() {
        readLock.lock();
        try {
            if (chessboard.gameVariants != GameVariants.THREE_CHECK)
                throw new VariantNotMatchException("This method should be called on three check variant ChessGame!");
            return chessboard.check_count[white];
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get 3 check 'black's checked count' <br>
     *
     * @return checked count for black
     * @throws VariantNotMatchException if variant isn't three check
     */
    public int getBlackCheckedCount() {
        readLock.lock();
        try {
            if (chessboard.gameVariants != GameVariants.THREE_CHECK)
                throw new VariantNotMatchException("This method should be called on three check variant ChessGame!");
            return chessboard.check_count[black];
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get 'full move' on this ChessGame
     *
     * @return full move
     */
    public int getFullMove() {
        readLock.lock();
        try {
            return this.chessboard.full_move / 2 + 1;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get 'half move' on this ChessGame
     *
     * @return half move
     */
    public int getHalfMove() {
        readLock.lock();
        try {
            return this.chessboard.half_ply;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Force to make the game end
     *
     * @param result game result
     * @param reason game over reason
     */
    private void forceEndGame(GameResult result, GameOverReason reason) {
        writeLock.lock();
        try {
            if (this.gameoverReason != GameOverReason.NOTGAMEOVER) {
                throw new IllegalStateException("This game is already finished!");
            }

            this.gameResult = result;
            this.gameoverReason = reason;
            this.headers.put("Result", PGNUtils.getGameResultString(this.gameResult));

            this.currentNode.terminalResult = result;
            this.currentNode.terminalReason = reason;
        } finally {
            writeLock.unlock();
        }

        notifyGameOver(result, reason);
    }


    /**
     * Get game result
     *
     * @return game result
     */
    public GameResult getGameResult() {
        writeLock.lock();
        try {
            if(evaluateGameState(getLastMainlineNode(this.moveHistoryRoot)) != GameResult.UNKNOWN) {
                notifyGameOver(this.gameResult, this.gameoverReason);
            }
            return this.gameResult;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Get game over reason
     *
     * @return game over reason
     */
    public GameOverReason getGameoverReason() {
        writeLock.lock();
        try {
            if(evaluateGameState(getLastMainlineNode(this.moveHistoryRoot)) != GameResult.UNKNOWN) {
                notifyGameOver(this.gameResult, this.gameoverReason);
            }
            return this.gameoverReason;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * When one of player have resigned
     *
     * @param isWhiteResigning is player white
     */
    public void resign(boolean isWhiteResigning) {
        GameResult result = isWhiteResigning ? GameResult.BLACK_WON : GameResult.WHITE_WON;
        forceEndGame(result, GameOverReason.RESIGNATION);
    }

    /**
     * If both players agreed draw
     */
    public void agreeDraw() {
        forceEndGame(GameResult.DRAW, GameOverReason.AGREEMENTDRAW);
    }

    /**
     * If one of player time overed
     *
     * @param isWhiteFlagged is white time overed
     */
    public void timeOver(boolean isWhiteFlagged) {
        GameResult result = isWhiteFlagged ? GameResult.BLACK_WON : GameResult.WHITE_WON;
        forceEndGame(result, GameOverReason.TIMEOVER);
    }

    /**
     * External adjudication
     *
     * @param result result
     */
    public void adjudication(GameResult result) {
        forceEndGame(result, GameOverReason.ADJUDICATION);
    }

    /**
     * When server have to force this game end
     *
     * @param result game result
     * @param reason game over reason
     */
    public void forceEndGameExternal(GameResult result, GameOverReason reason) {
        forceEndGame(result, reason);
    }

    /**
     * Add move data on move history (tree)
     *
     * @param moveData move info (data)
     * @return whether history changed
     *
     * @throws MoveNotFoundException - could not find the node
     */
    private boolean addMoveHistory(MoveInfo moveData) {
        for(int i = 0; i < currentNode.children.size(); i++) {
            MoveNode child = currentNode.children.get(i);

            if (moveData.originEncodedData() == child.moveData.originEncodedData()) {
                currentNode = child;

                return false;
            }
        }

        MoveNode result = new MoveNode(moveData, currentNode);

        currentNode.children.add(result);
        currentNode = result;

        nodeCache.put(result.id, result);

        return true;
    }

    /**
     * Remove cache on this node and this node's children
     *
     * @param node node
     */
    private void removeNodeFromCache(MoveNode node) {
        if (node == null) return;

        nodeCache.remove(node.id);

        for (MoveNode child : node.children) {
            removeNodeFromCache(child);
        }
    }

    /**
     * Remove node and node's all children on tree and cache
     *
     * @param nodeId node to remove (id)
     *
     * @throws MoveNotFoundException when the current node (move) is not found
     * @throws HistoryTreeException when node to remove is root node
     */
    public void deleteVariation(long nodeId) {
        writeLock.lock();
        try {
            MoveNode targetNode = nodeCache.get(nodeId);
            if (targetNode == null) throw new MoveNotFoundException("Could not find the node to delete!");
            if (targetNode == moveHistoryRoot) throw new HistoryTreeException("Cannot delete the root node!");

            MoveNode parent = targetNode.parent;

            boolean isCurrentNodeDeleting = false;
            MoveNode temp = currentNode;
            while (temp != null) {
                if (temp == targetNode) {
                    isCurrentNodeDeleting = true;
                    break;
                }
                temp = temp.parent;
            }

            if (isCurrentNodeDeleting) {
                jumpToNode(parent.id);
            }

            parent.children.remove(targetNode);

            removeNodeFromCache(targetNode);
        } finally {
            writeLock.unlock();
        }

        notifyHistoryChanged();
    }

    /**
     * Promote this node to mainline on nodeId's parent
     * <p>
     * Example : <br>
     * e4 e5 Nf3 (Nc3 Nf6 <-) <br>
     * and the result is <br>
     * e4 e5 Nc3 (Nf3) Nf6
     *
     * @param nodeId node to promote
     */
    public void promoteVariationLocal(long nodeId) {
        boolean shouldNotifyHistory = false;

        writeLock.lock();
        try {
            MoveNode targetNode = nodeCache.get(nodeId);
            if (targetNode == null) throw new MoveNotFoundException("Could not find the node to promote!");
            if (targetNode == moveHistoryRoot || targetNode.parent == null) return;

            MoveNode parent = targetNode.parent;
            int currentIndex = parent.children.indexOf(targetNode);

            if (currentIndex > 0) {
                parent.children.remove(currentIndex);
                parent.children.addFirst(targetNode);

                shouldNotifyHistory = true;
            }
        } finally {
            writeLock.unlock();
        }

        if(shouldNotifyHistory) notifyHistoryChanged();
    }

    /**
     * Get Root node on move history <br>
     *
     * @return Root node
     */
    public MoveNodeDTO getRootNode() {
        readLock.lock();
        try {
            return convertToDTO(moveHistoryRoot);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get current node's UUID
     * When current node is null, returns -1
     *
     * @return current node's uuid string
     */
    public long getCurrentNodeId() {
        readLock.lock();
        try {
            if (currentNode == null) return -1;

            return currentNode.id;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get current node as DTO
     *
     * @return MoveNodeDTO of the current node
     */
    public MoveNodeDTO getCurrentNodeDTO() {
        readLock.lock();
        try {
            if (currentNode == null) return null;

            return convertToDTO(currentNode);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Move position to node (nodeId)
     *
     * @param nodeId node uuid
     */
    public void jumpToNode(long nodeId) {
        writeLock.lock();

        GameResult gameResult = GameResult.UNKNOWN;

        try {
            // get node
            MoveNode targetNode = nodeCache.get(nodeId);
            if (targetNode == null) {
                throw new MoveNotFoundException("Could not find the node!");
            }

            // get node path
            List<MoveNode> historyPath = new ArrayList<>();
            MoveNode temp = targetNode;
            while (temp != moveHistoryRoot) {
                historyPath.add(temp);
                temp = temp.parent;
            }

            Collections.reverse(historyPath);

            // reset pos
            ChessboardUtils.parseFen(this.chessboard, this.startPositionFEN);

            for (MoveNode node : historyPath) {
                if(chessboard.gameVariants == GameVariants.STANDARD) {
                    MoveGenerator.makeStandardMove(this.chessboard, node.moveData.originEncodedData());
                } else {
                    MoveGenerator.makeMove(this.chessboard, node.moveData.originEncodedData());
                }
            }

            this.currentNode = targetNode;
            if(autoChangeGameOver) {
                gameResult = evaluateGameState(currentNode);
            }
        } finally {
            writeLock.unlock();
        }

        notifyPositionJumped(getFEN());
        if(gameResult != GameResult.UNKNOWN) {
            notifyGameOver(this.gameResult, this.gameoverReason);
        }
    }

    /**
     * Jump to mainline ply
     * <p>
     * Example : <br>
     * e2e4 e7e5 g1f3 g8f6
     * jumpToMainlinePly(2) and result is e7e5
     *
     * @param targetPly ply
     *
     * @throws MoveNotFoundException - if move is not found or targetPly is out of bounds
     */
    public void jumpToMainlinePly(int targetPly) {
        writeLock.lock();

        GameResult gameResult;

        try {
            if(targetPly < 0) throw new MoveNotFoundException("Target ply is less than 0!");
            if(currentNode == null) throw new MoveNotFoundException("Current node is null!");

            int currentPly = 0;
            MoveNode temp = this.currentNode;
            List<MoveNode> history = new ArrayList<>();

            while (temp != moveHistoryRoot && temp != null) {
                history.add(temp);
                currentPly++;
                temp = temp.parent;
            }

            if (currentPly == targetPly) return;

            if (targetPly < currentPly) {
                Collections.reverse(history);

                ChessboardUtils.parseFen(this.chessboard, this.startPositionFEN);
                MoveNode newCurrentNode = moveHistoryRoot;

                for (int i = 0; i < targetPly; i++) {
                    MoveNode nextNode = history.get(i);
                    if(chessboard.gameVariants == GameVariants.STANDARD) {
                        MoveGenerator.makeStandardMove(this.chessboard, nextNode.moveData.originEncodedData());
                    } else {
                        MoveGenerator.makeMove(this.chessboard, nextNode.moveData.originEncodedData());
                    }
                    newCurrentNode = nextNode;
                }
                this.currentNode = newCurrentNode;
            } else {
                while (currentPly < targetPly && !this.currentNode.children.isEmpty()) {
                    MoveNode nextNode = this.currentNode.children.getFirst();

                    if(chessboard.gameVariants == GameVariants.STANDARD) {
                        MoveGenerator.makeStandardMove(this.chessboard, nextNode.moveData.originEncodedData());
                    } else {
                        MoveGenerator.makeMove(this.chessboard, nextNode.moveData.originEncodedData());
                    }
                    this.currentNode = nextNode;
                    currentPly++;
                }

                if (currentPly < targetPly) {
                    throw new MoveNotFoundException("Variation history out of bounds! Reached maximum ply: " + currentPly);
                }
            }

            gameResult = evaluateGameState(currentNode);
        } finally {
            writeLock.unlock();
        }

        notifyPositionJumped(getFEN());
        if(gameResult != GameResult.UNKNOWN) {
            notifyGameOver(this.gameResult, this.gameoverReason);
        }
    }

    /**
     * Add default headers like Event, Site, etc.
     */
    private void setDefaultHeaders() {
        headers.put("Event", "?");
        headers.put("Site", "?");
        headers.put("Date", "????.??.??");
        headers.put("Round", "?");
        headers.put("White", "?");
        headers.put("Black", "?");
        headers.put("Result", "*");
    }

    /**
     * Get headers like Event, Site, etc.
     *
     * @return header map (String, value)
     */
    public LinkedHashMap<String, String> getHeaders() {
        readLock.lock();
        try {
            return headers;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Set / Add header
     *
     * @param key header string
     * @param value header value
     */
    public void setHeader(String key, String value) {
        writeLock.lock();
        try {
            this.headers.put(key, value);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Change flag changing game over state when moved / unmoved. <br>
     * if you want efficiency, disable this to get more NPS. but you can't use Listener on onGameOver method. <br>
     *
     * @param autoChangeGameOver en/disable flag changing game over state when moved
     */
    public void setAutoChangingGameOver(boolean autoChangeGameOver) {
        writeLock.lock();
        try {
            this.autoChangeGameOver = autoChangeGameOver;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Update this node's game over state and return result.
     * if this node's game over state is already there, just return cached value.
     *
     * @param node node
     *
     * @return Game result
     */
    private GameResult evaluateGameState(MoveNode node) {
        // when resign / agreement draw
        if (node.terminalReason != null) {
            this.gameResult = node.terminalResult;
            this.gameoverReason = node.terminalReason;
            return node.terminalResult;
        }

        // when value is already cached
        if (node.isStateEvaluated) {
            this.gameResult = node.calculatedResult;
            this.gameoverReason = node.calculatedReason;
            return node.calculatedResult;
        }

        // if value is not cached

        GameOverReason reason = isGameOver();
        GameResult result = GameResult.UNKNOWN;

        if (reason != GameOverReason.NOTGAMEOVER) {
            result = switch (reason) {
                case CHECKMATE -> getTurn() ? GameResult.BLACK_WON : GameResult.WHITE_WON;
                case THREE_CHECK -> getWhiteCheckedCount() >= 3 ? GameResult.BLACK_WON : GameResult.WHITE_WON;
                case KING_OF_THE_HILL -> (chessboard.bitboards[K] & BoardSquares.CENTER_SQUARES) != 0
                        ? GameResult.WHITE_WON : GameResult.BLACK_WON;
                case HORDE -> GameResult.BLACK_WON;
                case KING_RACE -> {
                    int racingResult = ChessboardUtils.getGameResultForRacingKings(chessboard);
                    if(racingResult == ChessboardUtils.WHITE_WON_VALUE) yield GameResult.WHITE_WON;
                    else if(racingResult == ChessboardUtils.BLACK_WON_VALUE) yield GameResult.BLACK_WON;
                    else if(racingResult == ChessboardUtils.DREW_VALUE) yield GameResult.DRAW;
                    else yield GameResult.UNKNOWN;
                }
                case ANTICHESS -> getTurn() ? GameResult.BLACK_WON : GameResult.WHITE_WON;
                case ATOMIC -> {
                    if(chessboard.bitboards[k] == 0L) yield GameResult.WHITE_WON;
                    if(chessboard.bitboards[K] == 0L) yield GameResult.BLACK_WON;
                    yield GameResult.UNKNOWN;
                }
                case STALEMATE, FIVEFOLD, FIFTYMOVES_CLAIM, INSUFFICIENTMATERIAL,
                     SEVENTYFIVE_MOVES, THREEFOLD_CLAIM -> GameResult.DRAW;
                default -> GameResult.UNKNOWN;
            };
        }

        node.calculatedReason = reason;
        node.calculatedResult = result;
        node.isStateEvaluated = true;

        this.gameoverReason = reason;
        this.gameResult = result;

        if (result != GameResult.UNKNOWN) {
            this.headers.put("Result", PGNUtils.getGameResultString(this.gameResult));
            return this.gameResult;
        }

        return result;
    }

    /**
     * Save clock data on last move on MoveNode(DTO)
     *
     * @param hours hours data
     * @param minutes minutes data
     * @param seconds seconds data
     */
    public void setCurrentMoveClock(int hours, int minutes, int seconds) {
        writeLock.lock();
        try {
            if (this.currentNode == moveHistoryRoot) throw new ClockException("...");

            StringBuilder sb = new StringBuilder(8);
            sb.append(hours).append(':');
            if (minutes < 10) sb.append('0');
            sb.append(minutes).append(':');
            if (seconds < 10) sb.append('0');
            sb.append(seconds);

            this.currentNode.getAnnotation().clk = sb.toString();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Save clock data on last move on MoveNode(DTO)
     *
     * @param seconds seconds data
     */
    public void setCurrentMoveClock(int seconds) {
        int resultHours = seconds / 3600;
        int resultMinutes = (seconds % 3600) / 60;
        int resultSeconds = seconds % 60;
        setCurrentMoveClock(resultHours, resultMinutes, resultSeconds);
    }

    /**
     * Save clock data on last move on MoveNode(DTO)
     *
     * @param milliseconds milliseconds data
     */
    public void setCurrentMoveClockMilliSeconds(long milliseconds) {
        writeLock.lock();
        try {
            if (this.currentNode == moveHistoryRoot) throw new ClockException("...");

            long seconds = milliseconds / 1000;
            long resultHours = seconds / 3600;
            long resultMinutes = (seconds % 3600) / 60;
            long resultSeconds = seconds % 60;
            long decimalPoint = milliseconds % 1000 / 100;

            StringBuilder sb = new StringBuilder(8);
            sb.append(resultHours).append(':');
            if (resultMinutes < 10) sb.append('0');
            sb.append(resultMinutes).append(':');
            if (resultSeconds < 10) sb.append('0');
            sb.append(resultSeconds);
            sb.append(".").append(decimalPoint);

            this.currentNode.getAnnotation().clk = sb.toString();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Save clock data on last move on MoveNode(DTO)
     *
     * @param clkTime string format like "0:05:00"
     */
    public void setCurrentMoveClock(String clkTime) {
        writeLock.lock();
        try {
            if (this.currentNode == moveHistoryRoot) throw new ClockException("Current position can not be start position!");
            this.currentNode.getAnnotation().clk = clkTime;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Save time stamp data on last move on MoveNode(DTO)
     *
     * @param timeStamp time elapsed string
     */
    public void setTimeStamp(String timeStamp) {
        writeLock.lock();
        try {
            if (this.currentNode == moveHistoryRoot) throw new ClockException("Current position can not be start position!");
            this.currentNode.getAnnotation().timeStamp = timeStamp;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Add engine eval data on this current move
     *
     * @param eval eval data like "1.25", "#-3"...
     */
    public void setCurrentMoveEval(String eval) {
        writeLock.lock();
        try {
            if (this.currentNode == moveHistoryRoot) return;
            this.currentNode.getAnnotation().eval = eval;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Add highlighting square data on this current move
     *
     * @param csl square data like "Ge4" (Green square on e4), "Yd5" (Yellow square on d5)
     */
    public void setCurrentMoveCsl(String csl) {
        writeLock.lock();
        try {
            if (this.currentNode == moveHistoryRoot) return;
            this.currentNode.getAnnotation().csl = csl;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Add highlighting arrow data on this current move
     *
     * @param cal arrow data like "Gg1f3" (Green arrow g1 to f3), "Ye2e4" (Yellow arrow e2 to e4)
     */
    public void setCurrentMoveCal(String cal) {
        writeLock.lock();
        try {
            if (this.currentNode == moveHistoryRoot) return;
            this.currentNode.getAnnotation().cal = cal;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Add comment data on this current move
     *
     * @param comment comment string
     */
    public void setCurrentMoveComment(String comment) {
        writeLock.lock();
        try {
            if (this.currentNode == moveHistoryRoot) return;
            this.currentNode.getAnnotation().comment = comment;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Convert MoveNode to MoveNodeDTO
     *
     * @param node MoveNode class
     * @return converted MoveNodeDTO
     */
    private MoveNodeDTO convertToDTO(MoveNode node) {
        if (node == null) return null;

        List<MoveNodeDTO> childDTOs = node.children.stream()
                .map(this::convertToDTO)
                .toList();

        MoveAnnotationDTO annotationDTO = null;
        if (node.getAnnotation() != null) {
            MoveAnnotation anno = node.getAnnotation();
            annotationDTO = new MoveAnnotationDTO(
                    anno.comment, anno.nag, anno.clk, anno.timeStamp,
                    anno.eval, anno.csl, anno.cal
            );
        }

        return new MoveNodeDTO(
                node.id,
                node.moveData,
                childDTOs,
                node.san,
                annotationDTO
        );
    }

    /**
     * Parse "Variant" section on PGN header to GameVariant enum
     *
     * @param variantValue "Variant" section on PGN header
     * @return GameVariant enum
     */
    private static GameVariants parseVariantHeader(String variantValue) {
        if (variantValue == null) return GameVariants.STANDARD;

        return switch (variantValue.trim().toLowerCase()) {
            case "crazyhouse" -> GameVariants.CRAZY_HOUSE;
            case "three-check", "threecheck", "3-check", "3check" -> GameVariants.THREE_CHECK;
            case "king of the hill", "kingofthehill", "koth" -> GameVariants.KING_OF_THE_HILL;
            case "horde", "hord", "hd" -> GameVariants.HORDE;
            case "racing kings", "racing king", "racingkings", "racingking"
            ,"king race", "kingrace", "kr" -> GameVariants.RACING_KINGS;
            case "antichess", "anti chess", "ac", "anti", "giveaway", "losing chess",
                 "losingchess", "suicide chess", "suicidechess" -> GameVariants.ANTICHESS;
            case "atomic", "atomic chess", "atom", "at", "nuclear", "nuclear chess",
                 "explosion chess", "bomb chess" -> GameVariants.ATOMIC;
            default -> GameVariants.STANDARD;
        };
    }

    /**
     * Load PGN on this ChessGame
     *
     * @param pgnString PGN data
     */
    public PGNGame loadPGN(String pgnString) {
        if (pgnString == null || pgnString.isEmpty()) {
            throw new IllegalArgumentException("PGN string is empty");
        }
        pgnString = pgnString.replace("\uFEFF", "");

        Map<String, String> parsedHeaders = new HashMap<>();
        String[] lines = pgnString.split("\\R");
        int line_stopped = -1;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("[")) {
                line = line.substring(1, line.length() - 1);
                String[] parts = line.split(" ", 2);
                if (parts.length == 2) {
                    String type = parts[0];
                    String what = parts[1].replace("\"", "");
                    parsedHeaders.put(type, what);
                }
            } else {
                line_stopped = i;
                break;
            }
        }

        String movePGNString = "";
        if (line_stopped != -1) {
            StringBuilder moveBuilder = new StringBuilder();
            for (int i = line_stopped; i < lines.length; i++) {
                moveBuilder.append(lines[i]).append("\n");
            }
            movePGNString = moveBuilder.toString();
        }

        MoveNode rootNode = new MoveNode();
        MoveNode currentParsedNode = rootNode;
        Map<Long, MoveNode> tempNodeCache = new HashMap<>();
        tempNodeCache.put(rootNode.id, rootNode);

        record VariationState(MoveNode node, Chessboard snapshotBoard) {}
        Stack<VariationState> variationStack = new Stack<>();

        Chessboard pgnChessboard;
        GameVariants parsedVariant = parseVariantHeader(parsedHeaders.get("Variant"));
        boolean isChess960 = false;
        if(parsedHeaders.containsKey("Variant")) {
            switch (parsedHeaders.get("Variant").trim().toLowerCase()) {
                case "chess960", "fischerandom", "fischerrandom" -> isChess960 = true;
            }
        }

        if ("1".equals(parsedHeaders.get("SetUp")) && parsedHeaders.containsKey("FEN")) {
            pgnChessboard = new Chessboard(parsedHeaders.get("FEN"), isChess960, parsedVariant);
        } else {
            pgnChessboard = new Chessboard(getDefaultStartPosition(parsedVariant), isChess960, parsedVariant);
        }

        GameResult parsedGameResult = GameResult.UNKNOWN;

        PGNLexer lexer = new PGNLexer(movePGNString);
        PGNToken currentToken;

        while ((currentToken = lexer.nextToken()).type() != TokenType.EOF) {
            switch (currentToken.type()) {
                case COMMENT:
                    String rawComment = currentToken.value().trim();

                    Matcher clkMatcher = CLK_PATTERN.matcher(rawComment);
                    if (clkMatcher.find()) {
                        currentParsedNode.getAnnotation().clk = clkMatcher.group(1);
                        rawComment = clkMatcher.replaceAll("").trim();
                    }

                    Matcher timestampMatcher = TIMESTAMP_PATTERN.matcher(rawComment);
                    if (timestampMatcher.find()) {
                        currentParsedNode.getAnnotation().timeStamp = timestampMatcher.group(1);
                        rawComment = timestampMatcher.replaceAll("").trim();
                    }

                    Matcher evalMatcher = EVAL_PATTERN.matcher(rawComment);
                    if (evalMatcher.find()) {
                        currentParsedNode.getAnnotation().eval = evalMatcher.group(1);
                        rawComment = evalMatcher.replaceAll("").trim();
                    }

                    Matcher cslMatcher = CSL_PATTERN.matcher(rawComment);
                    if (cslMatcher.find()) {
                        currentParsedNode.getAnnotation().csl = cslMatcher.group(1);
                        rawComment = cslMatcher.replaceAll("").trim();
                    }

                    Matcher calMatcher = CAL_PATTERN.matcher(rawComment);
                    if (calMatcher.find()) {
                        currentParsedNode.getAnnotation().cal = calMatcher.group(1);
                        rawComment = calMatcher.replaceAll("").trim();
                    }

                    if (!rawComment.isEmpty()) {
                        currentParsedNode.getAnnotation().comment = (currentParsedNode.getAnnotation().comment == null)
                                ? rawComment : currentParsedNode.getAnnotation().comment + " " + rawComment;
                    }
                    break;

                case NAG:
                    currentParsedNode.getAnnotation().nag = currentToken.value();
                    break;

                case VARIATION_START:
                    variationStack.push(new VariationState(currentParsedNode, new Chessboard(pgnChessboard)));
                    if (currentParsedNode.moveData != null) {
                        MoveGenerator.unmakeMove(pgnChessboard, currentParsedNode.moveData.originEncodedData());
                        currentParsedNode = currentParsedNode.parent;
                    }
                    break;

                case VARIATION_END:
                    if (!variationStack.isEmpty()) {
                        VariationState state = variationStack.pop();
                        currentParsedNode = state.node;
                        pgnChessboard = state.snapshotBoard;
                    } else {
                        throw new PGNConvertException("Variation stack is empty!");
                    }
                    break;

                case RESULT:
                    if (currentToken.value().equals("1-0")) parsedGameResult = GameResult.WHITE_WON;
                    if (currentToken.value().equals("0-1")) parsedGameResult = GameResult.BLACK_WON;
                    if (currentToken.value().equals("1/2-1/2")) parsedGameResult = GameResult.DRAW;
                    break;

                case MOVE:
                    String rawSan = currentToken.value();
                    int cleanEnd = rawSan.length();
                    while (cleanEnd > 0) {
                        char lastChar = rawSan.charAt(cleanEnd - 1);
                        if (lastChar == '!' || lastChar == '?') cleanEnd--;
                        else break;
                    }

                    String pureSan = rawSan.substring(0, cleanEnd);
                    String annotation = rawSan.substring(cleanEnd);

                    int moveData = ConvertStringMoveUtils.sanToMoveData(pgnChessboard, pureSan);
                    if (!ChessboardUtils.isLegalMove(pgnChessboard, moveData)) {
                        throw new IllegalMoveException(new MoveInfo(moveData).toString(),
                                ChessboardUtils.getFen(pgnChessboard));
                    }
                    MoveGenerator.makeMove(pgnChessboard, moveData);

                    MoveInfo moveInfo = new MoveInfo(moveData);
                    MoveNode newNode = new MoveNode(moveInfo, currentParsedNode);
                    newNode.san = pureSan;

                    if (!annotation.isEmpty()) {
                        String parsedNag = switch (annotation) {
                            case "!" -> "$1"; case "?" -> "$2"; case "!!" -> "$3";
                            case "??" -> "$4"; case "!?" -> "$5"; case "?!" -> "$6";
                            default -> "";
                        };
                        if (!parsedNag.isEmpty()) {
                            newNode.getAnnotation().nag =
                                    (newNode.getAnnotation().nag == null || newNode.getAnnotation().nag.isEmpty())
                                            ? parsedNag : newNode.getAnnotation().nag + " " + parsedNag;
                        }
                    }

                    currentParsedNode.children.add(newNode);
                    currentParsedNode = newNode;
                    tempNodeCache.put(newNode.id, newNode);
                    break;
            }
        }

        if (parsedGameResult != GameResult.UNKNOWN) {
            MoveNode lastNode = getLastMainlineNode(rootNode);
            lastNode.terminalResult = parsedGameResult;

            if (ChessboardUtils.isCheckmate(pgnChessboard)) {
                lastNode.terminalReason = GameOverReason.CHECKMATE;
            } else if (ChessboardUtils.isStaleMate(pgnChessboard)) {
                lastNode.terminalReason = GameOverReason.STALEMATE;
            } else {
                lastNode.terminalReason = (parsedGameResult == GameResult.DRAW) ?
                        GameOverReason.AGREEMENTDRAW : GameOverReason.RESIGNATION;
            }
        }

        MoveNodeDTO rootDTO = convertToDTO(rootNode);

        writeLock.lock();
        try {
            String fenToLoad = parsedHeaders.getOrDefault("FEN", getDefaultStartPosition(parsedVariant));
            this.chessboard.gameVariants = parsedVariant;
            this.chessboard.isChess960 = isChess960;
            ChessboardUtils.parseFen(this.chessboard, fenToLoad);
            this.startPositionFEN = fenToLoad;

            this.moveHistoryRoot = rootNode;
            this.currentNode = rootNode;

            this.nodeCache.clear();
            this.nodeCache.putAll(tempNodeCache);

            this.headers.clear();
            setDefaultHeaders();
            this.headers.putAll(parsedHeaders);

            this.gameResult = parsedGameResult;
        } finally {
            writeLock.unlock();
        }

        return new PGNGame(parsedHeaders, rootDTO, parsedGameResult);
    }

    public PGNGame toPGNGame(int maxNodes) {
        writeLock.lock();
        try {
            if(this.headers.isEmpty()) setDefaultHeaders();

            if(chessboard.isChess960) {
                this.headers.put("Variant", "Chess960");
            }

            // if there is another variant, overwrite it
            if(chessboard.gameVariants != GameVariants.STANDARD) {
                switch (chessboard.gameVariants) {
                    case CRAZY_HOUSE -> this.headers.put("Variant", "Crazyhouse");
                    case THREE_CHECK -> this.headers.put("Variant", "Three-check");
                    case KING_OF_THE_HILL -> this.headers.put("Variant", "King of the Hill");
                    case HORDE -> this.headers.put("Variant", "Horde");
                    case ANTICHESS -> this.headers.put("Variant", "Antichess");
                    case ATOMIC -> this.headers.put("Variant", "Atomic");
                    case RACING_KINGS -> this.headers.put("Variant", "Racing Kings");
                }
            }

            String currentStartFen = this.startPositionFEN;
            if (!currentStartFen.equals(getDefaultStartPosition(chessboard.gameVariants))) {
                this.headers.put("SetUp", "1");
                this.headers.put("FEN", currentStartFen);
            } else {
                this.headers.remove("SetUp");
                this.headers.remove("FEN");
            }

            evaluateGameState(getLastMainlineNode(this.moveHistoryRoot));
            this.headers.put("Result", PGNUtils.getGameResultString(this.gameResult));

            Chessboard tempBoard = new Chessboard(startPositionFEN);
            tempBoard.gameVariants = this.getGameVariants();

            MoveNodeDTO rootDTO = buildPGNTreeWithSan(this.moveHistoryRoot, tempBoard, maxNodes, 0);

            return new PGNGame(new LinkedHashMap<>(this.headers), rootDTO, this.gameResult);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Add san move on pgn tree
     *
     * @param node root node
     * @param tempBoard board
     * @param maxNodesCount max nodes count
     * @param currentNodes current nodes (default : 0)
     * @return root node
     *
     * @throws NodesOverflowException if move count is more than maxNodesCount
     */
    private MoveNodeDTO buildPGNTreeWithSan(MoveNode node, Chessboard tempBoard, int maxNodesCount, int currentNodes) {
        currentNodes++;
        if(maxNodesCount < currentNodes) throw new NodesOverflowException(
                "This pgn's node (move) count is more than max nodes count! (Max node count : " + maxNodesCount + ")"
        );

        String calculatedSan = null;

        if (node.moveData != null) {
            calculatedSan = ConvertStringMoveUtils.toSanString(tempBoard, node.moveData);

            MoveGenerator.makeMove(tempBoard, node.moveData.originEncodedData());
        }

        List<MoveNodeDTO> childrenDTOs = new java.util.ArrayList<>();

        for (MoveNode child : node.children) {
            childrenDTOs.add(buildPGNTreeWithSan(child, new Chessboard(tempBoard), maxNodesCount, currentNodes));
        }

        MoveAnnotation nodeAnnotation = node.getAnnotation();
        MoveAnnotationDTO annotationDTO = new MoveAnnotationDTO(nodeAnnotation.comment,
                nodeAnnotation.nag, nodeAnnotation.clk, nodeAnnotation.timeStamp,
                nodeAnnotation.eval, nodeAnnotation.csl, nodeAnnotation.cal);;

        return new MoveNodeDTO(
                node.id,
                node.moveData,
                childrenDTOs,
                calculatedSan,
                annotationDTO
        );
    }

    /**
     * Generate new MoveNodeDTO with san move data
     *
     * @return new MoveNodeDTO with san move data
     *
     * @throws NodesOverflowException if move count is too large
     */
    public MoveNodeDTO getRootNodeWithSan() {
        readLock.lock();
        try {
            Chessboard tempBoard = new Chessboard(this.startPositionFEN);
            tempBoard.gameVariants = this.getGameVariants();

            return buildPGNTreeWithSan(moveHistoryRoot, tempBoard, MAX_PGN_NODE_COUNT, 0);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Generate new MoveNodeDTO with san move data
     *
     * @param maxNodesCount max nodes count
     * @return new MoveNodeDTO with san move data
     *
     * @throws NodesOverflowException if move count is more than maxNodesCount
     */
    public MoveNodeDTO getRootNodeWithSan(int maxNodesCount) {
        readLock.lock();
        try {
            Chessboard tempBoard = new Chessboard(this.startPositionFEN);
            tempBoard.gameVariants = this.getGameVariants();

            return buildPGNTreeWithSan(moveHistoryRoot, tempBoard, maxNodesCount, 0);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get pgn string <p>
     * Warning : if this ChessGame is chess 960 and gameVariant is not standard, it's going to be overwritten. <br>
     * chess 960 = true, gameVariant = Crazyhouse, PGN header is [Variant "Crazyhouse"].
     *
     * @param maxNodes max nodes count
     * @return pgn string
     *
     * @throws NodesOverflowException if move count is more than <b>maxNodes</b>
     */
    public String getPGN(int maxNodes) {
        return PGNUtils.export(this, toPGNGame(maxNodes), false);
    }

    /**
     * Get pgn string <p>
     * Warning : if this ChessGame is chess 960 and gameVariant is not standard, it's going to be overwritten. <br>
     * chess 960 = true, gameVariant = Crazyhouse, PGN header is [Variant "Crazyhouse"].
     *
     * @return pgn string
     *
     * @throws NodesOverflowException if move count is too large (you can adjust by {@link #getPGN(int maxNodes)})
     */
    public String getPGN() {
        return getPGN(MAX_PGN_NODE_COUNT);
    }

    /**
     * Get pgn string with no extra commentary, clk, nag, etc. <p>
     *
     * Warning : if this ChessGame is chess 960 and gameVariant is not standard, it's going to be overwritten. <br>
     * chess 960 = true, gameVariant = Crazyhouse, PGN header is [Variant "Crazyhouse"].
     *
     * @return pgn string
     *
     * @throws NodesOverflowException if move count is too large (you can adjust by {@link #getPGN(int maxNodes)})
     */
    public String getPurePGN() {
        return getPurePGN(MAX_PGN_NODE_COUNT);
    }

    /**
     * Get pgn string with no extra commentary, clk, nag, etc. <p>
     *
     * Warning : if this ChessGame is chess 960 and gameVariant is not standard, it's going to be overwritten. <br>
     * chess 960 = true, gameVariant = Crazyhouse, PGN header is [Variant "Crazyhouse"].
     *
     * @param maxNodes max nodes count
     * @return pgn string
     *
     * @throws NodesOverflowException if move count is more than <b>maxNodes</b>
     */
    public String getPurePGN(int maxNodes) {
        return PGNUtils.export(this, toPGNGame(maxNodes), true);
    }

    /**
     * Get last main line node
     * <p>
     * Example : <br>
     * e4 e5 Nf3 Nc6 (Nf6 Nxe5) 'Bc4' <br>
     * and the result is Bc4
     *
     * @return last main line node
     */
    private MoveNode getLastMainlineNode() {
        readLock.lock();
        try {
            MoveNode lastNode = moveHistoryRoot;

            while (!lastNode.children.isEmpty()) {
                lastNode = lastNode.children.getFirst();
            }

            return lastNode;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get last main line node
     * <p>
     * Example : <br>
     * e4 e5 Nf3 Nc6 (Nf6 Nxe5) 'Bc4' <br>
     * and the result is Bc4
     *
     * @param startNode start root node
     *
     * @return last main line node
     */
    private MoveNode getLastMainlineNode(MoveNode startNode) {
        readLock.lock();
        try {
            MoveNode lastNode = startNode;

            while (!lastNode.children.isEmpty()) {
                lastNode = lastNode.children.getFirst();
            }

            return lastNode;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get mainline move data
     * <p>
     * Example : <br>
     * history -> <b>e4 e5 Nf3 (Nc3) Nc6</b> <br>
     * and the result is <br>
     * <b>e4 e5 Nf3 Nc6</b>
     */
    public List<MoveDataDTO> getMainlineData() {
        List<MoveDataDTO> result = new ArrayList<>();

        readLock.lock();
        try {
            Chessboard tempBoard = new Chessboard(startPositionFEN);

            MoveNode lastNode = moveHistoryRoot;

            while (!lastNode.children.isEmpty()) {
                lastNode = lastNode.children.getFirst();

                // make move
                MoveGenerator.makeMove(tempBoard, lastNode.moveData.originEncodedData());

                result.add(
                        new MoveDataDTO(
                                lastNode.id,
                                ChessboardUtils.getFen(tempBoard),
                                lastNode.moveData,
                                lastNode.annotation
                        )
                );
            }

            return result;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get game start position fen
     *
     * @return game start position fen
     */
    public String getStartPositionFEN() {
        readLock.lock();
        try {
            return startPositionFEN;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get whether this ChessGame is chess960
     *
     * @return whether this ChessGame is chess960
     */
    public boolean isChess960() {
        readLock.lock();
        try {
            return chessboard.isChess960;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get game variants
     *
     * @return game variants
     */
    public GameVariants getGameVariants() {
        readLock.lock();
        try {
            return chessboard.gameVariants;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Add chess game listener
     *
     * @param listener listener
     */
    public void addChessGameListener(ChessGameListener listener) {
        listeners.addIfAbsent(listener);
    }

    /**
     * Remove chess game listener
     *
     * @param listener listener
     */
    public void removeChessGameListener(ChessGameListener listener) {
        listeners.remove(listener);
    }

    /**
     * Notify listeners when move made
     *
     * @param moveInfo move data
     */
    private void notifyMoveMade(MoveInfo moveInfo) {
        for(ChessGameListener listener : listeners) {
            listener.onMoveMade(moveInfo);
        }
    }

    /**
     * Notify listeners when move unmade
     *
     * @param moveInfo unmade move data
     */
    private void notifyMoveUnmade(MoveInfo moveInfo) {
        for(ChessGameListener listener : listeners) {
            listener.onMoveUnmade(moveInfo);
        }
    }

    /**
     * Notify listeners when move remade
     *
     * @param moveInfo remade move data
     */
    private void notifyMoveRemade(MoveInfo moveInfo) {
        for(ChessGameListener listener : listeners) {
            listener.onMoveRemade(moveInfo);
        }
    }

    /**
     * Notify listeners when position jumped to node (pgn move)
     *
     * @param targetFen jumped position fen
     */
    private void notifyPositionJumped(String targetFen) {
        for (ChessGameListener listener : listeners) {
            listener.onPositionJumped(targetFen);
        }
    }

    /**
     * Notify listeners when game overed
     *
     * @param result game result
     * @param reason game over reason
     */
    private void notifyGameOver(GameResult result, GameOverReason reason) {
        for (ChessGameListener listener : listeners) {
            listener.onGameOver(result, reason);
        }
    }

    /**
     * Notify listeners when history changed
     */
    private void notifyHistoryChanged() {
        for (ChessGameListener listener : listeners) {
            listener.onHistoryChanged();
        }
    }

    public long getPolyglotHash() {
        readLock.lock();
        try {
            return PolyglotHashUtils.getPolyglotHash(this.chessboard);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get WDL result on this chess position <br>
     * not supports other variants
     *
     * @param tablebase table base class
     * @return WDL result
     */
    public int probeSyzygyWdl(SyzygyTablebase tablebase) throws IOException{
        if (getGameVariants() != GameVariants.STANDARD && isChess960())
            throw new VariantNotMatchException("Variant should be Standard chess or Chess 960!");

        readLock.lock();
        try {
            return tablebase.getWdlData(this.chessboard);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get DTZ result on this chess position <br>
     * not supports other variants
     *
     * @param tablebase table base class
     * @return DTZ result
     */
    public int probeSyzygyDtz(SyzygyTablebase tablebase) throws IOException {
        if (getGameVariants() != GameVariants.STANDARD && isChess960())
            throw new VariantNotMatchException("Variant should be Standard chess or Chess 960!");

        readLock.lock();
        try {
            return tablebase.getDtzData(this.chessboard);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get the best move based on Syzygy tablebase <br>
     * if is checkmate or stalemate, return null
     *
     * @param tablebase Syzygy tablebase
     * @return best move
     * @throws IOException if tablebase could not find or something
     */
    public MoveInfo findBestMoveSyzygy(SyzygyTablebase tablebase) throws IOException {
        List<SyzygyMoveDTO> bestMoves = findRankedSyzygyMoves(tablebase);
        if (bestMoves.isEmpty()) return null;
        return bestMoves.getFirst().move();
    }

    /**
     * Get Sorted moves based on Syzygy tablebase (first is best move, last is worst move)
     * <p>
     * WDL scale used here is -2~2 (Loss..Win), matching {@link SyzygyTablebase#getWdlData}.
     *
     * @param tablebase Syzygy table base
     * @return sorted moves list
     * @throws IOException if tablebase could not find or something
     */
    public List<SyzygyMoveDTO> findRankedSyzygyMoves(SyzygyTablebase tablebase) throws IOException {
        if (getGameVariants() != GameVariants.STANDARD && isChess960())
            throw new VariantNotMatchException("Variant should be Standard chess or Chess 960!");

        readLock.lock();
        try {
            int[] moveArray = new int[MoveCache.MAX_MOVE_SIZE];
            int moveCount = MoveGenerator.generateMoves(this.chessboard, moveArray);

            if (moveCount == 0) {
                return List.of(); // checkmate or stalemate
            }

            List<SyzygyMoveDTO> ranked = new ArrayList<>();
            int halfMoveClock = this.getHalfMove();

            for (int i = 0; i < moveCount; i++) {
                int move = moveArray[i];
                boolean zeroing = EncodeMove.getMoveCapture(move)
                        || EncodeMove.getMovePiece(move) == P
                        || EncodeMove.getMovePiece(move) == p;

                MoveGenerator.makeMove(this.chessboard, move);

                boolean triggersRepetition = ChessboardUtils.getRepetitionCount(this.chessboard, 2) >= 2;

                int childWdl = tablebase.getWdlData(this.chessboard);
                int ourWdl = triggersRepetition ? 0 : -childWdl;

                int distance = (ourWdl == 0) ? 0 : (zeroing ? 0 : Math.abs(tablebase.getDtzData(this.chessboard)));

                if (!zeroing && (halfMoveClock + distance >= 100)) {
                    if (ourWdl == 2) ourWdl = 1;
                    else if (ourWdl == -2) ourWdl = -1;
                }

                ranked.add(new SyzygyMoveDTO(new MoveInfo(move), ourWdl, distance));

                MoveGenerator.unmakeMove(this.chessboard, move);
            }

            ranked.sort((a, b) -> {
                if (a.ourWdl() != b.ourWdl()) {
                    return b.ourWdl() - a.ourWdl();
                }
                if (a.ourWdl() > 0) {
                    return a.distance() - b.distance();
                }
                if (a.ourWdl() < 0) {
                    return b.distance() - a.distance();
                }
                return 0;
            });

            return ranked;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Find tactics and return
     *
     * @param whiteAttacking is white attacking
     * @return tactics dto(s)
     */
    public List<TacticFinding> findTactics(boolean whiteAttacking) {
        return TacticAnalyzer.findAllTactics(this.chessboard, whiteAttacking);
    }

    /**
     * Find immediate tactics and return
     *
     * @param whiteAttacking is white attacking
     * @return tactics dto(s)
     */
    public List<TacticFinding> findImmediateTactics(boolean whiteAttacking) {
        return TacticAnalyzer.findImmediateThreats(this.chessboard, whiteAttacking);
    }


    /**
     * Get Hanging pieces square
     *
     * @param whiteAttacking if true, get black's hanging pieces. otherwise, get white's hanging pieces.
     * @return hanging pieces square
     */
    public List<Square> findHangingPieces(boolean whiteAttacking) {
        return ChessTacticUtils.findHangingPieces(this.chessboard, whiteAttacking);
    }

    /**
     * Get this board to ascii
     *
     * @return Ascii board
     */
    public String toAscii() {
        readLock.lock();
        try {
            return ChessboardUtils.toStringChessboard(this.chessboard);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Print this board ascii
     */
    public void printBoard() {
        System.out.println(this.toAscii());
    }

    /**
     * Print history with san
     *
     * @param rootNode root node
     * @param depth start depth
     */
    private void printHistory(MoveNodeDTO rootNode, int depth) {
        if(rootNode == null) return;

        boolean isCurrent = Objects.equals(this.getCurrentNodeId(), rootNode.id());
        String pointer = isCurrent ? " <-" : "";

        if (Objects.equals(rootNode.id(), this.moveHistoryRoot.id)) {
            System.out.println(pointer.trim());
        } else {
            String prefix = (depth > 0) ? "└ " : "";
            System.out.println(" ".repeat(depth) + prefix + rootNode.san() + pointer);
        }

        for(int i = 1; i < rootNode.children().size(); i++) {
            MoveNodeDTO child = rootNode.children().get(i);
            printHistory(child, depth + 1);
        }

        if (!rootNode.children().isEmpty()) {
            printHistory(rootNode.children().getFirst(), depth);
        }
    }

    /**
     * Print history
     *
     * @throws NodesOverflowException if move count is too large
     */
    public void printHistory() {
        readLock.lock();
        try {
            printHistory(getRootNodeWithSan(), 0);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Print history
     *
     * @throws NodesOverflowException if move count is more than maxNodeSize
     */
    public void printHistory(int maxNodeSize) {
        readLock.lock();
        try {
            printHistory(getRootNodeWithSan(maxNodeSize), 0);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Do perft test <br>
     * (JVM preheat included, using single thread, bulk counting is enabled by default)
     *
     * @param depth perft depth
     * @return perft result dto
     */
    public PerftResult perft(int depth) {
        return perft(depth, 1, false, true);
    }

    /**
     * Do perft test <br>
     * (using single thread, bulk counting is enabled by default)
     *
     * @param depth perft depth
     * @param concurrency using this amount of threads
     * @return perft result dto
     */
    public PerftResult perft(int depth, int concurrency) {
        return perft(depth, concurrency, false, true);
    }

    /**
     * Do perft test <br>
     * (bulk counting is enabled by default)
     *
     * @param depth perft depth
     * @param concurrency using this amount of threads
     * @param silent don't print any logs
     *
     * @return perft result dto
     */
    public PerftResult perft(int depth, int concurrency, boolean silent) {
        return PerftDriver.perftAPITest(this, depth, concurrency, silent, true);
    }

    /**
     * Do perft test
     *
     * @param depth perft depth
     * @param concurrency using this amount of threads
     * @param silent don't print any logs
     * @param bulkCounting do bulk counting
     *
     * @return perft result dto
     */
    public PerftResult perft(int depth, int concurrency, boolean silent, boolean bulkCounting) {
        return PerftDriver.perftAPITest(this, depth, concurrency, silent, bulkCounting);
    }

    /**
     * Return String FEN
     *
     * @return fen
     */
    @Override
    public String toString() {
        return this.getFEN();
    }

    /**
     * Get whether this Chessboard and obj is equal "position"
     *
     * @return whether this Chessboard and obj is equal "position"
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ChessGame other)) return false;
        return this.chessboard.hash_key == other.chessboard.hash_key;
    }

    /**
     * Hash key for deciding equal position
     *
     * @return hash key
     */
    @Override
    public int hashCode() {
        return Long.hashCode(this.chessboard.hash_key);
    }
}

package com.pepero.jcb.api;

import com.pepero.jcb.api.book.PolyglotHashUtils;
import com.pepero.jcb.api.dto.*;
import com.pepero.jcb.api.enums.*;
import com.pepero.jcb.api.exception.*;
import com.pepero.jcb.api.exception.type.FENErrorType;
import com.pepero.jcb.api.parse.ConvertStringMoveUtils;
import com.pepero.jcb.api.parse.FENValidator;
import com.pepero.jcb.core.bitboard.Attacks;
import com.pepero.jcb.core.bitboard.BitBoardUtils;
import com.pepero.jcb.core.constant.BoardSquares;
import com.pepero.jcb.core.constant.CastlingRights;
import com.pepero.jcb.core.constant.EncodedPieces;
import com.pepero.jcb.core.constant.MoveCache;
import com.pepero.jcb.core.*;
import com.pepero.jcb.core.encode.EncodeMove;
import com.pepero.jcb.api.perft.PerftDriver;
import com.pepero.jcb.api.perft.PerftResult;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.pepero.jcb.core.MoveGenerator.*;
import static com.pepero.jcb.core.constant.SideToMove.*;
import static com.pepero.jcb.core.constant.EncodedPieces.*;

/**
 * Chess board class storing tree history data, board state, etc.
 */
public class ChessGame {
    // start position constant
    public static final String START_POSITION = Chessboard.start_position;



    // Chessboard class
    private final Chessboard chessboard;

    private static final int[] PIECE_VALUES = new int[12];
    static {
        PIECE_VALUES[P] = 1;  PIECE_VALUES[p] = -1;
        PIECE_VALUES[N] = 3;  PIECE_VALUES[n] = -3;
        PIECE_VALUES[B] = 3;  PIECE_VALUES[b] = -3;
        PIECE_VALUES[R] = 5;  PIECE_VALUES[r] = -5;
        PIECE_VALUES[Q] = 9;  PIECE_VALUES[q] = -9;
        PIECE_VALUES[K] = 100; PIECE_VALUES[k] = -100;
    }

    /**
     * Max PGN Node count
     */
    private static final int MAX_PGN_NODE_COUNT = 2048;

    /**
     * Node counter for making node cache long id
     */
    private final AtomicLong nodeCounter = new AtomicLong(0L);

    /**
     * Node cache for going instantly to other node (Long id)
     */
    private LongObjectOpenHashMap<MoveNode> nodeCache = new LongObjectOpenHashMap<>();

    // multi-thread safe lock
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();

    /**
     * Move tree root <p>
     * ---> [root] - e4 - e5 - Nf3 ( - Nc3 - Nf6 ) - Nc6
     */
    private MoveNode moveHistoryRoot = new MoveNode(nodeCounter.incrementAndGet());

    /**
     * Current move node on {@link ChessGame#moveHistoryRoot}
     */
    private MoveNode currentNode = moveHistoryRoot;


    // game variables

    /**
     * Initial piece count <br>
     * The piece type index on {@link EncodedPieces}
     */
    private static final int[] initialPieceCounts = new int[]{
            8, // White pawn
            2, // White knight
            2, // White bishop
            2, // White rook
            1, // White queen
            1, // White king

            8, // Black pawn
            2, // Black knight
            2, // Black bishop
            2, // Black rook
            1, // Black queen
            1, // Black king
    };

    /**
     * PGN headers
     */
    private final LinkedHashMap<String, String> headers = new LinkedHashMap<>();

    /**
     * Game result variable <br>
     * The game result variable types can be found on {@link GameResult} <p>
     *
     * This game result variable doesn't update when the result of this game is already finished. <br>
     * Example : e4 e5 Qh5 Nc6 Bc4 Nf6 Qxf7#, and if undo it, the game result doesn't change. but the
     * {@link #isCheckmate()} changes. <br>
     * You can get this value on {@link ChessGame#getGameResult()}
     */
    private GameResult gameResult = GameResult.UNKNOWN;

    /**
     * Game over reason variable for checking why this game finished <br>
     * The game over reason types can be found on {@link GameOverReason} <p>
     *
     * This game over reason variable doesn't update when the result of this game is already finished. <br>
     * Example : e4 e5 Qh5 Nc6 Bc4 Nf6 Qxf7#, and if undo it, the game over reason doesn't change. but the
     * {@link #isCheckmate()} changes. <br>
     * You can get this value on {@link #getGameoverReason()}
     */
    private GameOverReason gameoverReason = GameOverReason.NOTGAMEOVER;

    /**
     * Start position FEN <br>
     * this position fen can be reset on {@link #fromFEN(String fen)} methods.
     */
    private String startPositionFEN;

    /**
     * Chess game listeners
     */
    private final CopyOnWriteArrayList<ChessGameListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Initialize position with PGN string
     *
     * @param pgn pgn string
     * @throws NodesOverflowException if move count is too large (you can adjust by {@link #fromPGN(String, int maxNodesCount)})
     */
    public static ChessGame fromPGN(String pgn) {
        ChessGame result = new ChessGame(false, GameVariant.STANDARD);
        result.loadPGN(pgn);
        return result;
    }

    /**
     * Initialize position with PGN string
     *
     * @param pgn pgn string
     * @param maxNodesCount max nodes count
     *
     * @throws NodesOverflowException if move count is more than maxNodesCount
     */
    public static ChessGame fromPGN(String pgn, int maxNodesCount) {
        ChessGame result = new ChessGame(false, GameVariant.STANDARD);
        result.loadPGN(pgn, maxNodesCount);
        return result;
    }

    /**
     * Initialize position with FEN string
     *
     * @param fen fen string
     * @param gameVariant game variant ( standard, crazyhouse ... ) <br>
     *                     you can get this variant type on {@link GameVariant}
     *
     * @throws FENConvertException if converting fen string failed
     */
    public static ChessGame fromFEN(String fen, GameVariant gameVariant) {
        return new ChessGame(fen, false, gameVariant);
    }

    /**
     * Initialize position with FEN string
     *
     * @param fen fen string
     * @param isChess960 is Chess 960 variant <br>
     *                   if chess 960 variant is true, the castling LAN(UCI) is a little different. <br>
     *                   the standard chess is 'e1g1', but if chess960, it's going to be 'e1h1' on standard position
     *
     * @throws FENConvertException if converting fen string failed
     */
    public static ChessGame fromFEN(String fen, boolean isChess960) {
        return new ChessGame(fen, isChess960, GameVariant.STANDARD);
    }

    /**
     * Initialize position with FEN string
     *
     * @param fen fen string
     * @param isChess960 is Chess 960 variant <br>
     *                   if chess 960 variant is true, the castling LAN(UCI) is a little different. <br>
     *                   the standard chess is 'e1g1', but if chess960, it's going to be 'e1h1' on standard position
     * @param gameVariant game variant ( standard, crazyhouse ... ) <br>
     *                     you can get this variant type on {@link GameVariant}
     *
     * @throws FENConvertException if converting fen string failed
     */
    public static ChessGame fromFEN(String fen, boolean isChess960, GameVariant gameVariant) {
        return new ChessGame(fen, isChess960, gameVariant);
    }

    /**
     * Initialize position with FEN string
     *
     * @param fen fen string
     *
     * @throws FENConvertException if converting fen string failed
     */
    public static ChessGame fromFEN(String fen) {
        return fromFEN(fen, GameVariant.STANDARD);
    }

    /**
     * Initialize position with {@link Chessboard} class
     *
     * @param chessboard chess board class
     */
    public static ChessGame fromChessboard(Chessboard chessboard) {
        return new ChessGame(chessboard);
    }

    /**
     * Initialize position to start position <br>
     * Note that the start position constant on {@link #START_POSITION}
     * or if you want to get other start position fen like {@link Chessboard#racing_kings_start_position},
     * go to {@link Chessboard}
     */
    public static ChessGame startPosition() {
        return new ChessGame(false, GameVariant.STANDARD);
    }

    /**
     * Initialize position to start position  <br>
     * Note that the start position constant on {@link #START_POSITION}
     * or if you want to get other start position fen like {@link Chessboard#racing_kings_start_position},
     * go to {@link Chessboard}
     *
     * @param isChess960 is Chess 960 variant <br>
     *                   if chess 960 variant is true, the castling LAN(UCI) is a little different. <br>
     *                   the standard chess is 'e1g1', but if chess960, it's going to be 'e1h1' on standard position
     */
    public static ChessGame startPosition(boolean isChess960) {
        return new ChessGame(isChess960, GameVariant.STANDARD);
    }

    /**
     * Initialize position to start position <br>
     * this method automatically set the right start position for <b>gameVariant</b>
     *
     * @param gameVariant game variant ( standard, crazyhouse ... ) <br>
     *                     you can get this variant type on {@link GameVariant}
     */
    public static ChessGame startPosition(GameVariant gameVariant) {
        return new ChessGame(false, gameVariant);
    }

    /**
     * Initialize position to start position <br>
     * this method automatically set the right start position for <b>gameVariant</b>
     *
     * @param isChess960 is Chess 960 variant <br>
     *                   if chess 960 variant is true, the castling LAN(UCI) is a little different. <br>
     *                   the standard chess is 'e1g1', but if chess960, it's going to be 'e1h1' on standard position
     * @param gameVariant game variant ( standard, crazyhouse ... ) <br>
     *                     you can get this variant type on {@link GameVariant}
     */
    public static ChessGame startPosition(boolean isChess960, GameVariant gameVariant) {
        return new ChessGame(isChess960, gameVariant);
    }

    /**
     * Lightweight copy constructor <br>
     * <b>Warning : This doesn't copy event listener and history tree but the position of this ChessGame</b>
     *
     * @param other ChessGame class to copy
     */
    public static ChessGame lightWeightCopy(ChessGame other) {
        return new ChessGame(other);
    }

    /**
     * Initialize position with FEN string
     *
     * @param fen fen string
     * @param isChess960 is Chess 960 variant <br>
     *                   if chess 960 variant is true, the castling LAN(UCI) is a little different. <br>
     *                   the standard chess is 'e1g1', but if chess960, it's going to be 'e1h1' on standard position
     * @param gameVariant game variant ( standard, crazyhouse ... ) <br>
     *                     you can get this variant type on {@link GameVariant}
     *
     * @throws FENConvertException if converting fen string failed
     */
    private ChessGame(String fen, boolean isChess960, GameVariant gameVariant) {
        FENValidator.validateString(fen, isChess960, gameVariant);

        chessboard = new Chessboard();
        startPositionFEN = fen;

        chessboard.isChess960 = isChess960;
        chessboard.gameVariant = gameVariant;

        try {
            ChessboardUtils.parseFen(this.chessboard, fen);
        } catch (Exception e) {
            throw new FENConvertException("Could not parse the fen.", FENErrorType.UNKNOWN);
        }

        FENValidator.validateLogicalState(chessboard, gameVariant);

        nodeCache.put(moveHistoryRoot.id, moveHistoryRoot);
    }

    /**
     * Initialize position to start position <br>
     * this method automatically set the right start position for <b>gameVariant</b>
     *
     * @param isChess960 is Chess 960 variant <br>
     *                   if chess 960 variant is true, the castling LAN(UCI) is a little different. <br>
     *                   the standard chess is 'e1g1', but if chess960, it's going to be 'e1h1' on standard position
     * @param gameVariant game variant ( standard, crazyhouse ... ) <br>
     *                     you can get this variant type on {@link GameVariant}
     */
    private ChessGame(boolean isChess960, GameVariant gameVariant) {
        this.chessboard = new Chessboard();
        this.chessboard.isChess960 = isChess960;
        this.chessboard.gameVariant = gameVariant;

        String startFen = ChessboardUtils.getDefaultStartPosition(gameVariant);
        ChessboardUtils.parseFen(this.chessboard, startFen);

        startPositionFEN = startFen;

        nodeCache.put(moveHistoryRoot.id, moveHistoryRoot);
    }

    /**
     * Lightweight copy constructor <br>
     * <b>Warning : This doesn't copy event listener and history tree but the position of this ChessGame</b>
     *
     * @param other ChessGame class to copy
     */
    private ChessGame(ChessGame other) {
        other.readLock.lock();
        writeLock.lock();
        try {
            this.chessboard = new Chessboard(other.chessboard);
            this.startPositionFEN = other.startPositionFEN;

            this.moveHistoryRoot = new MoveNode(nodeCounter.incrementAndGet());
            this.currentNode = this.moveHistoryRoot;
            this.nodeCache.put(this.moveHistoryRoot.id, this.moveHistoryRoot);

            this.gameResult = other.gameResult;
            this.gameoverReason = other.gameoverReason;

            this.headers.clear();
            this.headers.putAll(other.headers);
        } finally {
            other.readLock.unlock();
            writeLock.unlock();
        }
    }

    /**
     * Initialize position with {@link Chessboard} class <br>
     * Copies the original {@link Chessboard} class. <br>
     * and this method doesn't get the history on {@link Chessboard}
     *
     * @param chessboard chess board class
     */
    private ChessGame(Chessboard chessboard) {
        writeLock.lock();
        try {
            this.chessboard = new Chessboard(chessboard);
            this.startPositionFEN = ChessboardUtils.getFen(chessboard);

            this.moveHistoryRoot = new MoveNode(nodeCounter.incrementAndGet());
            this.currentNode = this.moveHistoryRoot;
            this.nodeCache.put(this.moveHistoryRoot.id, this.moveHistoryRoot);

            setDefaultHeaders();
        } finally {
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
     * Get FEN on this ChessGame <p>
     *
     * This fen printing dialect default is lichess, <p>
     *
     * example : the 3 check lichess dialect fen is <br>
     * <b>"r1bqkbnr/pp1ppppp/2n5/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 3+3 2 3"</b> <br>
     * but the 3 check fairy stockfish dialect fen is <br>
     * <b>"r1bqkbnr/pp1ppppp/2n5/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3 +0+0"</b>
     *
     * @return fen (lichess dialect) if you want to change dialect, go to {@link #getFEN(FENDialect)}
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
     * Get FEN on this ChessGame with dialect variable <p>
     *
     * example : the 3 check lichess dialect fen is <br>
     * <b>"r1bqkbnr/pp1ppppp/2n5/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 3+3 2 3"</b> <br>
     * but the 3 check fairy stockfish dialect fen is <br>
     * <b>"r1bqkbnr/pp1ppppp/2n5/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3 +0+0"</b>
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
     * Make move for internal make move methods <p>
     *
     * if you want to know what's the <b>encodedMove</b>, go to {@link EncodeMove}
     *
     * @param encodedMove the move encoded as an integer (contains source, target, flags, etc.)
     *
     * @param originalMoveString original move string for error message (null allowed)
     */
    private void internalMakeMove(int encodedMove, String originalMoveString) {
        MoveInfo moveData;
        GameResult gameResult;
        boolean historyChanged;

        if (!ChessboardUtils.isLegalMove(this.chessboard, encodedMove)) {
            String errorStr = (originalMoveString != null) ? originalMoveString : new MoveInfo(encodedMove).toLanString();
            throw new IllegalMoveException(errorStr, this.getFEN());
        }

        MoveGenerator.makeMove(this.chessboard, encodedMove);
        moveData = new MoveInfo(encodedMove);
        historyChanged = addMoveHistory(moveData);

        gameResult = evaluateGameState(currentNode);

        notifyMoveMade(moveData);
        if (gameResult != GameResult.UNKNOWN) {
            notifyGameOver(this.gameResult, this.gameoverReason);
        }
        if (historyChanged) {
            notifyHistoryChanged();
        }
    }

    /**
     * Make move for internal make move methods <br>
     * this method doesn't check whether this is a legal move or not <p>
     *
     * if you want to know what's the <b>encodedMove</b>, go to {@link EncodeMove}
     *
     * @param encodedMove the move encoded as an integer (contains source, target, flags, etc.)
     *
     */
    private void internalMakeMoveValidated(int encodedMove) {
        MoveInfo moveData;
        GameResult gameResult;
        boolean historyChanged;

        MoveGenerator.makeMove(this.chessboard, encodedMove);
        moveData = new MoveInfo(encodedMove);
        historyChanged = addMoveHistory(moveData);

        gameResult = evaluateGameState(currentNode);

        notifyMoveMade(moveData);
        if (gameResult != GameResult.UNKNOWN) {
            notifyGameOver(this.gameResult, this.gameoverReason);
        }
        if (historyChanged) {
            notifyHistoryChanged();
        }
    }

    /**
     * Make move on this ChessGame
     *
     * @param lan move like e2e4, e7e5 (LAN (or UCI) move string)
     *
     * @throws IllegalMoveException if move is illegal move
     * @throws ConvertMoveException if move string is incorrect
     */
    public void makeMove(String lan) {
        if(lan == null) throw new NullPointerException("Lan (or uci) data can not be null!");

        writeLock.lock();
        try {
            int encodedMove = ConvertStringMoveUtils.lanToMoveData(this.chessboard, lan);
            internalMakeMove(encodedMove, lan);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Make a move on this ChessGame and return converted san move
     *
     * @param lan move like "e2e4", "e7e5" (LAN (or UCI) move string)
     * @return converted san move
     *
     * @throws IllegalMoveException if move is illegal move
     * @throws ConvertMoveException if move string is incorrect
     */
    public String makeMoveReturningSan(String lan) {
        if(lan == null) throw new NullPointerException("Lan (or uci) data can not be null!");

        writeLock.lock();
        try {
            int encodedMove = ConvertStringMoveUtils.lanToMoveData(chessboard, lan);
            String san = ConvertStringMoveUtils.toSanString(chessboard, encodedMove);
            internalMakeMoveValidated(encodedMove);
            return san;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Make a move on this ChessGame (San string)
     *
     * @param sanString san string
     *
     * @throws IllegalMoveException if move is illegal move
     * @throws ConvertMoveException if move string is incorrect
     */
    public void makeMoveSan(String sanString) {
        if(sanString == null) throw new NullPointerException("San data can not be null!");

        makeMove(toLanString(sanString));
    }

    /**
     * Make a move on this ChessGame (ENCODED MOVE)<p>
     *
     * if you want to know what's the <b>encodedMove</b>, go to {@link EncodeMove}
     *
     * @param encodedMove the move encoded as an integer (contains source, target, flags, etc.)
     *
     * @throws IllegalMoveException if move is illegal move
     * @throws ConvertMoveException if move data is not correct
     */
    public void makeMove(int encodedMove) {
        writeLock.lock();
        try {
            internalMakeMove(encodedMove, null);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Make moves on this ChessGame (San string)
     *
     * @param sanString san string like "e4 e5 Nf3 Nc6"
     *
     * @throws IllegalMoveException if move is illegal move
     * @throws ConvertMoveException if move data is not correct
     */
    public void makeMoveSanAll(String sanString) {
        if(sanString == null) throw new NullPointerException("San string can not be null!");

        writeLock.lock();
        try {
            sanString = sanString.trim();
            String[] sanStrings = sanString.split(" ");

            Chessboard tempChessboard = new Chessboard(this.chessboard);
            int[] encodedMoves = new int[sanStrings.length];

            for (int i = 0; i < sanStrings.length; i++) {
                int encodedMove = ConvertStringMoveUtils.sanToMoveData(tempChessboard, sanStrings[i]);
                if (!MoveGenerator.isLegalMove(tempChessboard, encodedMove)) {
                    throw new IllegalMoveException(sanStrings[i], ChessboardUtils.getFen(tempChessboard));
                }
                MoveGenerator.makeMove(tempChessboard, encodedMove);
                encodedMoves[i] = encodedMove;
            }

            for (int encodedMove : encodedMoves) {
                internalMakeMoveValidated(encodedMove);
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
     * @throws IllegalMoveException if move is illegal move
     * @throws ConvertMoveException if move data is not correct
     */
    public void makeMoveAll(String lanString) {
        if(lanString == null) throw new NullPointerException("Lan string can not be null!");

        writeLock.lock();
        try {
            lanString = lanString.trim();
            String[] lanStrings = lanString.split(" ");

            Chessboard tempChessboard = new Chessboard(this.chessboard);
            int[] encodedMoves = new int[lanStrings.length];

            for (int i = 0; i < lanStrings.length; i++) {
                int encodedMove = ConvertStringMoveUtils.sanToMoveData(tempChessboard, lanStrings[i]);
                if(!MoveGenerator.isLegalMove(tempChessboard, encodedMove)) {
                    throw new IllegalMoveException(lanStrings[i],
                            ChessboardUtils.getFen(tempChessboard));
                }
                MoveGenerator.makeMove(tempChessboard, encodedMove);
                encodedMoves[i] = encodedMove;
            }

            for (int encodedMove : encodedMoves) {
                internalMakeMoveValidated(encodedMove);
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Make move for internal make move raw methods<p>
     *
     * if you want to know what's the <b>encodedMove</b>, go to {@link EncodeMove}
     *
     * @param encodedMove the move encoded as an integer (contains source, target, flags, etc.)
     */
    private void internalMakeMoveRaw(int encodedMove) {
        writeLock.lock();
        try {
            if(!ChessboardUtils.isLegalMove(this.chessboard, encodedMove)) {
                throw new IllegalMoveException(EncodeMove.moveToString(encodedMove),
                        this.getFEN());
            }

            MoveGenerator.makeMove(this.chessboard, encodedMove);
        } finally {
            writeLock.unlock();
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
     * Try to make move on this ChessGame without throwing an exception <p>
     *
     * if you want to know what's the <b>encodedMove</b>, go to {@link EncodeMove}
     *
     * @param encodedMove the move encoded as an integer (contains source, target, flags, etc.)
     *
     * @return true if the move was legal and applied, false otherwise
     */
    public boolean tryMakeMoveRaw(int encodedMove) {
        try {
            makeMoveRaw(encodedMove);
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
        if(lanMove == null) throw new NullPointerException("Lan string can not be null!");

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
        if(moveString == null) throw new NullPointerException("Lan string can not be null!");

        int encodedMove = ConvertStringMoveUtils.lanToMoveData(this.chessboard, moveString);
        internalMakeMoveRaw(encodedMove);
    }

    /**
     * Make move on this ChessGame <br>
     * <b>Warning : This raw method doesn't update history, call listener, and update game over variable. </b><p>
     *
     * if you want to know what's the <b>encodedMove</b>, go to {@link EncodeMove}
     *
     * @param encodedMove the move encoded as an integer (contains source, target, flags, etc.)
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
     * <b>Warning : This method doesn't update history, call listener, and update game over variable. </b><p>
     *
     * if you want to know what's the <b>encodedMove</b>, go to {@link EncodeMove}
     *
     * @param encodedMove the move encoded as an integer (contains source, target, flags, etc.)
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
     * @param sourceSquare Source square
     * @param targetSquare Target square
     * @param promotionType Promotion type like queen, rook, bishop and knight ({@link PieceType#QUEEN}, {@link PieceType#ROOK} ... )
     *
     * @throws IllegalMoveException if move is illegal move
     * @throws ConvertMoveException if move data is not correct
     */
    public void makeMove(Square sourceSquare, Square targetSquare, PieceType promotionType) {
        Objects.requireNonNull(sourceSquare, "The source square can not be null!");
        Objects.requireNonNull(targetSquare, "The target square can not be null!");
        Objects.requireNonNull(promotionType, "The promotion type can not be null!");

        if(promotionType != PieceType.NONE && promotionType != PieceType.QUEEN && promotionType != PieceType.ROOK &&
                promotionType != PieceType.BISHOP && promotionType != PieceType.KNIGHT) {
            throw new IllegalMoveException("Promotion Piece type is unknown! please use like PieceType.QUEEN, PieceType.ROOK", this.getFEN());
        }

        writeLock.lock();
        try {
            int encodedMove;
            encodedMove = ConvertStringMoveUtils.parseMoveDataToEncodedMove(
                    this.chessboard, sourceSquare.getIndex(), targetSquare.getIndex(), promotionType.getPieceType()
            );
            internalMakeMoveValidated(encodedMove);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Make move on this ChessGame (Source square, Target square)
     *
     * @param sourceSquare Source square (you can make square on BoardSquares.java)
     * @param targetSquare Target square (you can make square on BoardSquares.java)
     *
     * @throws IllegalMoveException if move is illegal move
     * @throws ConvertMoveException if move data is not correct
     */
    public void makeMove(Square sourceSquare, Square targetSquare) {
        makeMove(sourceSquare, targetSquare, PieceType.NONE);
    }

    /**
     * Make move on this ChessGame (MoveInfo)
     *
     * @param moveInfo move data
     *
     * @throws IllegalMoveException if move is illegal move
     */
    public void makeMove(MoveInfo moveInfo) {
        writeLock.lock();
        try {
            internalMakeMove(moveInfo.originEncodedData(), moveInfo.toLanString());
        } finally {
            writeLock.unlock();
        }
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
     *
     * @throws ConvertMoveException if converting move failed
     */
    public boolean tryMakeMoveSan(String sanString) {
        if(sanString == null) throw new NullPointerException("San string can not be null!");

        try {
            makeMoveSan(sanString);
            return true;
        } catch (IllegalMoveException e) {
            return false;
        }
    }

    /**
     * Try to make a move on this ChessGame without throwing an exception<p>
     *
     * if you want to know what's the <b>encodedMove</b>, go to {@link EncodeMove}
     *
     * @param encodedMove the move encoded as an integer (contains source, target, flags, etc.)
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
        if(sanString == null) throw new NullPointerException("San string can not be null!");

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
     *
     * @throws ConvertMoveException if converting move failed
     */
    public boolean tryMakeMoveAll(String lanString) {
        if(lanString == null) throw new NullPointerException("Lan string can not be null!");

        try {
            makeMoveAll(lanString);
            return true;
        } catch (IllegalMoveException e) {
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
     * @throws EmptyMoveUndoException if move history is empty and unmake move
     */
    public MoveInfo unmakeMove() {
        MoveInfo moveInfo;
        GameResult gameResult;

        writeLock.lock();
        try {
            if (!canUndo()) throw new EmptyMoveUndoException();

            moveInfo = currentNode.moveData;
            currentNode = currentNode.parent;

            MoveGenerator.unmakeMove(this.chessboard, moveInfo.originEncodedData());

            gameResult = evaluateGameState(currentNode);
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
     * @throws EmptyMoveRedoException if redo history is empty and remake move
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
     * @throws EmptyMoveRedoException if redo history is empty and remake move
     */
    public MoveInfo remakeMove(int variationIndex) {
        MoveInfo moveInfo;
        GameResult gameResult;

        writeLock.lock();
        try {
            if (!canRedo()) throw new EmptyMoveRedoException();
            if(currentNode.children.size() <= variationIndex) throw new VariationNotFoundException();

            currentNode = currentNode.children.get(variationIndex);
            moveInfo = currentNode.moveData;

            MoveGenerator.makeMove(this.chessboard, moveInfo.originEncodedData());

            gameResult = evaluateGameState(currentNode);
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
     * @throws MoveNotFoundException if current node (move) not found
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
     *
     * @throws EmptyMoveRedoException if move to go forward not found
     */
    public MoveInfo goForward() {
        if (canRedo()) return remakeMove();
        return null;
    }

    /**
     * Go backward on history (mainline)
     *
     * @return undid move info (if undoing move failed, returns null)
     *
     * @throws EmptyMoveUndoException if move to go backward not found
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
     * Get white turn
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
     * Get captured piece <br>
     * You can also get pocket data on CrazyHouse variant
     *
     * @param isWhite if white, returns black captured piece. if black, returns white captured piece.
     */
    public Map<PieceType, Integer> getCapturedPieces(boolean isWhite) {
        readLock.lock();
        try {
            Map<PieceType, Integer> captured = new EnumMap<>(PieceType.class);
            if(getGameVariant() == GameVariant.CRAZY_HOUSE) {
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
            if (this.chessboard.gameVariant == GameVariant.CRAZY_HOUSE) {
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
     * Get white pieces count
     */
    public int getWhitePieceCount() {
        return BitBoardUtils.countBits(chessboard.occupancies[white]);
    }

    /**
     * Get black pieces count
     */
    public int getBlackPieceCount() {
        return BitBoardUtils.countBits(chessboard.occupancies[black]);
    }

    /**
     * Get pieces count
     */
    public int getPieceCount() {
        return BitBoardUtils.countBits(chessboard.occupancies[both]);
    }

    /**
     * Get piece type on square
     * if not found, returns NONE
     *
     * @param square square
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
     * Example : chessboard = start pos, source = e2, returns e2e3, e2e4
     */
    public List<MoveInfo> getLegalMovesForSource(Square source) {
        Objects.requireNonNull(source, "Source Square is null!");

        writeLock.lock();
        try {
            int[] move_list = MoveCache.CHESSGAME_MOVE_CACHE.get();
            int move_count = generateMoves(chessboard, move_list);
            List<MoveInfo> result = new ArrayList<>(move_count);

            for (int count = 0; count < move_count; count++){
                int encodedMove = move_list[count];
                if(EncodeMove.getMoveSource(encodedMove) != source.getIndex()) continue;
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
     * Example : chessboard = start pos, target = e4, returns e2e3, e2e4
     *
     * @return generated move
     */
    public List<MoveInfo> getLegalMovesForTarget(Square target) {
        Objects.requireNonNull(target, "Target Square is null!");

        writeLock.lock();
        try {
            int[] move_list = MoveCache.CHESSGAME_MOVE_CACHE.get();
            int move_count = generateMoves(chessboard, move_list);
            List<MoveInfo> result = new ArrayList<>(move_count);

            for (int count = 0; count < move_count; count++){
                int encodedMove = move_list[count];
                if(EncodeMove.getMoveTarget(encodedMove) != target.getIndex()) continue;
                result.add(new MoveInfo(encodedMove));
            }
            return result;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Get board state Map(square)(piece)
     */
    public Map<Square, Piece> getBoardStateMap() {
        readLock.lock();
        try {
            Map<Square, Piece> result = new EnumMap<>(Square.class);

            for(Square square : Square.values()) {
                int piece_type = ChessboardUtils.getPieceTypeOnSquare(this.chessboard, square.getIndex());
                if (piece_type != -1) {
                    result.put(square, Piece.fromIndex(piece_type));
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
     */
    public boolean isEmpty(Square square) {
        readLock.lock();
        try {
            return ChessboardUtils.getPieceTypeOnSquare(this.chessboard, square.getIndex()) == -1;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get whether white has king side castling right
     */
    public boolean hasWhiteKingSideCastling() {
        readLock.lock();
        try {
            return (chessboard.castle & CastlingRights.WK) != 0;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get whether white has queen side castling right
     */
    public boolean hasWhiteQueenSideCastling() {
        readLock.lock();
        try {
            return (chessboard.castle & CastlingRights.WQ) != 0;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get whether black has king side castling right
     */
    public boolean hasBlackKingSideCastling() {
        readLock.lock();
        try {
            return (chessboard.castle & CastlingRights.BK) != 0;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get whether black has queen side castling right
     */
    public boolean hasBlackQueenSideCastling() {
        readLock.lock();
        try {
            return (chessboard.castle & CastlingRights.BQ) != 0;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get whether white has any castling rights
     */
    public boolean hasWhiteCastling() {
        readLock.lock();
        try {
            return hasWhiteKingSideCastling() || hasWhiteQueenSideCastling();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get whether black has any castling rights
     */
    public boolean hasBlackCastling() {
        readLock.lock();
        try {
            return hasBlackKingSideCastling() || hasBlackQueenSideCastling();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get whether this position has any castling rights
     */
    public boolean hasCastling() {
        readLock.lock();
        try {
            return (chessboard.castle) != 0;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get castling rights info
     */
    public CastlingRightsInfo getCastlingRights() {
        readLock.lock();
        try {
            return new CastlingRightsInfo(
                    (chessboard.castle & CastlingRights.WK) != 0,
                    (chessboard.castle & CastlingRights.WQ) != 0,
                    (chessboard.castle & CastlingRights.BK) != 0,
                    (chessboard.castle & CastlingRights.BQ) != 0
            );
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get whether the king is under attack
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
     * Get checking piece (king attacker) <br>
     * The max size of this return list is 2.
     */
    public List<Square> getChecker() {
        readLock.lock();
        try {
            List<Square> checker = new ArrayList<>();

            int oppSide = chessboard.side ^ 1;

            int kingSquare = BitBoardUtils.getLS1BIndex(
                    chessboard.side == white ? chessboard.bitboards[K] : chessboard.bitboards[k]);

            // get all checker
            long checkersMask = 0L;

            // pawn
            if (oppSide == white)  {
                checkersMask |= Attacks.pawn_attacks[black][kingSquare] & chessboard.bitboards[P];
            } else {
                checkersMask |= Attacks.pawn_attacks[white][kingSquare] & chessboard.bitboards[p];
            }

            // knight
            checkersMask |= Attacks.knight_attacks[kingSquare] &
                    (oppSide == white ? chessboard.bitboards[N] : chessboard.bitboards[n]);

            // bishop
            checkersMask |= Attacks.getBishopAttacks(kingSquare, chessboard.occupancies[both]) &
                    (oppSide == white ? (chessboard.bitboards[B] | chessboard.bitboards[Q]) :
                            (chessboard.bitboards[b] | chessboard.bitboards[q]));

            // rook
            checkersMask |= Attacks.getRookAttacks(kingSquare, chessboard.occupancies[both]) &
                    (oppSide == white ? (chessboard.bitboards[R] | chessboard.bitboards[Q]) :
                            (chessboard.bitboards[r] | chessboard.bitboards[q]));

            // queen is already contained

            while (checkersMask != 0L) {
                int square = BitBoardUtils.getLS1BIndex(checkersMask);
                checker.add(Square.fromIndex(square));
                checkersMask &= 1L << square;
            }

            return checker;
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
     * @throws VariantNotMatchException if this ChessGame isn't Three check variant
     */
    public boolean isThreeChecked() {
        if(chessboard.gameVariant != GameVariant.THREE_CHECK) throw new VariantNotMatchException(
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
     * @throws VariantNotMatchException if this ChessGame isn't King of the hill variant
     */
    public boolean isKingGoneToHill() {
        if(chessboard.gameVariant != GameVariant.KING_OF_THE_HILL) throw new VariantNotMatchException(
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
     * @throws VariantNotMatchException if this ChessGame isn't Horde variant
     */
    public boolean isHordePiecesGone() {
        if(chessboard.gameVariant != GameVariant.HORDE) throw new VariantNotMatchException(
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
     * Get whether this Giveaway position overed
     *
     * @throws VariantNotMatchException if this ChessGame isn't Giveaway variant
     */
    public boolean isGiveawayOver() {
        if(chessboard.gameVariant != GameVariant.GIVEAWAY) throw new VariantNotMatchException(
                "The variant should be Giveaway!"
        );

        readLock.lock();
        try {
            return ChessboardUtils.isGiveawayOver(chessboard);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get whether this Suicide position overed
     *
     * @throws VariantNotMatchException if this ChessGame isn't a Suicide variant
     */
    public boolean isSuicideOver() {
        if(chessboard.gameVariant != GameVariant.SUICIDE) throw new VariantNotMatchException(
                "The variant should be Suicide!"
        );

        readLock.lock();
        try {
            return ChessboardUtils.isSuicideOver(chessboard);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get whether this atomic position overed
     *
     * @throws VariantNotMatchException if this ChessGame isn't an Atomic variant
     */
    public boolean isAtomicOver() {
        if(chessboard.gameVariant != GameVariant.ATOMIC) throw new VariantNotMatchException(
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
     * @throws VariantNotMatchException if this ChessGame isn't racing kings variant
     */
    public boolean isKingRaceOver() {
        if(chessboard.gameVariant != GameVariant.RACING_KINGS) throw new VariantNotMatchException(
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

    /**
     * Get whether this position is insufficient material
     */
    public boolean isInsufficientMaterial() {
        readLock.lock();
        try {
            if (chessboard.gameVariant == GameVariant.GIVEAWAY
                    || chessboard.gameVariant == GameVariant.SUICIDE
                    || chessboard.gameVariant == GameVariant.ATOMIC
                    || chessboard.gameVariant == GameVariant.THREE_CHECK
                    || chessboard.gameVariant == GameVariant.KING_OF_THE_HILL
                    || chessboard.gameVariant == GameVariant.RACING_KINGS
                    || chessboard.gameVariant == GameVariant.HORDE) {
                return false;
            }

            if (this.chessboard.gameVariant == GameVariant.CRAZY_HOUSE) {
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
            if(chessboard.gameVariant == GameVariant.GIVEAWAY) {
                if(isGiveawayOver()) return GameOverReason.GIVEAWAY;
            }
            if(chessboard.gameVariant == GameVariant.SUICIDE) {
                if(isSuicideOver()) return GameOverReason.SUICIDE;
            }
            if(chessboard.gameVariant == GameVariant.ATOMIC) {
                if(isAtomicOver()) return GameOverReason.ATOMIC;
            }
            if(chessboard.gameVariant == GameVariant.THREE_CHECK) {
                if(isThreeChecked()) return GameOverReason.THREE_CHECK;
            }
            if(chessboard.gameVariant == GameVariant.KING_OF_THE_HILL) {
                if(isKingGoneToHill()) return GameOverReason.KING_OF_THE_HILL;
            }
            if(chessboard.gameVariant == GameVariant.HORDE) {
                if(isHordePiecesGone()) return GameOverReason.HORDE;
            }
            if(chessboard.gameVariant == GameVariant.RACING_KINGS) {
                if(isKingRaceOver()) return GameOverReason.KING_RACE;
            }

            int repetitionCount = ChessboardUtils.getRepetitionCount(this.chessboard, 5);

            if (includeClaimableDraws) {
                if (repetitionCount >= 3) return GameOverReason.THREEFOLD_CLAIM;
                if (canClaimFiftyMoves()) return GameOverReason.FIFTYMOVES_CLAIM;
            }

            if(chessboard.gameVariant != GameVariant.GIVEAWAY && chessboard.gameVariant != GameVariant.SUICIDE) {
                boolean inCheck = isCheck();

                if (inCheck) {
                    if (isCheckmate()) return GameOverReason.CHECKMATE;
                } else {
                    if (isStalemate()) return GameOverReason.STALEMATE;
                }
            }

            if(repetitionCount >= 5) return GameOverReason.FIVEFOLD;
            if(isSeventyFiveMoves()) return GameOverReason.SEVENTYFIVE_MOVES;
            if(isInsufficientMaterial()) return GameOverReason.INSUFFICIENT_MATERIAL;

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
     *
     * @throws ConvertMoveException when converting move failed
     * @throws IllegalMoveException if move is illegal
     */
    public String toSan(String lanMove){
        if(lanMove == null) throw new NullPointerException("Lan data can not be null!");

        readLock.lock();
        try {
            return ConvertStringMoveUtils.parseLanSequenceToSan(this.chessboard, lanMove).trim();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Convert move data to SAN (like e4 e5 Nf3)
     *
     * @param moveData moves data
     * @return converted SAN move
     *
     * @throws ConvertMoveException if converting move failed
     * @throws IllegalMoveException if move is illegal
     */
    public String toSan(List<MoveInfo> moveData){
        readLock.lock();
        try {
            int[] encodedMoves = moveData.stream()
                    .mapToInt(MoveInfo::originEncodedData)
                    .toArray();

            return ConvertStringMoveUtils.parseEncodedMoveToSan(this.chessboard,
                    encodedMoves).trim();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Convert move data to SAN (like e4 e5 Nf3)
     *
     * @param moveData move data
     * @return converted SAN move
     *
     * @throws IllegalMoveException if move is illegal
     */
    public String toSan(MoveInfo moveData){
        if(moveData == null) throw new NullPointerException("Move data can not be null!");

        readLock.lock();
        try {
            return ConvertStringMoveUtils.toSanString(this.chessboard,
                    moveData.originEncodedData()).trim();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Convert encoded move data to SAN (like e4 e5 Nf3)
     *
     * @param encodedMoves encoded moves data<p>
     *
     * if you want to know what's the <b>encodedMoves</b>, go to {@link EncodeMove}
     *
     * @return converted SAN move
     *
     * @throws ConvertMoveException if converting move failed
     * @throws IllegalMoveException if move is illegal
     */
    public String toSan(int[] encodedMoves) {
        readLock.lock();
        try {
            return ConvertStringMoveUtils.parseEncodedMoveToSan(this.chessboard,
                    encodedMoves).trim();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Convert move data to SAN (like e4 e5 Nf3) <p>
     *
     * if you want to know what's the <b>encodedMove</b>, go to {@link EncodeMove}
     *
     * @param encodedMove the move encoded as an integer (contains source, target, flags, etc.)
     *
     * @return converted SAN move
     *
     * @throws IllegalMoveException if move is illegal
     */
    public String toSan(int encodedMove){
        readLock.lock();
        try {
            return ConvertStringMoveUtils.toSanString(this.chessboard,
                    encodedMove).trim();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Translate SAN string to LAN string
     *
     * @param san SAN move
     * @return Translated string result
     *
     * @throws ConvertMoveException if converting move failed
     */
    public String toLanString(String san) {
        if(san == null) throw new NullPointerException("San data can not be null!");

        readLock.lock();
        try {
            return ConvertStringMoveUtils.toLanString(this.chessboard, san);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Translate SAN string to MoveInfo
     *
     * @param san SAN move
     * @return Translated move data result
     *
     * @throws ConvertMoveException if converting move failed
     * @throws IllegalMoveException if move is illegal
     */
    public MoveInfo sanToMoveData(String san) {
        if(san == null) throw new NullPointerException("San data can not be null!");

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
     * Translate LAN string to MoveInfo
     *
     * @param lan LAN move
     * @return Translated move data result
     *
     * @throws ConvertMoveException if converting move failed
     * @throws IllegalMoveException if move is illegal
     */
    public MoveInfo lanToMoveData(String lan) {
        if(lan == null) throw new NullPointerException("Lan (or uci) data can not be null!");

        Chessboard tempBoard;
        readLock.lock();
        try {
            tempBoard = new Chessboard(this.chessboard);
            return new MoveInfo(ConvertStringMoveUtils.lanToMoveData(tempBoard, lan));
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
            if (chessboard.gameVariant != GameVariant.THREE_CHECK)
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
            if (chessboard.gameVariant != GameVariant.THREE_CHECK)
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
            if (chessboard.gameVariant != GameVariant.THREE_CHECK)
                throw new VariantNotMatchException("This method should be called on three check variant ChessGame!");
            return chessboard.check_count[black];
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get 'full move' on this ChessGame
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
            this.headers.put("Result", PGNExporter.getGameResultString(this.gameResult));

            this.currentNode.terminalResult = result;
            this.currentNode.terminalReason = reason;
        } finally {
            writeLock.unlock();
        }

        notifyGameOver(result, reason);
    }


    /**
     * Get game result <p>
     *
     * This game result doesn't update when the result of this game is already finished. <br>
     * Example : e4 e5 Qh5 Nc6 Bc4 Nf6 Qxf7#, and if undo it, the game result doesn't change. but the
     * {@link #isCheckmate()} changes. <br>
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
     * Game over reason variable for checking why this game finished <p>
     *
     * This game over reason variable doesn't update when the result of this game is already finished. <br>
     * Example : e4 e5 Qh5 Nc6 Bc4 Nf6 Qxf7#, and if undo it, the game over reason doesn't change. but the
     * {@link #isCheckmate()} changes. <br>
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
     * When one of player has resigned
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
        forceEndGame(GameResult.DRAW, GameOverReason.AGREEMENT_DRAW);
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
     * When have to force this game end
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
     * @throws MoveNotFoundException could not find the node
     */
    private boolean addMoveHistory(MoveInfo moveData) {
        for(int i = 0; i < currentNode.children.size(); i++) {
            MoveNode child = currentNode.children.get(i);

            if (moveData.originEncodedData() == child.moveData.originEncodedData()) {
                currentNode = child;

                return false;
            }
        }

        MoveNode result = new MoveNode(moveData, currentNode, nodeCounter.incrementAndGet());

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

            evaluateGameState(getLastMainlineNode(this.moveHistoryRoot));
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

            evaluateGameState(getLastMainlineNode(this.moveHistoryRoot));
        } finally {
            writeLock.unlock();
        }

        if(shouldNotifyHistory) notifyHistoryChanged();
    }

    /**
     * Get Root node on move history
     */
    public MoveNodeDTO getRootNode() {
        readLock.lock();
        try {
            return moveHistoryRoot.convertToDTO();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get current node's long id
     */
    public long getCurrentNodeId() {
        readLock.lock();
        try {
            return currentNode.id;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get current move info
     */
    public MoveInfo getCurrentMoveInfo() {
        readLock.lock();
        try {
            return new MoveInfo(currentNode.moveData.originEncodedData());
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Move position to node (nodeId)
     *
     * @param nodeId node id
     */
    public void jumpToNode(long nodeId) {
        writeLock.lock();

        GameResult gameResult;

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
                MoveGenerator.makeMove(this.chessboard, node.moveData.originEncodedData());
            }

            this.currentNode = targetNode;
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
     * Jump to mainline ply
     * <p>
     * Example : <br>
     * e2e4 e7e5 g1f3 g8f6
     * jumpToMainlinePly(2) and result is e7e5
     *
     * @param targetPly ply
     *
     * @throws MoveNotFoundException if move is not found or targetPly is out of bounds
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
                    MoveGenerator.makeMove(this.chessboard, nextNode.moveData.originEncodedData());
                    newCurrentNode = nextNode;
                }
                this.currentNode = newCurrentNode;
            } else {
                while (currentPly < targetPly && !this.currentNode.children.isEmpty()) {
                    MoveNode nextNode = this.currentNode.children.getFirst();

                    MoveGenerator.makeMove(this.chessboard, nextNode.moveData.originEncodedData());
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
     * Update this node's game over state and return result.
     * if this node's game over state is already there, just return cached value.
     *
     * @param node node
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
                case GIVEAWAY -> getTurn() ? GameResult.WHITE_WON : GameResult.BLACK_WON;
                case SUICIDE -> {
                    int suicideResult = ChessboardUtils.getGameResultForSuicide(chessboard);
                    if(suicideResult == ChessboardUtils.WHITE_WON_VALUE) yield GameResult.WHITE_WON;
                    else if(suicideResult == ChessboardUtils.BLACK_WON_VALUE) yield GameResult.BLACK_WON;
                    else if(suicideResult == ChessboardUtils.DREW_VALUE) yield GameResult.DRAW;
                    else yield GameResult.UNKNOWN;
                }
                case ATOMIC -> {
                    if(chessboard.bitboards[k] == 0L) yield GameResult.WHITE_WON;
                    if(chessboard.bitboards[K] == 0L) yield GameResult.BLACK_WON;
                    yield GameResult.UNKNOWN;
                }
                case STALEMATE, FIVEFOLD, FIFTYMOVES_CLAIM, INSUFFICIENT_MATERIAL,
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
            this.headers.put("Result", PGNExporter.getGameResultString(this.gameResult));
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
            long decimalPoint = milliseconds % 1000 / 10;

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
     * Load PGN on this ChessGame
     *
     * @param pgnString PGN data
     *
     * @throws NodesOverflowException if move count is too large (you can adjust by {@link #loadPGN(String, int maxNodes)})
     */
    public void loadPGN(String pgnString) {
        writeLock.lock();
        try {
            PGNParsedData parsedData = PGNParser.parse(pgnString, MAX_PGN_NODE_COUNT);

            String fenToLoad = parsedData.startFEN();
            this.chessboard.gameVariant = parsedData.variant();
            this.chessboard.isChess960 = parsedData.isChess960();
            ChessboardUtils.parseFen(this.chessboard, fenToLoad);
            this.startPositionFEN = fenToLoad;

            this.moveHistoryRoot = parsedData.rootNode();
            this.currentNode = parsedData.rootNode();

            this.nodeCache = parsedData.cache();

            this.headers.clear();
            setDefaultHeaders();
            this.headers.putAll(parsedData.header());

            this.gameResult = parsedData.gameResult();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Load PGN on this ChessGame
     *
     * @param pgnString PGN data
     * @param maxNodesCount max nodes count
     *
     * @throws NodesOverflowException if move count is more than maxNodesCount
     */
    public void loadPGN(String pgnString, int maxNodesCount) {
        writeLock.lock();
        try {
            PGNParsedData parsedData = PGNParser.parse(pgnString, maxNodesCount);

            String fenToLoad = parsedData.startFEN();
            this.chessboard.gameVariant = parsedData.variant();
            this.chessboard.isChess960 = parsedData.isChess960();
            ChessboardUtils.parseFen(this.chessboard, fenToLoad);
            this.startPositionFEN = fenToLoad;

            this.moveHistoryRoot = parsedData.rootNode();
            this.currentNode = parsedData.rootNode();

            this.nodeCache = parsedData.cache();

            this.headers.clear();
            setDefaultHeaders();
            this.headers.putAll(parsedData.header());

            this.gameResult = parsedData.gameResult();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Generate new MoveNodeDTO with san move data
     *
     * @throws NodesOverflowException if move count is too large
     */
    public MoveNodeDTO getRootNodeWithSan() {
        readLock.lock();
        try {
            Chessboard tempBoard = new Chessboard(this.startPositionFEN);
            tempBoard.gameVariant = this.getGameVariant();

            return PGNExporter.buildPGNTreeWithSan(moveHistoryRoot, tempBoard, MAX_PGN_NODE_COUNT,
                    new int[1]);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Generate new MoveNodeDTO with san move data
     *
     * @param maxNodesCount max nodes count
     *
     * @throws NodesOverflowException if move count is more than maxNodesCount
     */
    public MoveNodeDTO getRootNodeWithSan(int maxNodesCount) {
        readLock.lock();
        try {
            Chessboard tempBoard = new Chessboard(this.startPositionFEN);
            tempBoard.gameVariant = this.getGameVariant();

            return PGNExporter.buildPGNTreeWithSan(moveHistoryRoot, tempBoard, maxNodesCount,
                    new int[1]);
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
     *
     * @throws NodesOverflowException if move count is more than <b>maxNodes</b>
     */
    public String getPGN(int maxNodes) {
        writeLock.lock();
        try {
            if (this.headers.isEmpty()) setDefaultHeaders();

            evaluateGameState(getLastMainlineNode(this.moveHistoryRoot));

            return PGNExporter.export(this,
                    PGNExporter.createPGNGame(headers, startPositionFEN, getGameVariant(),
                            isChess960(), getGameResult(), moveHistoryRoot, maxNodes), false);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Get pgn string <p>
     * Warning : if this ChessGame is chess 960 and gameVariant is not standard, it's going to be overwritten. <br>
     * chess 960 = true, gameVariant = Crazyhouse, PGN header is [Variant "Crazyhouse"].
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
     * @param maxNodes max nodes count
     *
     * @throws NodesOverflowException if move count is more than <b>maxNodes</b>
     */
    public String getPurePGN(int maxNodes) {
        writeLock.lock();
        try {
            if (this.headers.isEmpty()) setDefaultHeaders();

            evaluateGameState(getLastMainlineNode(this.moveHistoryRoot));

            return PGNExporter.export(this,
                    PGNExporter.createPGNGame(headers, startPositionFEN, getGameVariant(),
                    isChess960(), getGameResult(), moveHistoryRoot, maxNodes), true);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Get pgn string with no extra commentary, clk, nag, etc. <p>
     *
     * Warning : if this ChessGame is chess 960 and gameVariant is not standard, it's going to be overwritten. <br>
     * chess 960 = true, gameVariant = Crazyhouse, PGN header is [Variant "Crazyhouse"].
     *
     * @throws NodesOverflowException if move count is too large (you can adjust by {@link #getPGN(int maxNodes)})
     */
    public String getPurePGN() {
        return getPurePGN(MAX_PGN_NODE_COUNT);
    }

    /**
     * Get last main line node
     * <p>
     * Example : <br>
     * e4 e5 Nf3 Nc6 (Nf6 Nxe5) 'Bc4' <br>
     * and the result is Bc4
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
     * Get game start position fen <br>
     * if this ChessGame generated with {@link #fromFEN(String)} methods, the result is the reset fen string
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
     * Get game variant
     */
    public GameVariant getGameVariant() {
        readLock.lock();
        try {
            return chessboard.gameVariant;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Add chess game listener
     */
    public void addChessGameListener(ChessGameListener listener) {
        listeners.addIfAbsent(listener);
    }

    /**
     * Remove chess game listener
     */
    public void removeChessGameListener(ChessGameListener listener) {
        listeners.remove(listener);
    }

    /**
     * Notify listeners when move made
     *
     * @param moveInfo maked move data
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
     * @param targetFen jumped to position fen
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
     * Get total nodes count
     */
    public int getTotalNodeCount() {
        readLock.lock();
        try {
            return this.nodeCache.size();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get current chess board copy (snapshot)
     */
    public Chessboard getBoardSnapshot() {
        readLock.lock();
        try {
            return new Chessboard(this.chessboard);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get this board to ascii
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
     */
    @Override
    public String toString() {
        return this.getFEN();
    }

    /**
     * Get whether this Chessboard and obj is equal "position"
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ChessGame other)) return false;
        return this.chessboard.hash_key == other.chessboard.hash_key;
    }

    /**
     * Get hash key for deciding equal position
     */
    @Override
    public int hashCode() {
        return Long.hashCode(this.chessboard.hash_key);
    }
}

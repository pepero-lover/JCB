package com.pepero.jcb.api;

import com.pepero.jcb.api.book.PolyglotHashUtils;
import com.pepero.jcb.api.dto.*;
import com.pepero.jcb.api.enums.*;
import com.pepero.jcb.api.event.ChessGameListener;
import com.pepero.jcb.api.exception.*;
import com.pepero.jcb.api.parse.ConvertStringMoveUtils;
import com.pepero.jcb.api.parse.FENValidator;
import com.pepero.jcb.api.parse.pgn.MoveAnnotation;
import com.pepero.jcb.api.parse.pgn.PGNLexer;
import com.pepero.jcb.api.parse.pgn.PGNUtils;
import com.pepero.jcb.api.parse.pgn.TokenType;
import com.pepero.jcb.bitboard.BitBoardUtils;
import com.pepero.jcb.constant.BoardSquares;
import com.pepero.jcb.constant.CastlingRights;
import com.pepero.jcb.core.*;
import com.pepero.jcb.encode.EncodeMove;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.pepero.jcb.constant.BoardSquares.no_sq;
import static com.pepero.jcb.constant.EncodedPieces.*;
import static com.pepero.jcb.constant.SideToMove.white;
import static com.pepero.jcb.core.ChessboardUtils.ascii_pieces;
import static com.pepero.jcb.core.MoveGenerator.ILLEGAL_MOVE;
import static com.pepero.jcb.core.MoveGenerator.generateMoves;

public class ChessGame {
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
            this.id = ++nodeCounter;

            this.moveData = null;
            this.parent = null;
        }

        MoveNode(MoveInfo moveData, MoveNode parent) {
            this.id = ++nodeCounter;

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

    private long nodeCounter = 0L;
    private final Map<Long, MoveNode> nodeCache = new HashMap<>();

    private boolean autoChangeGameOver = true;

    private MoveNode moveHistoryRoot = new MoveNode();

    private volatile MoveNode currentNode = moveHistoryRoot;


    // game variables

    private int[] initialPieceCounts = new int[12];

    private LinkedHashMap<String, String> headers = new LinkedHashMap<>();

    private volatile GameResult gameResult = GameResult.UNKNOWN;
    private volatile GameOverReason gameoverReason = GameOverReason.NOTGAMEOVER;

    private final String startPositionFEN;

    private final List<ChessGameListener> listeners = new CopyOnWriteArrayList<>();

    // for pgn parsing pattern
    private static final Pattern CLK_PATTERN = Pattern.compile("\\[%clk\\s+([^\\]]+)\\]");
    private static final Pattern EVAL_PATTERN = Pattern.compile("\\[%eval\\s+([^\\]]+)\\]");
    private static final Pattern CSL_PATTERN = Pattern.compile("\\[%csl\\s+([^\\]]+)\\]");
    private static final Pattern CAL_PATTERN = Pattern.compile("\\[%cal\\s+([^\\]]+)\\]");

    /**
     * Initialize position with FEN string
     * @param fen fen string
     *
     * @throws FENConvertException - if converting fen string failed
     */
    public ChessGame(String fen) {
        this(fen, GameVariants.STANDARD);
    }

    /**
     * Initialize position with FEN string
     *
     * @param fen fen string
     * @param gameVariants game variants ( standard, chess 960 ... )
     *
     * @throws FENConvertException - if converting fen string failed
     */
    public ChessGame(String fen, GameVariants gameVariants) {
        FENValidator.validateString(fen);

        chessboard = new Chessboard();
        startPositionFEN = fen;

        try {
            ChessboardUtils.parseFen(this.chessboard, fen);
        } catch (Exception e) {
            throw new FENConvertException("Could not parse the fen.");
        }

        chessboard.gameVariants = gameVariants;

        FENValidator.validateLogicalState(chessboard);

        nodeCache.put(moveHistoryRoot.id, moveHistoryRoot);

        calculateInitialPieces(fen);
    }

    /**
     * Initialize position to start position
     */
    public ChessGame() {
        this.chessboard = new Chessboard();

        ChessboardUtils.parseFen(this.chessboard, Chessboard.start_position);

        startPositionFEN = Chessboard.start_position;

        nodeCache.put(moveHistoryRoot.id, moveHistoryRoot);

        calculateInitialPieces(this.getFEN());
    }

    /**
     * Lightweight copy constructor
     * Warning : This doesn't copy event listener and history tree but the position of this ChessGame
     *
     * @param other ChessGame class to copy
     */
    public ChessGame(ChessGame other) {
        this.chessboard = new Chessboard(other.chessboard);

        this.startPositionFEN = other.getFEN();
        this.autoChangeGameOver = other.autoChangeGameOver;

        System.arraycopy(other.initialPieceCounts, 0, this.initialPieceCounts, 0, 12);

        this.nodeCounter = 0L;
        this.moveHistoryRoot = new MoveNode();
        this.currentNode = this.moveHistoryRoot;
        this.nodeCache.put(this.moveHistoryRoot.id, this.moveHistoryRoot);

        setDefaultHeaders();
    }

    /**
     * Get FEN on this ChessGame
     * @return fen
     */
    public String getFEN() {
        return ChessboardUtils.getFen(this.chessboard);
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
        int encoded_move = ConvertStringMoveUtils.parseLanToEncodedMove(
                this.chessboard, moveString
        );

        if(!ChessboardUtils.isLegalMove(this.chessboard, encoded_move))
            throw new IllegalMoveException(moveString);

        boolean isSuccess = MoveGenerator.makeMove(this.chessboard, encoded_move);
        if(!isSuccess) throw new IllegalMoveException(moveString);

        MoveInfo moveData = new MoveInfo(encoded_move);

        addMoveHistory(moveData);

        notifyMoveMade(moveData);

        if(autoChangeGameOver) {
            evaluateGameState();
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
        if(!ChessboardUtils.isLegalMove(this.chessboard, encodedMove))
            throw new IllegalMoveException(new MoveInfo(encodedMove).toLanString());

        boolean isSuccess = MoveGenerator.makeMove(this.chessboard, encodedMove);
        if(!isSuccess) throw new IllegalMoveException(new MoveInfo(encodedMove).toLanString());

        MoveInfo moveData = new MoveInfo(encodedMove);

        addMoveHistory(moveData);

        notifyMoveMade(moveData);

        if(autoChangeGameOver) {
            evaluateGameState();
        }
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
        sanString = sanString.trim();
        String[] sanStrings = sanString.split(" ");
        for(String san : sanStrings) {
            makeMoveSan(san);
        }
    }

    /**
     * Make moves on this ChessGame (Lan string)
     *
     * @param lanString san string like "e2e4 e7e5 g1f3 b8c6"
     *
     * @throws IllegalMoveException - if move is illegal move
     * @throws ConvertMoveException - if move data is not correct
     */
    public void makeMoveAll(String lanString) {
        lanString = lanString.trim();
        String[] lanStrings = lanString.split(" ");
        for(String lan : lanStrings) {
            makeMove(lan);
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
            promotionType != PieceType.BISHOP && promotionType != PieceType.KNIGHT)
            throw new IllegalMoveException("Promotion Piece type is unknown! please use like PieceType.QUEEN, PieceType.ROOK");

        int isLegal = MoveGenerator.isLegalMove(this.chessboard, sourceSquare.getIndex(), targetSquare.getIndex(),
                promotionType.getPieceType());

        if(isLegal == ILLEGAL_MOVE){
            String promoChar = (promotionType != PieceType.NONE) ?
                    String.valueOf(ChessboardUtils.promotion_pieces[promotionType.getPieceType()]) : "";
            throw new IllegalMoveException(BoardSquares.square_to_coordinates[sourceSquare.getIndex()]
                    + BoardSquares.square_to_coordinates[targetSquare.getIndex()]
                    + promoChar);
        }

        int encoded_move = ConvertStringMoveUtils.parseMoveDataToEncodedMove(
                this.chessboard, sourceSquare.getIndex(), targetSquare.getIndex(), promotionType.getPieceType()
        );

        if(!ChessboardUtils.isLegalMove(this.chessboard, encoded_move))
            throw new IllegalMoveException(new MoveInfo(encoded_move).toLanString());

        boolean isSuccess = MoveGenerator.makeMove(this.chessboard, encoded_move);
        if(!isSuccess) throw new IllegalMoveException(new MoveInfo(encoded_move).toLanString());

        MoveInfo moveData = new MoveInfo(encoded_move);

        addMoveHistory(moveData);

        notifyMoveMade(moveData);

        if(autoChangeGameOver) {
            evaluateGameState();
        }
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
        if(!ChessboardUtils.isLegalMove(this.chessboard, moveInfo.originEncodedData()))
            throw new IllegalMoveException(moveInfo.toLanString());

        boolean isSuccess = MoveGenerator.makeMove(this.chessboard, moveInfo.originEncodedData());
        if(!isSuccess) throw new IllegalMoveException(moveInfo.toString());

        MoveInfo moveData = new MoveInfo(moveInfo.originEncodedData());

        addMoveHistory(moveData);

        notifyMoveMade(moveData);

        if(autoChangeGameOver) {
            evaluateGameState();
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
        if (!canUndo()) throw new EmptyMoveUndoException();

        MoveInfo moveInfo = currentNode.moveData;
        currentNode = currentNode.parent;

        MoveGenerator.unmakeMove(chessboard, moveInfo.originEncodedData());

        notifyMoveUnmade(moveInfo);

        if(autoChangeGameOver) {
            evaluateGameState();
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
        if (!canRedo()) throw new EmptyMoveRedoException();

        if(currentNode.children.size() <= variationIndex) throw new VariationNotFoundException();

        currentNode = currentNode.children.get(variationIndex);
        MoveInfo moveInfo = currentNode.moveData;

        boolean isSuccess = MoveGenerator.makeMove(this.chessboard, moveInfo.originEncodedData());
        if (!isSuccess) {
            currentNode = currentNode.parent;
            throw new IllegalMoveException(moveInfo.toString());
        }

        notifyMoveRemade(moveInfo);

        if(autoChangeGameOver) {
            evaluateGameState();
        }

        return moveInfo;
    }

    /**
     * Get whether this position can undo
     *
     * @return whether this position can undo
     */
    public boolean canUndo() {
        return currentNode != moveHistoryRoot;
    }

    /**
     * Get whether this position can redo
     *
     * @return whether this position can redo
     *
     * @throws MoveNotFoundException if the current node is not found
     */
    public boolean canRedo() {
        return !currentNode.children.isEmpty();
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
        if (currentNode == null) throw new MoveNotFoundException();
        return currentNode.children.size() > variationIndex;
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
        List<MoveInfo> result = new ArrayList<>();
        MoveNode current = currentNode;
        while (current != null && current.moveData != null) {
            result.add(current.moveData);
            current = current.parent;
        }
        Collections.reverse(result);
        return Collections.unmodifiableList(result);
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
        if(!canUndo()) return new String[0];

        try {
            MoveInfo lastMove = getLastMove();

            String source = lastMove.sourceSquare().toString().toLowerCase();
            String target = lastMove.targetSquare().toString().toLowerCase();

            return new String[] { source, target };

        } catch (MoveNotFoundException e) {
            return new String[0];
        }
    }

    public MoveType getLastMoveType() {
        MoveInfo lastMove = getLastMove();

        if(lastMove.capture()) return MoveType.CAPTURE;
        if(lastMove.castling()) return MoveType.CASTLING;
        if(lastMove.promotionPiece() != PieceType.NONE) return MoveType.PROMOTION;
        if(lastMove.enpassant()) return MoveType.ENPASSANT;
        return MoveType.NORMAL;
    }

    /**
     * Get the last (previous) move
     *
     * @return the last (previous) move
     *
     * @throws MoveNotFoundException if the current node is not found (Only for variation mode)
     */
    public MoveInfo getLastMove() {
        if(currentNode == null) throw new MoveNotFoundException();

        return currentNode.moveData;
    }

    /**
     * Get white turn
     *
     * @return white turn
     */
    public boolean getTurn() {
        return this.chessboard.side == white;
    }

    /**
     * Get whether this move legal move
     *
     * @param source source square
     * @param target target square
     * @return whether this move legal move
     */
    public boolean canDropPiece(Square source, Square target) {
        int sourceIndex = source.getIndex();
        int targetIndex = target.getIndex();

        int[] move_list = new int[255];
        int move_count = MoveGenerator.generateMoves(chessboard, move_list);

        for(int i = 0; i < move_count; i++) {
            int move = move_list[i];
            if(EncodeMove.getMoveSource(move) == sourceIndex
                    && EncodeMove.getMoveTarget(move) == targetIndex) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get whether this move is a promotion move
     *
     * @param source source square
     * @param target target square
     * @return whether this move is a promotion move
     */
    public boolean shouldPromotion(Square source, Square target) {
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
    }

    /**
     * Init initial piece count
     *
     * @param fen this chess game fen
     */
    private void calculateInitialPieces(String fen) {
        String boardFen = fen.split(" ")[0];
        for (int i = 0; i < boardFen.length(); i++) {
            char c = boardFen.charAt(i);
            switch (c) {
                case 'P' -> initialPieceCounts[P]++; case 'N' -> initialPieceCounts[N]++;
                case 'B' -> initialPieceCounts[B]++; case 'R' -> initialPieceCounts[R]++;
                case 'Q' -> initialPieceCounts[Q]++;

                case 'p' -> initialPieceCounts[p]++; case 'n' -> initialPieceCounts[n]++;
                case 'b' -> initialPieceCounts[b]++; case 'r' -> initialPieceCounts[r]++;
                case 'q' -> initialPieceCounts[q]++;
            }
        }
    }

    /**
     * Get captured piece
     *
     * @param isWhite if white, returns black captured piece. if black, returns white captured piece.
     * @return captured piece
     */
    public Map<PieceType, Integer> getCapturedPieces(boolean isWhite) {
        Map<PieceType, Integer> captured = new EnumMap<>(PieceType.class);

        if (isWhite) {
            captured.put(PieceType.PAWN,
                    initialPieceCounts[p] - BitBoardUtils.countBits(chessboard.getBitboardPiece(p)));
            captured.put(PieceType.KNIGHT,
                    initialPieceCounts[n] - BitBoardUtils.countBits(chessboard.getBitboardPiece(n)));
            captured.put(PieceType.BISHOP,
                    initialPieceCounts[b] - BitBoardUtils.countBits(chessboard.getBitboardPiece(b)));
            captured.put(PieceType.ROOK,
                    initialPieceCounts[r] - BitBoardUtils.countBits(chessboard.getBitboardPiece(r)));
            captured.put(PieceType.QUEEN,
                    initialPieceCounts[q] - BitBoardUtils.countBits(chessboard.getBitboardPiece(q)));
        } else {
            captured.put(PieceType.PAWN,
                    initialPieceCounts[P] - BitBoardUtils.countBits(chessboard.getBitboardPiece(P)));
            captured.put(PieceType.KNIGHT,
                    initialPieceCounts[N] - BitBoardUtils.countBits(chessboard.getBitboardPiece(N)));
            captured.put(PieceType.BISHOP,
                    initialPieceCounts[B] - BitBoardUtils.countBits(chessboard.getBitboardPiece(B)));
            captured.put(PieceType.ROOK,
                    initialPieceCounts[R] - BitBoardUtils.countBits(chessboard.getBitboardPiece(R)));
            captured.put(PieceType.QUEEN,
                    initialPieceCounts[Q] - BitBoardUtils.countBits(chessboard.getBitboardPiece(Q)));
        }

        captured.values().removeIf(count -> count <= 0);
        return captured;
    }

    /**
     * Get piece score (For GUI showing / material comparison)
     *
     * @return piece score
     */
    public int getPieceScore() {
        int piece_score = 0;

        for(int white_piece = P; white_piece <= K; white_piece++) {
            int multiply = switch (white_piece) {
                case P -> 1;
                case N, B -> 3;
                case R -> 5;
                case Q -> 9;
                case K -> 100;
                default -> 0;
            };

            piece_score += BitBoardUtils.countBits(this.chessboard.getBitboardPiece(white_piece)) * multiply;
        }

        for(int black_piece = p; black_piece <= k; black_piece++) {
            int multiply = switch (black_piece) {
                case p -> 1;
                case n, b -> 3;
                case r -> 5;
                case q -> 9;
                case k -> 100;
                default -> 0;
            };

            piece_score -= BitBoardUtils.countBits(this.chessboard.getBitboardPiece(black_piece)) * multiply;
        }

        return piece_score;
    }

    /**
     * Get piece type on square
     * if not found, returns NONE
     *
     * @param square square
     * @return piece type
     */
    public Piece getPieceOnSquare(Square square){
        int piece_type = ChessboardUtils.getPieceTypeOnSquare(this.chessboard, square.getIndex());

        return Piece.fromIndex(piece_type);
    }



    /**
     * Get legal moves on this chess game
     *
     * @return legal moves
     */
    public List<MoveInfo> getLegalMoves() {
        int[] move_list = new int[255];
        int move_count = generateMoves(this.chessboard, move_list);
        List<MoveInfo> result = new ArrayList<>(move_count);

        for (int count = 0; count < move_count; count++){
            int encodedMove = move_list[count];
            if(!MoveGenerator.makeMove(this.chessboard ,encodedMove))
                continue;
            MoveGenerator.unmakeMove(chessboard, encodedMove);
            result.add(new MoveInfo(encodedMove));
        }

        return result;
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

        int[] move_list = new int[255];
        int move_count = generateMoves(this.chessboard, move_list);
        List<MoveInfo> result = new ArrayList<>(move_count);

        for (int count = 0; count < move_count; count++){
            int encodedMove = move_list[count];
            if(EncodeMove.getMoveSource(encodedMove) != square.getIndex()) continue;

            if(!MoveGenerator.makeMove(this.chessboard ,encodedMove))
                continue;
            MoveGenerator.unmakeMove(chessboard, encodedMove);
            result.add(new MoveInfo(encodedMove));
        }

        return result;
    }

    /**
     * Generate moves only one target square
     * <p>
     * Example : chessboard = start pos, square = e4, returns e2e3, e2e4
     *
     * @return generated move
     */
    public List<MoveInfo> getLegalMovesForTarget(Square square) {
        Objects.requireNonNull(square, "Target Square is null!");

        int[] move_list = new int[255];
        int move_count = generateMoves(this.chessboard, move_list);
        List<MoveInfo> result = new ArrayList<>(move_count);

        for (int count = 0; count < move_count; count++){
            int encodedMove = move_list[count];
            if(EncodeMove.getMoveTarget(encodedMove) != square.getIndex()) continue;

            if(!MoveGenerator.makeMove(this.chessboard ,encodedMove))
                continue;
            MoveGenerator.unmakeMove(chessboard, encodedMove);
            result.add(new MoveInfo(encodedMove));
        }

        return result;
    }

    /**
     * Get board state (Map)(square)(piece)
     *
     * @return board state (Map)
     */
    public Map<Square, Piece> getBoardStateMap() {
        Map<Square, Piece> result = new HashMap<>();
        for(Square square : Square.values()) {
            Piece piece = getPieceOnSquare(square);
            if (piece != Piece.NONE) {
                result.put(square, piece);
            }
        }

        return result;
    }

    /**
     * Get whether this square is empty
     *
     * @param square square
     * @return whether this square is empty
     */
    public boolean isEmpty(Square square) {
        return getPieceOnSquare(square) == Piece.NONE;
    }

    /**
     * Get whether the king is under attack
     *
     * @return whether the king is under attack
     */
    public boolean isCheck() {
        return ChessboardUtils.isCheck(this.chessboard);
    }

    /**
     * Get checking piece (max size is 2)
     *
     * @return checking piece
     */
    public List<Square> getChecker() {
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
    }

    /**
     * Get whether this position is checkmate
     *
     * @return whether this position is checkmate
     */
    public boolean isCheckmate() {
        return ChessboardUtils.isCheckmate(this.chessboard);
    }

    /**
     * Get whether this position is stalemate
     *
     * @return whether this position is stalemate
     */
    public boolean isStalemate() {
        return ChessboardUtils.isStaleMate(this.chessboard);
    }

    /**
     * Get whether this position is threefold repetition
     *
     * @return whether this position is threefold repetition
     */
    public boolean isThreefoldRepetition() {
        return ChessboardUtils.getRepetitionCount(this.chessboard) == 3;
        // because getRepetitionCount method returns 3 if the position is repeated over 3 times
    }

    public boolean isInsufficientMaterial() {
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
    }

    /**
     * Get whether this position is fifty moves draw
     *
     * @return whether this position is fifty moves draw
     */
    public boolean isFiftyMoves() {
        return chessboard.half_ply >= 100;
    }

    /**
     * Get whether this game overed.
     * If not, return GameOverReason.NOTGAMEOVER.
     * <p>
     * Types : CHECKMATE, STALEMATE, THREEFOLD REPETITION, FIFTY MOVES DRAW
     *
     * @return game over reason (if not, return GameOverReason.NOTGAMEOVER)
     */
    public GameOverReason isGameOver() {
        boolean inCheck = isCheck();

        if (inCheck) {
            if (isCheckmate()) return GameOverReason.CHECKMATE;
        } else {
            if (isStalemate()) return GameOverReason.STALEMATE;
        }

        if(isThreefoldRepetition()) return GameOverReason.THREEFOLD;
        if(isFiftyMoves()) return GameOverReason.FIFTYMOVES;
        if(isInsufficientMaterial()) return GameOverReason.INSUFFICIENTMATERIAL;

        return GameOverReason.NOTGAMEOVER;
    }

    /**
     * Convert LAN (like e2e4 e7e5 g1f3) to SAN (like e4 e5 Nf3)
     *
     * @param lanMove LAN move
     * @return converted SAN move
     */
    public String toSan(String lanMove){
        ChessGame tempGame = new ChessGame(this.getFEN());
        return ConvertStringMoveUtils.translateLanSequence(tempGame.chessboard, lanMove).trim();
    }

    /**
     * Convert move data to SAN (like e4 e5 Nf3)
     *
     * @param moveData move data
     * @return converted SAN move
     */
    public String toSan(List<MoveInfo> moveData){
        ChessGame tempGame = new ChessGame(this.getFEN());

        StringBuilder sb = new StringBuilder();
        for(MoveInfo moveInfo : moveData) {
            sb.append(moveInfo.toString()).append(" ");
        }

        return ConvertStringMoveUtils.translateLanSequence(tempGame.chessboard, sb.toString()).trim();
    }

    /**
     * Translate SAN string to LAN string
     *
     * @param san SAN move
     * @return Translated string result
     */
    public String toLanString(String san) {
        return ConvertStringMoveUtils.toLanString(chessboard, san);
    }

    /**
     * Translate SAN string to MoveInfo
     *
     * @param san SAN move
     * @return Translated move data result
     */
    public MoveInfo toLanMoveData(String san) {
        return new MoveInfo(ConvertStringMoveUtils.sanToMoveData(chessboard, san));
    }

    /**
     * Get 'full move' on this ChessGame
     *
     * @return full move
     */
    public int getFullMove() {
        return this.chessboard.ply;
    }

    /**
     * Get 'half move' on this ChessGame
     *
     * @return half move
     */
    public int getHalfMove() {
        return this.chessboard.half_ply;
    }

    /**
     * Force to make the game end
     *
     * @param result game result
     * @param reason game over reason
     */
    private void forceEndGame(GameResult result, GameOverReason reason) {
        if (this.gameoverReason != GameOverReason.NOTGAMEOVER) {
            throw new IllegalStateException("This game is already finished!");
        }

        this.gameResult = result;
        this.gameoverReason = reason;
        this.headers.put("Result", PGNUtils.getGameResultString(this.gameResult));

        this.currentNode.terminalResult = result;
        this.currentNode.terminalReason = reason;

        notifyGameOver(result, reason);
    }


    /**
     * Get game result
     *
     * @return game result
     */
    public GameResult getGameResult() {
        evaluateGameState();
        return this.gameResult;
    }

    /**
     * Get game over reason
     *
     * @return game over reason
     */
    public GameOverReason getGameoverReason() {
        evaluateGameState();
        return this.gameoverReason;
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
     *
     * @throws MoveNotFoundException - could not find the node
     */
    private void addMoveHistory(MoveInfo moveData) {
        for(int i = 0; i < currentNode.children.size(); i++) {
            MoveNode child = currentNode.children.get(i);

            if (moveData.equals(child.moveData)) {
                currentNode = child;

                return;
            }
        }

        MoveNode result = new MoveNode(moveData, currentNode);

        currentNode.children.add(result);
        currentNode = result;

        nodeCache.put(result.id, result);

        notifyHistoryChanged();
    }

    /**
     * Redo move on tree (variation)
     * <p>
     * Example: e2e4 e7e5 (d7d5) g1f3 and pointer is on e2e4. <br>
     * if redoMoveOnTree(0), e7e5 is pointer, <br>
     * if redoMoveOnTree(1), d7d5 is pointer.
     *
     * @param variationIndex variation index
     *
     * @throws VariationNotFoundException if variation is not found
     * @throws MoveNotFoundException if the current node (move) is not found
     *
     * @return Move info
     */
    private MoveInfo redoMoveOnTree(int variationIndex) {
        if(currentNode.children.size() <= variationIndex
                || currentNode.children.get(variationIndex) == null) throw new VariationNotFoundException();

        currentNode = currentNode.children.get(variationIndex);
        return currentNode.moveData;
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
        MoveNode targetNode = nodeCache.get(nodeId);
        if (targetNode == null) throw new MoveNotFoundException("Could not find the node to promote!");
        if (targetNode == moveHistoryRoot || targetNode.parent == null) return;

        MoveNode parent = targetNode.parent;
        int currentIndex = parent.children.indexOf(targetNode);

        if (currentIndex > 0) {
            parent.children.remove(currentIndex);
            parent.children.addFirst(targetNode);

            notifyHistoryChanged();
        }
    }

    /**
     * Get Root node on move history <br>
     *
     * @return Root node
     */
    public MoveNodeDTO getRootNode() {
        return convertToDTO(moveHistoryRoot);
    }

    /**
     * Get current node's UUID
     * When current node is null, returns -1
     *
     * @return current node's uuid string
     */
    public long getCurrentNodeId() {
        if (currentNode == null) return -1;

        return currentNode.id;
    }

    /**
     * Get current node as DTO
     *
     * @return MoveNodeDTO of the current node
     */
    public MoveNodeDTO getCurrentNodeDTO() {
        if (currentNode == null) return null;

        return convertToDTO(currentNode);
    }

    /**
     * Move position to node (nodeId)
     *
     * @param nodeId node uuid
     */
    public void jumpToNode(long nodeId) {
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
        if(autoChangeGameOver) {
            evaluateGameState();
        }

        notifyPositionJumped(getFEN());
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

        notifyPositionJumped(getFEN());
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
        return headers;
    }

    /**
     * Set / Add header
     *
     * @param key header string
     * @param value header value
     */
    public void setHeader(String key, String value) {
        this.headers.put(key, value);
    }

    /**
     * Change flag changing game over state when moved / unmoved. <br>
     * if you want efficiency, disable this to get more NPS. but you can't use Listener on onGameOver method. <br>
     *
     * @param autoChangeGameOver en/disable flag changing game over state when moved
     */
    public void setAutoChangingGameOver(boolean autoChangeGameOver) {
        this.autoChangeGameOver = autoChangeGameOver;
    }

    /**
     * Update this current node's game over state.
     * if this current node's game over state is already there, just return cached value.
     */
    private void evaluateGameState() {
        // when resign / agreement draw
        if (this.currentNode.terminalReason != null) {
            this.gameResult = this.currentNode.terminalResult;
            this.gameoverReason = this.currentNode.terminalReason;
            return;
        }

        // when value is already cached
        if (this.currentNode.isStateEvaluated) {
            this.gameResult = this.currentNode.calculatedResult;
            this.gameoverReason = this.currentNode.calculatedReason;

            return;
        }

        // if value is not cached

        GameOverReason reason = isGameOver();
        GameResult result = GameResult.UNKNOWN;

        if (reason != GameOverReason.NOTGAMEOVER) {
            result = switch (reason) {
                case CHECKMATE -> getTurn() ? GameResult.BLACK_WON : GameResult.WHITE_WON;
                case STALEMATE, THREEFOLD, FIFTYMOVES, INSUFFICIENTMATERIAL -> GameResult.DRAW;
                default -> GameResult.UNKNOWN;
            };

            notifyGameOver(result, reason);
        }

        this.currentNode.calculatedReason = reason;
        this.currentNode.calculatedResult = result;
        this.currentNode.isStateEvaluated = true;

        this.gameoverReason = reason;
        this.gameResult = result;
        this.headers.put("Result", PGNUtils.getGameResultString(this.gameResult));
    }

    /**
     * Save clock data on last move on MoveNode(DTO)
     *
     * @param hours hours data
     * @param minutes minutes data
     * @param seconds seconds data
     */
    public void setCurrentMoveClock(int hours, int minutes, int seconds) {
        if (this.currentNode == moveHistoryRoot) throw new ClockException("Current position can not be start position!");
        this.currentNode.getAnnotation().clk = String.format("%d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * Save clock data on last move on MoveNode(DTO)
     *
     * @param clkTime string format like "0:05:00"
     */
    public void setCurrentMoveClock(String clkTime) {
        if (this.currentNode == moveHistoryRoot) throw new ClockException("Current position can not be start position!");
        this.currentNode.getAnnotation().clk = clkTime;
    }

    /**
     * Add engine eval data on this current move
     *
     * @param eval eval data like "1.25", "#-3"...
     */
    public void setCurrentMoveEval(String eval) {
        if (this.currentNode == moveHistoryRoot) return;
        this.currentNode.getAnnotation().eval = eval;
    }

    /**
     * Add highlighting square data on this current move
     *
     * @param csl square data like "Ge4" (Green square on e4), "Yd5" (Yellow square on d5)
     */
    public void setCurrentMoveCsl(String csl) {
        if (this.currentNode == moveHistoryRoot) return;
        this.currentNode.getAnnotation().csl = csl;
    }

    /**
     * Add highlighting arrow data on this current move
     *
     * @param cal arrow data like "Gg1f3" (Green arrow g1 to f3), "Ye2e4" (Yellow arrow e2 to e4)
     */
    public void setCurrentMoveCal(String cal) {
        if (this.currentNode == moveHistoryRoot) return;
        this.currentNode.getAnnotation().cal = cal;
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
                    anno.comment, anno.nag, anno.clk,
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
     * Load PGN on this ChessGame
     *
     * @param pgnString PGN data
     */
    public PGNGame loadPGN(String pgnString) {
        if (pgnString == null || pgnString.isEmpty()) {
            throw new IllegalArgumentException("PGN string is empty");
        }
        pgnString = pgnString.replace("\uFEFF", "");

        Map<String, String> headers = new HashMap<>();

        String[] lines = pgnString.split("\\R");

        int line_stopped = -1;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            line = line.trim();

            if(line.isEmpty()) continue;

            if(line.startsWith("[")) {
                line = line.substring(1, line.length() - 1);

                String[] parts = line.split(" ", 2);
                if(parts.length == 2) {
                    String type = parts[0];
                    String what = parts[1].replace("\"", "");

                    headers.put(type, what);
                }
            } else {
                line_stopped = i;
                break;
            }
        }

        String movePGNString = "";

        if(line_stopped != -1) {
            StringBuilder moveBuilder = new StringBuilder();
            for (int i = line_stopped; i < lines.length; i++) {
                moveBuilder.append(lines[i]);
                moveBuilder.append("\n");
            }
            movePGNString = moveBuilder.toString();
        }

        MoveNode rootNode = new MoveNode();
        MoveNode currentNode = rootNode;

        this.nodeCache.clear();
        this.nodeCache.put(rootNode.id, rootNode);

        record VariationState(MoveNode node, Chessboard snapshotBoard) {}

        Stack<VariationState> variationStack = new Stack<>();

        Chessboard pgnChessboard;

        if("1".equals(headers.get("SetUp")) && headers.containsKey("FEN")) {
            String startFEN = headers.get("FEN");
            pgnChessboard = new Chessboard(startFEN);
        } else {
            pgnChessboard = new Chessboard(Chessboard.start_position);
        }

        GameResult gameResult = GameResult.UNKNOWN;

        PGNLexer lexer = new PGNLexer(movePGNString);
        PGNToken currentToken;

        while ((currentToken = lexer.nextToken()).type() != TokenType.EOF) {
            switch (currentToken.type()) {
                case COMMENT:
                    String rawComment = currentToken.value().trim();

                    // clock parsing
                    Matcher clkMatcher = CLK_PATTERN.matcher(rawComment);
                    if (clkMatcher.find()) {
                        currentNode.getAnnotation().clk = clkMatcher.group(1);
                        rawComment = clkMatcher.replaceAll("").trim();
                    }

                    // engine eval parsing
                    Matcher evalMatcher = EVAL_PATTERN.matcher(rawComment);
                    if (evalMatcher.find()) {
                        currentNode.getAnnotation().eval = evalMatcher.group(1);
                        rawComment = evalMatcher.replaceAll("").trim();
                    }

                    // square light parsing
                    Matcher cslMatcher = CSL_PATTERN.matcher(rawComment);
                    if (cslMatcher.find()) {
                        currentNode.getAnnotation().csl = cslMatcher.group(1);
                        rawComment = cslMatcher.replaceAll("").trim();
                    }

                    // arrow light parsing
                    Matcher calMatcher = CAL_PATTERN.matcher(rawComment);
                    if (calMatcher.find()) {
                        currentNode.getAnnotation().cal = calMatcher.group(1);
                        rawComment = calMatcher.replaceAll("").trim();
                    }

                    // comment
                    if (!rawComment.isEmpty()) {
                        currentNode.getAnnotation().comment = (currentNode.getAnnotation().comment == null)
                                ? rawComment : currentNode.getAnnotation().comment + " " + rawComment;
                    }

                    break;
                case NAG:
                    currentNode.getAnnotation().nag = currentToken.value();
                    break;
                case VARIATION_START:
                    // start variation
                    variationStack.push(new VariationState(currentNode, new Chessboard(pgnChessboard)));
                    if (currentNode.moveData != null) {
                        int moveToUnmake = currentNode.moveData.originEncodedData();
                        MoveGenerator.unmakeMove(pgnChessboard, moveToUnmake);
                        currentNode = currentNode.parent;
                    }
                    break;
                case VARIATION_END:
                    // return to previous board
                    if (!variationStack.isEmpty()) {
                        VariationState state = variationStack.pop();
                        currentNode = state.node;
                        pgnChessboard = state.snapshotBoard;
                    } else {
                        throw new PGNConvertException("Variation stack is empty!");
                    }
                    break;
                case RESULT:
                    // game result
                    if (currentToken.value().equals("1-0")) gameResult = GameResult.WHITE_WON;
                    if (currentToken.value().equals("0-1")) gameResult = GameResult.BLACK_WON;
                    if (currentToken.value().equals("1/2-1/2")) gameResult = GameResult.DRAW;
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
                    if(!ChessboardUtils.isLegalMove(pgnChessboard, moveData))
                        throw new IllegalMoveException(new MoveInfo(moveData).toString());
                    MoveGenerator.makeMove(pgnChessboard, moveData);

                    MoveInfo moveInfo = new MoveInfo(moveData);
                    MoveNode newNode = new MoveNode(moveInfo, currentNode);
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

                    currentNode.children.add(newNode);
                    currentNode = newNode;

                    this.nodeCache.put(newNode.id, newNode);

                    break;
            }
        }

        String fenToLoad = headers.getOrDefault("FEN", Chessboard.start_position);
        ChessboardUtils.parseFen(this.chessboard, fenToLoad);

        MoveNodeDTO rootDTO = convertToDTO(rootNode);

        this.moveHistoryRoot = rootNode;
        this.currentNode = rootNode;

        this.headers.clear();
        setDefaultHeaders();
        this.headers.putAll(headers);

        this.gameResult = gameResult;
        if (gameResult != GameResult.UNKNOWN) {
            MoveNode lastNode = getLastMainlineNode(rootNode);
            lastNode.terminalResult = gameResult;

            if (ChessboardUtils.isCheckmate(pgnChessboard)) {
                lastNode.terminalReason = GameOverReason.CHECKMATE;
            } else if (ChessboardUtils.isStaleMate(pgnChessboard)) {
                lastNode.terminalReason = GameOverReason.STALEMATE;
            } else {
                lastNode.terminalReason = (gameResult == GameResult.DRAW) ?
                        GameOverReason.AGREEMENTDRAW : GameOverReason.RESIGNATION;
            }
        }

        return new PGNGame(headers, rootDTO, gameResult);
    }

    public PGNGame toPGNGame() {
        if(this.headers.isEmpty()) setDefaultHeaders();

        String currentStartFen = this.startPositionFEN;
        if (!currentStartFen.equals(Chessboard.start_position)) {
            this.headers.put("SetUp", "1");
            this.headers.put("FEN", currentStartFen);
        } else {
            this.headers.remove("SetUp");
            this.headers.remove("FEN");
        }

        evaluateGameState();
        this.headers.put("Result", PGNUtils.getGameResultString(this.gameResult));

        Chessboard tempBoard = new Chessboard(startPositionFEN);
        tempBoard.gameVariants = this.getGameVariants();

        MoveNodeDTO rootDTO = buildPGNTreeWithSan(this.moveHistoryRoot, tempBoard);

        return new PGNGame(new LinkedHashMap<>(this.headers), rootDTO, this.gameResult);
    }

    /**
     * Add san move on pgn tree
     *
     * @param node root node
     * @param tempBoard board
     * @return root node
     */
    private MoveNodeDTO buildPGNTreeWithSan(MoveNode node, Chessboard tempBoard) {
        String calculatedSan = null;

        if (node.moveData != null) {
            calculatedSan = ConvertStringMoveUtils.toSanString(tempBoard, node.moveData);

            MoveGenerator.makeMove(tempBoard, node.moveData.originEncodedData());
        }

        List<MoveNodeDTO> childrenDTOs = new java.util.ArrayList<>();

        for (MoveNode child : node.children) {
            childrenDTOs.add(buildPGNTreeWithSan(child, new Chessboard(tempBoard)));
        }

        MoveAnnotation nodeAnnotation = node.getAnnotation();
        MoveAnnotationDTO annotationDTO = new MoveAnnotationDTO(nodeAnnotation.comment, nodeAnnotation.nag, nodeAnnotation.clk,
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
     */
    public MoveNodeDTO getRootNodeWithSan() {
        Chessboard tempBoard = new Chessboard(this.startPositionFEN);
        tempBoard.gameVariants = this.getGameVariants();

        return buildPGNTreeWithSan(moveHistoryRoot, tempBoard);
    }

    public String getPGN() {
        return PGNUtils.export(this, toPGNGame());
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
        MoveNode lastNode = moveHistoryRoot;

        while (!lastNode.children.isEmpty()) {
            lastNode = lastNode.children.getFirst();
        }

        return lastNode;
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
        MoveNode lastNode = startNode;

        while (!lastNode.children.isEmpty()) {
            lastNode = lastNode.children.getFirst();
        }

        return lastNode;
    }

    /**
     * Get game start position fen
     *
     * @return game start position fen
     */
    public String getStartPositionFEN() {
        return startPositionFEN;
    }

    /**
     * Get game variants
     *
     * @return game variants
     */
    public GameVariants getGameVariants() {
        return chessboard.gameVariants;
    }

    /**
     * Add chess game listener
     *
     * @param listener listener
     */
    public void addChessGameListener(ChessGameListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
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
        return PolyglotHashUtils.getPolyglotHash(this.chessboard);
    }

    /**
     * Get this board to ascii
     *
     * @return Ascii board
     */
    public String toAscii() {
        StringBuilder sb = new StringBuilder(256);
        char[] board = new char[64];

        // initialize board with dots
        Arrays.fill(board, '.');

        // loop over all piece types
        for (int piece = P; piece <= k; piece++) {
            long bitboardPiece = chessboard.getBitboardPiece(piece);
            char pieceChar = ascii_pieces[piece];

            // a bit scanning: find all set bits for this piece type
            while (bitboardPiece != 0L) {
                int square = BitBoardUtils.getLS1BIndex(bitboardPiece);
                board[square] = pieceChar;
                bitboardPiece = BitBoardUtils.popBit(bitboardPiece,square);
            }
        }

        sb.append('\n');

        // loop over board ranks
        for (int rank = 0; rank < 8; rank++) {
            // append ranks
            sb.append("  ").append(8 - rank).append("  ");

            // loop over board files
            for (int file = 0; file < 8; file++) {
                int square = rank * 8 + file;
                // prints char piece from our mapped board
                sb.append(" ").append(board[square]);
            }
            // print new line every rank
            sb.append('\n');
        }

        // print board files
        sb.append("\n      a b c d e f g h \n\n");

        // print side to move
        sb.append("      Side:     ").append(chessboard.side == white ? "white" : "black").append("\n");

        // print enpassant square
        sb.append("      Enpassant:   ").append((chessboard.enpassant != no_sq) ?
                BoardSquares.square_to_coordinates[chessboard.enpassant] : "no").append("\n");

        // print castling rights
        sb.append("      Castling:  ")
                .append(((chessboard.castle & CastlingRights.WK) != 0) ? 'K' : '-')
                .append(((chessboard.castle & CastlingRights.WQ) != 0) ? 'Q' : '-')
                .append(((chessboard.castle & CastlingRights.BK) != 0) ? 'k' : '-')
                .append(((chessboard.castle & CastlingRights.BQ) != 0) ? 'q' : '-')
                .append("\n");

        return sb.toString();
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
     */
    public void printHistory() {
        printHistory(getRootNodeWithSan(), 0);
    }

    @Override
    public String toString() {
        return this.getFEN();
    }
}

package com.pepero.jcb.api;

import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.dto.PGNGame;
import com.pepero.jcb.api.enums.*;
import com.pepero.jcb.api.exception.*;
import com.pepero.jcb.api.parse.ConvertStringMoveUtils;
import com.pepero.jcb.api.parse.PGNUtils;
import com.pepero.jcb.bitboard.BitBoardUtils;
import com.pepero.jcb.constant.BoardSquares;
import com.pepero.jcb.constant.CastlingRights;
import com.pepero.jcb.core.*;
import com.pepero.jcb.encode.EncodeMove;

import java.util.*;

import static com.pepero.jcb.constant.BoardSquares.no_sq;
import static com.pepero.jcb.constant.EncodedPieces.*;
import static com.pepero.jcb.constant.SideToMove.white;
import static com.pepero.jcb.core.ChessboardUtils.ascii_pieces;
import static com.pepero.jcb.core.MoveGenerator.ILLEGAL_MOVE;
import static com.pepero.jcb.core.MoveGenerator.generateMoves;

public class ChessGame {
    static {
        Initializer.init();
    }

    // Chessboard class
    private final Chessboard chessboard;

    // For variation mode
    private class MoveNode {
        final MoveNode parent;
        final List<MoveNode> children = new ArrayList<>();
        final MoveInfo moveData;

        String san;
        String comment;
        String nag;

        GameResult terminalResult = null;
        GameOverReason terminalReason = null;

        MoveNode() {
            this.moveData = null;
            this.parent = null;
        }

        MoveNode(MoveInfo moveData, MoveNode parent) {
            this.moveData = moveData;
            this.parent = parent;
        }

        @Override
        public String toString() {
            String dataStr = (moveData == null) ? "ROOT" : moveData.toString();
            return dataStr + " -> " + children;
        }
    }

    public record MoveNodeDTO(
            MoveInfo moveData,
            List<MoveNodeDTO> children,
            String san,
            String comment,
            String nag // numeric annotation glyph
    ) {
        private static MoveNodeDTO from(MoveNode node) {
            if (node == null) return null;

            List<MoveNodeDTO> childDTOs = node.children.stream()
                    .map(MoveNodeDTO::from)
                    .toList();

            return new MoveNodeDTO(
                    node.moveData,
                    childDTOs,
                    node.san,
                    node.comment,
                    node.nag
            );
        }
    }

    // for variation

    private MoveNode moveHistoryRoot = new MoveNode();

    // pointer
    private MoveNode currentNode = moveHistoryRoot;


    // for linear

    private List<MoveInfo> linearHistory = new ArrayList<>();
    private int currentMoveIndex = -1;


    private LinkedHashMap<String, String> headers = new LinkedHashMap<>();

    private GameResult gameResult = GameResult.UNKNOWN;
    private GameOverReason gameoverReason = GameOverReason.NOTGAMEOVER;

    private final GameMode gameMode;

    private final String startPositionFEN;

    /**
     * Initialize position with FEN string
     * @param fen fen string
     * @param gameMode game mode (variation mode, linear mode)
     *
     * @throws FENConvertException - if converting fen string failed
     */
    public ChessGame(String fen, GameMode gameMode) {
        this(fen, gameMode, GameVariants.STANDARD);
    }


    /**
     * Initialize position with FEN string
     * @param fen fen string
     * @param gameMode game mode (variation mode, linear mode)
     * @param gameVariants game variants ( standard, chess 960 ... )
     *
     * @throws FENConvertException - if converting fen string failed
     */
    public ChessGame(String fen, GameMode gameMode, GameVariants gameVariants) {
        if (fen == null || fen.trim().isEmpty()) {
            throw new FENConvertException("FEN string is empty!");
        }

        chessboard = new Chessboard();
        startPositionFEN = fen;
        this.gameMode = gameMode;

        try {
            ChessboardUtils.parseFen(this.chessboard, fen);
        } catch (Exception e){
            throw new FENConvertException("Could not parse the fen.");
        }

        chessboard.gameVariants = gameVariants;
    }

    /**
     * Initialize position with FEN string (Variation mode default)
     *
     * @param fen fen string
     *
     * @throws FENConvertException - if converting fen string failed
     */
    public ChessGame(String fen) {
        this(fen, GameMode.VARIATION);
    }

    /**
     * Initialize position to start position
     */
    public ChessGame(GameMode gameMode) {
        this.chessboard = new Chessboard();

        ChessboardUtils.parseFen(this.chessboard, Chessboard.start_position);

        startPositionFEN = Chessboard.start_position;

        this.gameMode = gameMode;
    }

    /**
     * Initialize position to start position
     */
    public ChessGame() {
        this(GameMode.VARIATION);
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

        boolean isSuccess = MoveGenerator.makeMove(this.chessboard, encoded_move);
        if(!isSuccess) throw new IllegalMoveException(moveString);

        MoveInfo moveData = new MoveInfo(encoded_move);

        addMoveHistory(moveData);

        updateGameState();
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

        boolean isSuccess = MoveGenerator.makeMove(this.chessboard, encoded_move);
        if(!isSuccess) throw new IllegalMoveException(
                String.valueOf(sourceSquare) + targetSquare + (promotionType != PieceType.NONE ? promotionType : "")
        );

        MoveInfo moveData = new MoveInfo(encoded_move);

        addMoveHistory(moveData);

        updateGameState();
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
        boolean isSuccess = MoveGenerator.makeMove(this.chessboard, moveInfo.originEncodedData());
        if(!isSuccess) throw new IllegalMoveException(moveInfo.toString());

        MoveInfo moveData = new MoveInfo(moveInfo.originEncodedData());

        addMoveHistory(moveData);

        updateGameState();
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

        MoveInfo moveInfo;
        if (gameMode == GameMode.VARIATION) {
            moveInfo = currentNode.moveData;
            currentNode = currentNode.parent;
        } else {
            moveInfo = linearHistory.get(currentMoveIndex);
            currentMoveIndex--;
        }

        MoveGenerator.unmakeMove(chessboard, moveInfo.originEncodedData());
        updateGameState();

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
        if (!canRedo()) throw new EmptyMoveRedoException();

        MoveInfo moveInfo;
        if (gameMode == GameMode.VARIATION) {
            currentNode = currentNode.children.getFirst();
            moveInfo = currentNode.moveData;
        } else {
            currentMoveIndex++;
            moveInfo = linearHistory.get(currentMoveIndex);
        }

        boolean isSuccess = MoveGenerator.makeMove(this.chessboard, moveInfo.originEncodedData());
        if (!isSuccess) {
            if (gameMode == GameMode.VARIATION) {
                currentNode = currentNode.parent;
            } else {
                currentMoveIndex--;
            }
            throw new IllegalMoveException(moveInfo.toString());
        }

        updateGameState();
        return moveInfo;
    }

    /**
     * Remake (redo) move on this ChessGame (with Variation index) <br>
     * ONLY ON VARIATION MODE!
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
     * @throws VariationModeException - this method is variation mode only, but if this ChessGame is linear mode
     */
    public MoveInfo remakeMove(int variationIndex) {
        if (gameMode == GameMode.LINEAR) throw new VariationModeException();
        if (!canRedo()) throw new EmptyMoveRedoException();

        MoveInfo moveInfo;
        if (gameMode == GameMode.VARIATION) {
            if(currentNode.children.size() <= variationIndex) throw new VariationNotFoundException();

            currentNode = currentNode.children.get(variationIndex);
            moveInfo = currentNode.moveData;
        } else {
            currentMoveIndex++;
            moveInfo = linearHistory.get(currentMoveIndex);
        }

        boolean isSuccess = MoveGenerator.makeMove(this.chessboard, moveInfo.originEncodedData());
        if (!isSuccess) {
            if (gameMode == GameMode.VARIATION) {
                currentNode = currentNode.parent;
            } else {
                currentMoveIndex--;
            }
            throw new IllegalMoveException(moveInfo.toString());
        }

        updateGameState();
        return moveInfo;
    }

    /**
     * Get whether this position can undo
     *
     * @return whether this position can undo
     */
    public boolean canUndo() {
        if (gameMode == GameMode.VARIATION) {
            return currentNode != moveHistoryRoot;
        } else {
            return currentMoveIndex >= 0;
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
        if (gameMode == GameMode.VARIATION) {
            return !currentNode.children.isEmpty();
        } else {
            return currentMoveIndex < linearHistory.size() - 1;
        }
    }

    /**
     * Remake (redo) move on this ChessGame (with Variation index) <br>
     * ONLY ON VARIATION MODE!
     *
     * @param variationIndex variation index (if 0, goes main line)
     *
     * @return whether this position can redo
     *
     * @throws MoveNotFoundException - if current node (move) not found
     * @throws VariationModeException - this method is variation mode only, but if this ChessGame is linear mode
     */
    public boolean canRedo(int variationIndex) {
        if (gameMode == GameMode.LINEAR) throw new VariationModeException();
        if (currentNode == null) throw new MoveNotFoundException();
        return currentNode.children.size() > variationIndex;
    }

    /**
     * Get previous moves
     *
     * @return previous moves
     */
    public List<MoveInfo> getMoveHistory() {
        if (gameMode == GameMode.VARIATION) {
            List<MoveInfo> result = new ArrayList<>();
            MoveNode current = currentNode;
            while (current != null && current.moveData != null) {
                result.add(current.moveData);
                current = current.parent;
            }
            Collections.reverse(result);
            return Collections.unmodifiableList(result);
        } else {
            if (currentMoveIndex < 0) return Collections.emptyList();
            List<MoveInfo> result = new ArrayList<>(currentMoveIndex + 1);
            for (int i = 0; i <= currentMoveIndex; i++) {
                result.add(linearHistory.get(i));
            }
            return Collections.unmodifiableList(result);
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
     * Get captured piece
     *
     * @param isWhite if white, returns black captured piece. if black, returns white captured piece.
     * @return captured piece
     */
    public Map<PieceType, Integer> getCapturedPieces(boolean isWhite) {
        Map<PieceType, Integer> captured = new EnumMap<>(PieceType.class);

        // Get origin start pos and pieces

        String boardFen = startPositionFEN.split(" ")[0];

        int initPawn = 0, initKnight = 0, initBishop = 0, initRook = 0, initQueen = 0;

        char pawnChar   = isWhite ? 'p' : 'P';
        char knightChar = isWhite ? 'n' : 'N';
        char bishopChar = isWhite ? 'b' : 'B';
        char rookChar   = isWhite ? 'r' : 'R';
        char queenChar  = isWhite ? 'q' : 'Q';

        for (int i = 0; i < boardFen.length(); i++) {
            char c = boardFen.charAt(i);
            if (c == pawnChar) initPawn++;
            else if (c == knightChar) initKnight++;
            else if (c == bishopChar) initBishop++;
            else if (c == rookChar) initRook++;
            else if (c == queenChar) initQueen++;
        }

        if (isWhite) {
            captured.put(PieceType.PAWN, initPawn - BitBoardUtils.countBits(chessboard.getBitboardPiece(p)));
            captured.put(PieceType.KNIGHT, initKnight - BitBoardUtils.countBits(chessboard.getBitboardPiece(n)));
            captured.put(PieceType.BISHOP, initBishop - BitBoardUtils.countBits(chessboard.getBitboardPiece(b)));
            captured.put(PieceType.ROOK, initRook - BitBoardUtils.countBits(chessboard.getBitboardPiece(r)));
            captured.put(PieceType.QUEEN, initQueen - BitBoardUtils.countBits(chessboard.getBitboardPiece(q)));
        } else {
            captured.put(PieceType.PAWN, initPawn - BitBoardUtils.countBits(chessboard.getBitboardPiece(P)));
            captured.put(PieceType.KNIGHT, initKnight - BitBoardUtils.countBits(chessboard.getBitboardPiece(N)));
            captured.put(PieceType.BISHOP, initBishop - BitBoardUtils.countBits(chessboard.getBitboardPiece(B)));
            captured.put(PieceType.ROOK, initRook - BitBoardUtils.countBits(chessboard.getBitboardPiece(R)));
            captured.put(PieceType.QUEEN, initQueen - BitBoardUtils.countBits(chessboard.getBitboardPiece(Q)));
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
            boolean isBlackBishopOnLight = (chessboard.bitboards[B] & LIGHT_SQUARES) != 0;

            if (isWhiteBishopOnLight == isBlackBishopOnLight) {
                return true;
            }
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
        if(isCheckmate()) return GameOverReason.CHECKMATE;
        if(isStalemate()) return GameOverReason.STALEMATE;
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
        return new MoveInfo(ConvertStringMoveUtils.toLanMoveData(chessboard, san));
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
    }


    /**
     * Get game result
     *
     * @return game result
     */
    public GameResult getGameResult() {
        return this.gameResult;
    }

    /**
     * Get game over reason
     *
     * @return game over reason
     */
    public GameOverReason getGameoverReason() {
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
        if(gameMode == GameMode.VARIATION) {
            // Variation tree logic

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

            return;
        }

        if(gameMode == GameMode.LINEAR) {
            if (currentMoveIndex < linearHistory.size() - 1) {
                linearHistory.subList(currentMoveIndex + 1, linearHistory.size()).clear();
            }
            linearHistory.add(moveData);
            currentMoveIndex++;
        }
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
     * Get Root node on move history <br>
     *
     * @return Root node
     */
    public MoveNodeDTO getRootNode() {
        return MoveNodeDTO.from(moveHistoryRoot);
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
     * Update game result (state)
     */
    private void updateGameState() {
        if (this.currentNode.terminalReason != null) {
            this.gameResult = this.currentNode.terminalResult;
            this.gameoverReason = this.currentNode.terminalReason;
            this.headers.put("Result", PGNUtils.getGameResultString(this.gameResult));
            return;
        }

        this.gameoverReason = isGameOver();

        this.gameResult = switch (this.gameoverReason) {
            case CHECKMATE -> getTurn() ? GameResult.BLACK_WON : GameResult.WHITE_WON;
            case STALEMATE, THREEFOLD, FIFTYMOVES, INSUFFICIENTMATERIAL -> GameResult.DRAW;
            default -> GameResult.UNKNOWN;
        };

        this.headers.put("Result", PGNUtils.getGameResultString(this.gameResult));
    }

    /**
     * Load PGN on this ChessGame
     *
     * @param pgnString PGN data
     */
    public PGNGame loadPGN(String pgnString) {
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

        record VariationState(MoveNode node, Chessboard snapshotBoard) {}

        Stack<VariationState> variationStack = new Stack<>();

        movePGNString = movePGNString.replaceAll("\\s+", " ");

        movePGNString = movePGNString.replace("(", " ( ")
                .replace(")", " ) ")
                .replace("{", " { ")
                .replace("}", " } ");
        movePGNString = movePGNString.replaceAll("\\s+", " ").trim();

        Chessboard pgnChessboard;

        String[] tokens = movePGNString.split(" ");

        boolean isInsideComment = false;

        StringBuilder commentBuilder = new StringBuilder();

        if("1".equals(headers.get("SetUp")) && headers.containsKey("FEN")) {
            String startFEN = headers.get("FEN");
            pgnChessboard = new Chessboard(startFEN);
        } else {
            pgnChessboard = new Chessboard(Chessboard.start_position);
        }

        GameResult gameResult = GameResult.UNKNOWN;

        for (String token : tokens) {
            if(token.equals("{")) {
                isInsideComment = true;
                commentBuilder.setLength(0);
                continue;
            }

            if(token.equals("}")) {
                isInsideComment = false;

                currentNode.comment = commentBuilder.toString().trim();

                continue;
            }

            if (isInsideComment) {
                commentBuilder.append(token).append(" ");
                continue;
            }

            if (token.matches("^\\d+\\.+.*")) {
                token = token.replaceFirst("^\\d+\\.+", "");

                if (token.isEmpty()) {
                    continue;
                }
            }


            // number token like "1. e4" on "1."
            if (token.matches("\\d+\\.+")) {
                continue;
            }


            // Game result
            if (token.equals("1-0")) {
                gameResult = GameResult.WHITE_WON;

                continue;
            }
            if(token.equals("0-1")) {
                gameResult = GameResult.BLACK_WON;

                continue;
            }
            if(token.equals("1/2-1/2")) {
                gameResult = GameResult.DRAW;

                continue;
            }
            if(token.equals("*")) {
                continue;
            }


            if (token.startsWith("$")) {
                currentNode.nag = token;
                continue;
            }

            if (token.equals("(")) {
                variationStack.push(new VariationState(currentNode, new Chessboard(pgnChessboard)));
                currentNode = currentNode.parent;

                int moveToUnmake = currentNode.moveData.originEncodedData();
                MoveGenerator.unmakeMove(pgnChessboard, moveToUnmake);

                continue;
            }

            if (token.equals(")")) {
                VariationState state = variationStack.pop();

                currentNode = state.node;
                pgnChessboard = state.snapshotBoard;

                continue;
            }

            int moveData = ConvertStringMoveUtils.toLanMoveData(pgnChessboard, token);

            MoveGenerator.makeMove(pgnChessboard, moveData);

            MoveInfo moveInfo = new MoveInfo(moveData);

            MoveNode newNode = new MoveNode(moveInfo, currentNode);
            newNode.san = token;

            currentNode.children.add(newNode);

            currentNode = newNode;
        }

        String fenToLoad = headers.getOrDefault("FEN", Chessboard.start_position);
        ChessboardUtils.parseFen(this.chessboard, fenToLoad);

        MoveNodeDTO rootDTO = MoveNodeDTO.from(rootNode);

        this.moveHistoryRoot = rootNode;
        this.currentNode = rootNode;

        this.headers.clear();
        setDefaultHeaders();
        this.headers.putAll(headers);

        this.gameResult = gameResult;
        if (gameResult != GameResult.UNKNOWN) {
            MoveNode lastNode = getLastMainlineNode(rootNode);

            if (!ChessboardUtils.isCheckmate(pgnChessboard) && !ChessboardUtils.isStaleMate(pgnChessboard)) {
                lastNode.terminalResult = gameResult;
                lastNode.terminalReason = (gameResult == GameResult.DRAW) ?
                        GameOverReason.AGREEMENTDRAW : GameOverReason.RESIGNATION;
            }
        }

        updateGameState();

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

        updateGameState();

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
            String lan = node.moveData.toLanString(tempBoard.gameVariants);

            calculatedSan = ConvertStringMoveUtils.toSanString(tempBoard, lan);

            MoveGenerator.makeMove(tempBoard, node.moveData.originEncodedData());
        }

        List<MoveNodeDTO> childrenDTOs = new java.util.ArrayList<>();

        for (MoveNode child : node.children) {
            childrenDTOs.add(buildPGNTreeWithSan(child, new Chessboard(tempBoard)));
        }

        return new MoveNodeDTO(
                node.moveData,
                childrenDTOs,
                calculatedSan,
                node.nag,
                node.comment
        );
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
     * Get bitboard chessboard
     *
     * @return Chessboard
     */
    public Chessboard getChessboard() {
        return chessboard;
    }

    @Override
    public String toString() {
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
}

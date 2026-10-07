package com.pepero.jcb.api;

import com.pepero.jcb.api.dto.*;
import com.pepero.jcb.api.enums.*;
import com.pepero.jcb.api.exception.convert.ConvertMoveException;
import com.pepero.jcb.api.exception.game.*;
import com.pepero.jcb.api.exception.pgn.NodesOverflowException;
import com.pepero.jcb.core.*;
import com.pepero.jcb.core.encode.EncodeMove;
import com.pepero.jcb.core.hash.Zobrist;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only view of a {@link ChessGame}, passed to the action of
 * {@link ChessGame#read(java.util.function.Function)} and {@link ChessGame#read(java.util.function.Consumer)}. <p>
 *
 * Every method here only needs the read lock, so it is safe to call it while the read lock is already held.
 * Inside {@code read(...)} all of these observe the same position (no writer can interleave between them). <p>
 *
 * Methods that need the <b>write lock</b> internally are intentionally <b>not</b> part of this interface,
 * because calling them while holding the read lock would deadlock (read lock can't be upgraded to write lock):
 * <ul>
 *     <li>{@code getGameResult*}, {@code getGameOverReason*} (and the {@code *At} variants)</li>
 *     <li>{@code getPGN*}, {@code getMainlinePGN*}, {@code getPurePGN*}</li>
 * </ul>
 * If you need them together with other reads atomically, use {@link ChessGame#write(java.util.function.Function)}.
 */
public interface ChessGameReadView {

    // ---------------------------------------------------------------------------------------------
    // Position
    // ---------------------------------------------------------------------------------------------

    /**
     * Get FEN on this ChessGame (lichess dialect). <p>
     *
     * If you want to change dialect, go to {@link #getFEN(FENDialect)}
     *
     * @return fen (lichess dialect)
     */
    String getFEN();

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
    String getFEN(FENDialect dialect);

    /**
     * Get white turn
     *
     * @return true if it is white's turn, false if black's turn
     */
    boolean getTurn();

    /**
     * Get 'full move' on this ChessGame
     */
    int getFullMove();

    /**
     * Get 'half move' on this ChessGame
     */
    int getHalfMove();

    /**
     * Get the total number of plies played from the starting position to the current position. <br>
     * Starts at 0, incrementing by 1 after every move.
     *
     * @return current ply count
     */
    int getPly();

    /**
     * Get this position's internal {@link Zobrist} hash (JCB's own hashing scheme). <p>
     *
     * @return internal Zobrist hash of the current position
     */
    long getZobristHash();

    /**
     * Get this position's polyglot hash
     */
    long getPolyglotHash();

    /**
     * Get game start position fen <br>
     * if this ChessGame generated with {@code ChessGame.fromFEN(String)} methods, the result is the reset fen string
     */
    String getStartPositionFEN();

    /**
     * Get whether this ChessGame is chess960
     */
    boolean isChess960();

    /**
     * Get game variant
     */
    GameVariant getGameVariant();

    /**
     * Get current chess board copy (snapshot) <br>
     * This returns just a {@link Chessboard} class used on core logic
     */
    Chessboard getBoardSnapshot();

    /**
     * Get this board to ascii
     */
    String toAscii();

    // ---------------------------------------------------------------------------------------------
    // Board / pieces
    // ---------------------------------------------------------------------------------------------

    /**
     * Get board state Map(square)(piece) <br>
     * If the square is empty, doesn't contain on Map.
     */
    Map<Square, Piece> getBoardStateMap();

    /**
     * Get piece type on square
     * if not found, returns NONE
     *
     * @param square square
     */
    Piece getPieceOnSquare(Square square);

    /**
     * Get whether this square is empty
     *
     * @param square square
     */
    boolean isEmpty(Square square);

    /**
     * Get captured piece <br>
     * You can also get pocket data on CrazyHouse variant
     *
     * @param isWhite if white, returns black captured piece. if black, returns white captured piece.
     */
    Map<PieceType, Integer> getCapturedPieces(boolean isWhite);

    /**
     * Get piece score (For GUI showing / material comparison)
     */
    int getPieceScore();

    /**
     * Get castling rights info
     */
    CastlingRightsInfo getCastlingRights();

    // ---------------------------------------------------------------------------------------------
    // Moves
    // ---------------------------------------------------------------------------------------------

    /**
     * Get legal moves on this chess game
     */
    List<MoveInfo> getLegalMoves();

    /**
     * Generate moves only one source square
     * <p>
     * Example : chessboard = start pos, source = e2, returns e2e3, e2e4
     */
    List<MoveInfo> getLegalMovesForSource(Square source);

    /**
     * Generate moves only one target square
     * <p>
     * Example : chessboard = start pos, target = e4, returns e2e3, e2e4
     *
     * @return generated move
     */
    List<MoveInfo> getLegalMovesForTarget(Square target);

    /**
     * Get whether a LAN (or UCI) move string would be a legal move on this ChessGame,
     * without actually making it.
     *
     * @param lan move like "e2e4", "e7e5" (LAN (or UCI) move string)
     */
    boolean canMakeMoveLan(String lan);

    /**
     * Get whether a SAN move string would be a legal move on this ChessGame,
     * without actually making it.
     *
     * @param sanString san string like "e4", "Nf3"
     */
    boolean canMakeMoveSan(String sanString);

    /**
     * Get whether a move from sourceSquare to targetSquare (with the given promotion type)
     * would be a legal move, without actually making it.
     *
     * @param sourceSquare Source square
     * @param targetSquare Target square
     * @param promotionType Promotion type like queen, rook, bishop and knight ({@link PieceType#QUEEN}, {@link PieceType#ROOK} ... )
     */
    boolean canMakeMove(Square sourceSquare, Square targetSquare, PieceType promotionType);

    /**
     * Get whether a move from sourceSquare to targetSquare would be a legal move,
     * without actually making it.
     *
     * @param sourceSquare Source square
     * @param targetSquare Target square
     */
    boolean canMakeMove(Square sourceSquare, Square targetSquare);

    /**
     * Get whether this move is a promotion move
     *
     * @param source source square
     * @param target target square
     */
    boolean shouldPromotion(Square source, Square target);

    /**
     * Get whether this drop move (crazy house) is a legal move
     *
     * @param pieceType dropping piece type
     * @param target target square
     */
    boolean canDropPiece(PieceType pieceType, Square target);

    // ---------------------------------------------------------------------------------------------
    // Check / game state
    // ---------------------------------------------------------------------------------------------

    /**
     * Get whether the king is under attack
     */
    boolean isCheck();

    /**
     * Get checking piece (king attacker) <br>
     * The max size of this return list is 2.
     */
    List<Square> getChecker();

    /**
     * Get whether this square is attacked
     *
     * @param square square
     * @param side attacking side (if true, white is attacking, black otherwise)
     * @return true if square is attacked, false otherwise
     */
    boolean isSquareAttacked(Square square, boolean side);

    /**
     * Get whether this position is checkmate
     */
    boolean isCheckmate();

    /**
     * Get whether this position is stalemate
     */
    boolean isStalemate();

    /**
     * Get whether this position is insufficient material
     */
    boolean isInsufficientMaterial();

    /**
     * Get whether this game overed. <br>
     * If not, return <b>GameOverReason.NOTGAMEOVER</b>.
     * <br>
     * this includes claimable draws like fifty moves draw claim, threefold repetition draw claim.
     *
     * @return game over reason (if not, return GameOverReason.NOTGAMEOVER)
     */
    GameOverReason isGameOver();

    /**
     * Get whether this game overed. <br>
     * If not, return <b>GameOverReason.NOTGAMEOVER</b>.
     *
     * @param includeClaimableDraws include claimable draws like 50 moves draw claim, threefold repetition claim
     *
     * @return game over reason (if not, return GameOverReason.NOTGAMEOVER)
     */
    GameOverReason isGameOver(boolean includeClaimableDraws);

    /**
     * Get whether this position can be claimed draw
     */
    boolean canClaimDraw();

    /**
     * Get claimable draw reason <br>
     * like 50 moves draw claim, threefold draw claim
     */
    GameOverReason getClaimableDrawReason();

    /**
     * Get 3 check 'check count' <br>
     * the first index is white's checked count, <br>
     * the second index is black's checked count.
     *
     * @return checked count for each white/black
     * @throws VariantNotMatchException if variant isn't three check
     */
    int[] getCheckCount();

    /**
     * Get 3 check 'white's checked count' <br>
     *
     * @return checked count for white
     * @throws VariantNotMatchException if variant isn't three check
     */
    int getWhiteCheckedCount();

    /**
     * Get 3 check 'black's checked count' <br>
     *
     * @return checked count for black
     * @throws VariantNotMatchException if variant isn't three check
     */
    int getBlackCheckedCount();

    // ---------------------------------------------------------------------------------------------
    // Move conversion (based on the current position)
    // ---------------------------------------------------------------------------------------------

    /**
     * Convert LAN (like e2e4 e7e5 g1f3) to SAN (like e4 e5 Nf3)
     *
     * @param lanMove LAN move
     * @return converted SAN move
     *
     * @throws ConvertMoveException when converting move failed
     * @throws IllegalMoveException if move is illegal
     */
    String toSan(String lanMove);

    /**
     * Convert move data to SAN (like e4 e5 Nf3)
     *
     * @param moveData moves data
     * @return converted SAN move
     *
     * @throws ConvertMoveException if converting move failed
     * @throws IllegalMoveException if move is illegal
     */
    String toSan(List<MoveInfo> moveData);

    /**
     * Convert move data to SAN (like e4 e5 Nf3)
     *
     * @param moveData move data
     * @return converted SAN move
     *
     * @throws IllegalMoveException if move is illegal
     */
    String toSan(MoveInfo moveData);

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
    String toSan(int[] encodedMoves);

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
    String toSan(int encodedMove);

    /**
     * Translate SAN string to LAN string
     *
     * @param san SAN move
     * @return Translated string result
     *
     * @throws ConvertMoveException if converting move failed
     */
    String toLanString(String san);

    /**
     * Translate SAN string to MoveInfo
     *
     * @param san SAN move
     * @return Translated move data result
     *
     * @throws ConvertMoveException if converting move failed
     * @throws IllegalMoveException if move is illegal
     */
    MoveInfo sanToMoveData(String san);

    /**
     * Translate LAN string to MoveInfo
     *
     * @param lan LAN move
     * @return Translated move data result
     *
     * @throws ConvertMoveException if converting move failed
     * @throws IllegalMoveException if move is illegal
     */
    MoveInfo lanToMoveData(String lan);

    // ---------------------------------------------------------------------------------------------
    // History / navigation
    // ---------------------------------------------------------------------------------------------

    /**
     * Get whether this position can undo
     *
     * @return whether this position can undo
     */
    boolean canUndo();

    /**
     * Get whether this position can redo
     *
     * @return whether this position can redo
     *
     * @throws IllegalStateException if current node (move) not found
     */
    boolean canRedo();

    /**
     * Get whether this position can redo with the variation index
     *
     * @param variationIndex variation index (if 0, goes main line)
     *
     * @return whether this position can redo
     *
     * @throws IllegalStateException if current node (move) not found
     */
    boolean canRedo(int variationIndex);

    /**
     * Get current node's long id
     */
    long getCurrentNodeId();

    /**
     * Get current move info. <br>
     * If current node is root node, returns null.
     */
    MoveInfo getCurrentMoveInfo();

    /**
     * Get node IDs of all children of the current node.
     */
    List<Long> getChildNodeIds();

    /**
     * Get node move infos of all children of the current node.
     */
    List<MoveInfo> getChildMoveInfos();

    /**
     * Get the move path from the root to the current pointer position. <p>
     * Example: <br>
     * <b>e2e4 e7e5 g1f3 ( b1c3 &lt;- pointer ) b8c6 ) g8f6</b> <br>
     * result: <b>e2e4 e7e5 b1c3</b>
     */
    List<MoveInfo> getPathToCurrentNode();

    /**
     * Generate new MoveNodeDTO with san move data
     *
     * @throws NodesOverflowException if move count is too large
     */
    MoveNodeDTO getRootNode();

    /**
     * Generate new MoveNodeDTO with san move data
     *
     * @param maxNodesCount max nodes count
     *
     * @throws NodesOverflowException if move count is more than maxNodesCount
     */
    MoveNodeDTO getRootNode(int maxNodesCount);

    /**
     * Get mainline move data
     * <p>
     * Example : <br>
     * history -> <b>e4 e5 Nf3 (Nc3) Nc6</b> <br>
     * and the result is <br>
     * <b>e4 e5 Nf3 Nc6</b>
     *
     * @throws NodesOverflowException if move count is too large (you can adjust by {@link #getMainlineData(int)})
     */
    List<MoveDataDTO> getMainlineData();

    /**
     * Get mainline move data
     * <p>
     * Example : <br>
     * history -> <b>e4 e5 Nf3 (Nc3) Nc6</b> <br>
     * and the result is <br>
     * <b>e4 e5 Nf3 Nc6</b>
     *
     * @param maxNodes max main line data size (if size is bigger than this max nodes, throw exception.)
     *
     * @throws NodesOverflowException if size is bigger than this max nodes
     */
    List<MoveDataDTO> getMainlineData(int maxNodes);

    /**
     * Get headers like Event, Site, etc.
     *
     * @return header map (String, value)
     */
    LinkedHashMap<String, String> getHeaders();
}
package com.pepero.jcb.api;

import com.pepero.jcb.api.dto.*;
import com.pepero.jcb.api.enums.*;
import com.pepero.jcb.core.GameVariant;

import java.util.List;
import java.util.Map;

/**
 * Read-only view of a {@link ChessGame}, only valid inside {@link ChessGame#read(java.util.function.Function)}. <p>
 *
 * Every method here must only take the <b>read lock</b> (or no lock) internally.
 */
public interface ChessGameReadView {
    String getFEN();

    long getZobristHash();

    boolean getTurn();

    boolean isCheck();

    List<MoveInfo> getLegalMoves();

    Map<Square, Piece> getBoardStateMap();

    int getPieceScore();

    int getPly();

    int getHalfMove();

    int getFullMove();

    boolean canUndo();

    boolean canRedo();

    long getCurrentNodeId();

    boolean isCheckmate();

    boolean isStalemate();

    boolean isInsufficientMaterial();

    CastlingRightsInfo getCastlingRights();

    List<Square> getChecker();

    MoveInfo getCurrentMoveInfo();

    GameVariant getGameVariant();

    Map<PieceType, Integer> getCapturedPieces(boolean isWhite);

    List<MoveInfo> getLegalMovesForSource(Square source);

    boolean isSquareAttacked(Square square, boolean side);

    boolean canMakeMoveLan(String lan);

    boolean canMakeMoveSan(String sanString);

    boolean canMakeMove(Square sourceSquare, Square targetSquare, PieceType promotionType);
}
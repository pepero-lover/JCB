package com.pepero.jcb.api;

import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.api.enums.GameResult;

/**
 * Listener for {@link ChessGame} update like {@link #onMoveMade(ChessGame, MoveInfo)},
 * {@link #onGameOver(ChessGame, GameResult, GameOverReason)} <br>
 * You can use this on {@link ChessGame#addChessGameListener(ChessGameListener)}
 */
public interface ChessGameListener {
    /**
     * When moved
     * @param moveInfo moved info
     */
    default void onMoveMade(ChessGame source, MoveInfo moveInfo) {}

    /**
     * When move unmade
     * @param unmadeMoveInfo unmade move info
     */
    default void onMoveUnmade(ChessGame source, MoveInfo unmadeMoveInfo) {}

    /**
     * When move remade
     * @param remadeMoveInfo remade move info
     */
    default void onMoveRemade(ChessGame source, MoveInfo remadeMoveInfo) {}

    /**
     * When jump to node (pgn move) is called
     *
     * @param targetFen jumped node position
     */
    default void onPositionJumped(ChessGame source, String targetFen) {}

    /**
     * When the game ended
     *
     * @param result win/lose/draw result
     * @param reason game over reason (like checkmate, stalemate, agreement draw, etc.)
     */
    default void onGameOver(ChessGame source, GameResult result, GameOverReason reason) {}

    /**
     * When game state checked (always called when moved, jumped, forced to end the game, etc.)
     *
     * @param result win/lose/draw result
     * @param reason game over reason (like checkmate, stalemate, agreement draw, etc.)
     */
    default void onGameStateChecked(ChessGame source, GameResult result, GameOverReason reason) {}

    /**
     * When game history changed
     */
    default void onHistoryChanged(ChessGame source) {}
}
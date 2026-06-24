package com.pepero.jcb.api.event;

import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.api.enums.GameResult;
import com.pepero.jcb.api.enums.Square;

public interface ChessGameListener {
    /**
     * When moved
     * @param moveInfo moved info
     */
    void onMoveMade(MoveInfo moveInfo);

    /**
     * When move unmade
     * @param unmadeMoveInfo unmade move info
     */
    void onMoveUnmade(MoveInfo unmadeMoveInfo);

    /**
     * When move remade
     * @param remadeMoveInfo remade move info
     */
    void onMoveRemade(MoveInfo remadeMoveInfo);

    /**
     * When jump to node (pgn move) is called
     *
     * @param targetFen jumped node position
     */
    void onPositionJumped(String targetFen);

    /**
     * When the game ended
     * @param result win/lose/draw result
     * @param reason game over reason (like checkmate, stalemate, agreement draw, etc.)
     */
    void onGameOver(GameResult result, GameOverReason reason);
}
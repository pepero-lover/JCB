package com.pepero.jcb.api.event;

import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.api.enums.GameResult;

public interface ChessGameListener {
    /**
     * When moved
     * @param moveInfo moved info
     */
    void onMoveMade(MoveInfo moveInfo);

    /**
     * When game ended
     * @param result win/lose/draw result
     * @param reason game over reason (like checkmate, stalemate etc.)
     */
    void onGameOver(GameResult result, GameOverReason reason);
}
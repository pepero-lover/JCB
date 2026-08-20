package com.pepero.jcb.api;

import com.pepero.jcb.api.analyze.ChessTacticUtils;
import com.pepero.jcb.api.analyze.TacticAnalyzer;
import com.pepero.jcb.api.analyze.TacticFinding;
import com.pepero.jcb.api.enums.Square;

import java.util.List;

public class GameTacticAnalyzer {

    /**
     * Find tactics and return
     *
     * @param game chess game
     * @param whiteAttacking is white attacking
     * @return tactics dto(s)
     */
    public static List<TacticFinding> findTactics(ChessGame game, boolean whiteAttacking) {
        return TacticAnalyzer.findAllTactics(game.getBoardSnapshot(), whiteAttacking);
    }

    /**
     * Find immediate tactics and return
     *
     * @param game chess game
     * @param whiteAttacking is white attacking
     * @return tactics dto(s)
     */
    public static List<TacticFinding> findImmediateTactics(ChessGame game, boolean whiteAttacking) {
        return TacticAnalyzer.findImmediateThreats(game.getBoardSnapshot(), whiteAttacking);
    }

    /**
     * Get Hanging pieces square
     *
     * @param game chess game
     * @param whiteAttacking if true, get black's hanging pieces. otherwise, get white's hanging pieces.
     * @return hanging pieces square
     */
    public static List<Square> findHangingPieces(ChessGame game, boolean whiteAttacking) {
        return ChessTacticUtils.findHangingPieces(game.getBoardSnapshot(), whiteAttacking);
    }
}
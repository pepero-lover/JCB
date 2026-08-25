package com.pepero.jcb.api;

import com.pepero.jcb.api.analyze.ChessTacticUtils;
import com.pepero.jcb.api.analyze.TacticAnalyzer;
import com.pepero.jcb.api.analyze.TacticFinding;
import com.pepero.jcb.api.analyze.TacticType;
import com.pepero.jcb.api.enums.Square;
import com.pepero.jcb.core.Chessboard;

import java.util.List;

/**
 * Get game tactic on this position. <br>
 * This tactic analyzer finds tactics like {@link TacticType#PIN}, {@link TacticType#FORK}. <br>
 * The difference between {@link TacticAnalyzer} and this is the {@link TacticAnalyzer} uses {@link Chessboard}
 * class, but this class uses {@link ChessGame} class.
 */
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
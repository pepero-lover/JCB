package com.pepero.jcb.api;

import com.pepero.jcb.api.dto.GaviotaMoveDTO;
import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.exception.VariantNotMatchException;
import com.pepero.jcb.api.gaviota.GaviotaTablebase;
import com.pepero.jcb.core.constant.MoveCache;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.GameVariant;
import com.pepero.jcb.core.MoveGenerator;

import java.util.ArrayList;
import java.util.List;

public class GaviotaAnalyzer {

    /**
     * Get DTM (distance to mate, in half-moves) result on this chess position <br>
     * supports only Standard chess (Chess 960 included)
     * <p>
     * Unlike Syzygy's WDL/DTZ split, Gaviota tables encode a single signed
     * mate distance directly: positive if the side to move is winning,
     * negative if losing, 0 if drawn. See {@link GaviotaTablebase#probeDtm}.
     *
     * @param tablebase table base class
     * @param containCastle do not throw exception when game has castling rights <br>
     *                      Warning : Gaviota tables do not contain any position with
     *                      castling rights, so probing one will still fail inside
     *                      {@link GaviotaTablebase#probeDtm} even with this enabled.
     * @return DTM result
     *
     * @throws VariantNotMatchException if variant isn't standard chess
     * @throws IllegalArgumentException if this position has castling rights, or has more than 5 pieces
     * @throws GaviotaTablebase.MissingTableException if no table file covers this material
     */
    public static int probeDtm(ChessGame game, GaviotaTablebase tablebase, boolean containCastle) {
        validateVariant(game);
        if (!containCastle) validateCastling(game);
        return tablebase.probeDtm(game.getBoardSnapshot());
    }

    /**
     * Get DTM (distance to mate, in half-moves) result on this chess position <br>
     * supports only Standard chess (Chess 960 included)
     *
     * @param tablebase table base class
     * @return DTM result
     *
     * @throws VariantNotMatchException if variant isn't standard chess
     * @throws IllegalArgumentException if this position has castling rights, or has more than 5 pieces
     * @throws GaviotaTablebase.MissingTableException if no table file covers this material
     */
    public static int probeDtm(ChessGame game, GaviotaTablebase tablebase) {
        return probeDtm(game, tablebase, false);
    }

    /**
     * Get WDL result on this chess position <br>
     * supports only Standard chess (Chess 960 included)
     *
     * @param tablebase table base class
     * @param containCastle do not throw exception when game has castling rights <br>
     *                      Warning : Gaviota tables do not contain any position with
     *                      castling rights, so probing one will still fail inside
     *                      {@link GaviotaTablebase#probeWdl} even with this enabled.
     * @return WDL result, on a -1..1 scale (Loss/Draw/Win)
     *
     * @throws VariantNotMatchException if variant isn't standard chess
     * @throws IllegalArgumentException if this position has castling rights, or has more than 5 pieces
     * @throws GaviotaTablebase.MissingTableException if no table file covers this material
     */
    public static int probeWdl(ChessGame game, GaviotaTablebase tablebase, boolean containCastle) {
        validateVariant(game);
        if (!containCastle) validateCastling(game);
        return tablebase.probeWdl(game.getBoardSnapshot());
    }

    /**
     * Get WDL result on this chess position <br>
     * supports only Standard chess (Chess 960 included)
     *
     * @param tablebase table base class
     * @return WDL result, on a -1..1 scale (Loss/Draw/Win)
     *
     * @throws VariantNotMatchException if variant isn't standard chess
     * @throws IllegalArgumentException if this position has castling rights, or has more than 5 pieces
     * @throws GaviotaTablebase.MissingTableException if no table file covers this material
     */
    public static int probeWdl(ChessGame game, GaviotaTablebase tablebase) {
        return probeWdl(game, tablebase, false);
    }

    /**
     * Get the best move based on Gaviota tablebase <br>
     * if is checkmate or stalemate, return null
     *
     * @param tablebase Gaviota tablebase
     * @param containCastle do not throw exception when game has castling rights <br>
     *                      Warning : Gaviota tables do not contain any position with
     *                      castling rights, so ranking will still fail on any move
     *                      leading to a &gt;5-piece or castling-flagged child position.
     * @return best move
     */
    public static MoveInfo findBestMove(ChessGame game, GaviotaTablebase tablebase, boolean containCastle) {
        List<GaviotaMoveDTO> bestMoves = findRankedMoves(game, tablebase, containCastle);
        if (bestMoves.isEmpty()) return null;
        return bestMoves.getFirst().move();
    }

    /**
     * Get the best move based on Gaviota tablebase <br>
     * if is checkmate or stalemate, return null
     *
     * @param tablebase Gaviota tablebase
     * @return best move
     */
    public static MoveInfo findBestMove(ChessGame game, GaviotaTablebase tablebase) {
        return findBestMove(game, tablebase, false);
    }

    /**
     * Get sorted moves based on Gaviota tablebase (first is best move, last is worst move)
     * <p>
     * WDL scale used here is -1~1 (Loss..Win), matching {@link GaviotaTablebase#probeWdl}.
     * Unlike {@code SyzygyAnalyzer.findRankedMoves}, distance here is the raw
     * mate distance Gaviota tables store directly, so there is no separate
     * zeroing/50-move-rule threshold to apply.
     *
     * @param tablebase Gaviota table base
     * @param containCastle do not throw exception when game has castling rights <br>
     *                      Warning : Gaviota tables do not contain any position with
     *                      castling rights, so ranking will still fail on any move
     *                      leading to a &gt;5-piece or castling-flagged child position.
     *
     * @return sorted moves list
     */
    public static List<GaviotaMoveDTO> findRankedMoves(ChessGame game, GaviotaTablebase tablebase, boolean containCastle) {
        validateVariant(game);
        if (!containCastle) validateCastling(game);

        Chessboard board = game.getBoardSnapshot();

        int[] moveArray = new int[MoveCache.MAX_MOVE_SIZE];
        int moveCount = MoveGenerator.generateMoves(board, moveArray);

        if (moveCount == 0) return List.of();

        List<GaviotaMoveDTO> ranked = new ArrayList<>();

        for (int i = 0; i < moveCount; i++) {
            int move = moveArray[i];

            MoveGenerator.makeMove(board, move);

            int childWdl = tablebase.probeWdl(board);
            int ourWdl = -childWdl;
            int distance = (ourWdl == 0) ? 0 : Math.abs(tablebase.probeDtm(board));

            ranked.add(new GaviotaMoveDTO(new MoveInfo(move), ourWdl, distance));

            MoveGenerator.unmakeMove(board, move);
        }

        ranked.sort((a, b) -> {
            if (a.ourWdl() != b.ourWdl()) return b.ourWdl() - a.ourWdl();
            if (a.ourWdl() > 0) return a.distance() - b.distance();
            if (a.ourWdl() < 0) return b.distance() - a.distance();
            return 0;
        });

        return ranked;
    }

    /**
     * Get sorted moves based on Gaviota tablebase (first is best move, last is worst move)
     * <p>
     * WDL scale used here is -1~1 (Loss..Win), matching {@link GaviotaTablebase#probeWdl}.
     *
     * @param tablebase Gaviota table base
     *
     * @return sorted moves list
     */
    public static List<GaviotaMoveDTO> findRankedMoves(ChessGame game, GaviotaTablebase tablebase) {
        return findRankedMoves(game, tablebase, false);
    }

    private static void validateVariant(ChessGame game) {
        if (game.getGameVariant() != GameVariant.STANDARD) {
            throw new VariantNotMatchException("Gaviota tables only support Standard chess (including Chess 960)!");
        }
    }

    private static void validateCastling(ChessGame game) {
        if (game.hasCastling()) throw new IllegalArgumentException("Gaviota should not contain Castling rights!");
    }
}
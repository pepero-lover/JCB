package com.pepero.jcb.api;

import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.dto.SyzygyMoveDTO;
import com.pepero.jcb.api.exception.VariantNotMatchException;
import com.pepero.jcb.api.gaviota.GaviotaTablebase;
import com.pepero.jcb.api.syzygy.SyzygyTablebase;
import com.pepero.jcb.core.constant.MoveCache;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.ChessboardUtils;
import com.pepero.jcb.core.GameVariant;
import com.pepero.jcb.core.MoveGenerator;
import com.pepero.jcb.core.encode.EncodeMove;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.pepero.jcb.core.constant.EncodedPieces.P;
import static com.pepero.jcb.core.constant.EncodedPieces.p;

/**
 * Probe and get the DTZ (Distance to zero) / WDL (Win Draw Loss) data. <br>
 * If you want to use Gaviota Analyzer, go to {@link GaviotaAnalyzer}.
 * This uses {@link ChessGame} to probe the data, if you want to probe the data with {@link Chessboard},
 * go to {@link SyzygyTablebase}.
 */
public class SyzygyAnalyzer {

    /**
     * Get WDL result on this chess position <br>
     * supports only Standard chess, Chess 960 chess
     *
     * @param tablebase table base class
     * @param containCastle do not throw exception when game has castling rights <br>
     *                      Warning : if you enable this, the position that contained castling rights
     *                      wdl probing might be inaccurate.
     * @return WDL result
     *
     * @throws VariantNotMatchException if variant isn't standard chess or chess 960
     * @throws IllegalArgumentException if this position has castling right
     */
    public static int probeWdl(ChessGame game, SyzygyTablebase tablebase, boolean containCastle) throws IOException {
        validateVariant(game);
        if(!containCastle) validateCastling(game);
        return tablebase.getWdlData(game.getBoardSnapshot());
    }

    /**
     * Get WDL result on this chess position <br>
     * supports only Standard chess, Chess 960 chess
     *
     * @param tablebase table base class
     * @return WDL result
     *
     * @throws VariantNotMatchException if variant isn't standard chess or chess 960
     * @throws IllegalArgumentException if this position has castling right
     */
    public static int probeWdl(ChessGame game, SyzygyTablebase tablebase) throws IOException {
        return probeWdl(game, tablebase, false);
    }

    /**
     * Get DTZ result on this chess position <br>
     * supports only Standard chess, Chess 960 chess <br>
     * Note: the returned value is signed — positive means the side to move is winning,
     * negative means the side to move is losing (matches standard Syzygy DTZ convention).
     * A value of 0 means a draw.
     *
     * @param tablebase table base class
     * @param containCastle do not throw exception when game has castling rights <br>
     *                      Warning : if you enable this, the position that contained castling rights
     *                      wdl probing might be inaccurate.
     * @return signed DTZ result
     *
     * @throws VariantNotMatchException if variant isn't standard chess or chess 960
     * @throws IllegalArgumentException if this position has castling right
     */
    public static int probeDtz(ChessGame game, SyzygyTablebase tablebase, boolean containCastle) throws IOException {
        validateVariant(game);
        if(!containCastle) validateCastling(game);
        return tablebase.getDtzData(game.getBoardSnapshot());
    }

    /**
     * Get DTZ result on this chess position <br>
     * supports only Standard chess, Chess 960 chess <br>
     * Note: the returned value is signed — positive means the side to move is winning,
     * negative means the side to move is losing (matches standard Syzygy DTZ convention).
     * A value of 0 means a draw.
     *
     * @param tablebase table base class
     * @return signed DTZ result
     *
     * @throws VariantNotMatchException if variant isn't standard chess or chess 960
     * @throws IllegalArgumentException if this position has castling right
     */
    public static int probeDtz(ChessGame game, SyzygyTablebase tablebase) throws IOException {
        return probeDtz(game, tablebase, false);
    }

    /**
     * Get the best move based on Syzygy tablebase <br>
     * if is checkmate or stalemate, return null
     *
     * @param tablebase Syzygy tablebase
     * @param containCastle do not throw exception when game has castling rights <br>
     *                      Warning : if you enable this, the position that contained castling rights
     *                      wdl probing might be inaccurate.
     * @return best move
     * @throws IOException if tablebase could not find or something
     */
    public static MoveInfo findBestMove(ChessGame game, SyzygyTablebase tablebase, boolean containCastle) throws IOException {
        List<SyzygyMoveDTO> bestMoves = findRankedMoves(game, tablebase, containCastle);
        if (bestMoves.isEmpty()) return null;
        return bestMoves.getFirst().move();
    }

    /**
     * Get the best move based on Syzygy tablebase <br>
     * if is checkmate or stalemate, return null
     *
     * @param tablebase Syzygy tablebase
     *
     * @return best move
     * @throws IOException if tablebase could not find or something
     */
    public static MoveInfo findBestMove(ChessGame game, SyzygyTablebase tablebase) throws IOException {
        return findBestMove(game, tablebase, false);
    }

    /**
     * Get Sorted moves based on Syzygy tablebase (first is best move, last is worst move)
     * <p>
     * WDL scale used here is -2~2 (Loss..Win), matching {@link SyzygyTablebase#getWdlData}.
     *
     * @param tablebase Syzygy table base
     * @param containCastle do not throw exception when game has castling rights <br>
     *                      Warning : if you enable this, the position that contained castling rights
     *                      wdl probing might be inaccurate.
     *
     * @return sorted moves list
     * @throws IOException if tablebase could not find or something
     */
    public static List<SyzygyMoveDTO> findRankedMoves(ChessGame game, SyzygyTablebase tablebase, boolean containCastle) throws IOException {
        validateVariant(game);
        if(!containCastle) validateCastling(game);

        Chessboard board = game.getBoardSnapshot();
        int halfMoveClock = game.getHalfMove();

        int[] moveArray = new int[MoveCache.MAX_MOVE_SIZE];
        int moveCount = MoveGenerator.generateMoves(board, moveArray);

        if (moveCount == 0) return List.of();

        List<SyzygyMoveDTO> ranked = new ArrayList<>();

        for (int i = 0; i < moveCount; i++) {
            int move = moveArray[i];
            boolean zeroing = EncodeMove.getMoveCapture(move)
                    || EncodeMove.getMovePiece(move) == P
                    || EncodeMove.getMovePiece(move) == p;

            MoveGenerator.makeMove(board, move);

            boolean triggersRepetition = ChessboardUtils.getRepetitionCount(board, 2) >= 2;
            int childWdl = tablebase.getWdlData(board);
            int ourWdl = triggersRepetition ? 0 : -childWdl;
            int distance = (ourWdl == 0) ? 0 : (zeroing ? 0 : Math.abs(tablebase.getDtzData(board)));

            if (!zeroing && (halfMoveClock + distance > 100)) {
                if (ourWdl == 2) ourWdl = 1;
                else if (ourWdl == -2) ourWdl = -1;
            }

            ranked.add(new SyzygyMoveDTO(new MoveInfo(move), ourWdl, distance));

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
     * Get Sorted moves based on Syzygy tablebase (first is best move, last is worst move)
     * <p>
     * WDL scale used here is -2~2 (Loss..Win), matching {@link SyzygyTablebase#getWdlData}.
     *
     * @param tablebase Syzygy table base
     *
     * @return sorted moves list
     * @throws IOException if tablebase could not find or something
     */
    public static List<SyzygyMoveDTO> findRankedMoves(ChessGame game, SyzygyTablebase tablebase) throws IOException {
        return findRankedMoves(game, tablebase, false);
    }

    private static void validateVariant(ChessGame game) {
        if (game.getGameVariant() != GameVariant.STANDARD && game.getGameVariant() != GameVariant.ATOMIC &&
            game.getGameVariant() != GameVariant.GIVEAWAY && game.getGameVariant() != GameVariant.SUICIDE) {
            throw new VariantNotMatchException("Variant should be Standard chess or Chess 960!");
        }
    }

    private static void validateCastling(ChessGame game) {
        if(game.hasCastling()) throw new IllegalArgumentException("Syzygy should not contain Castling rights!");
    }
}
package com.pepero.jcb.api;

import com.pepero.jcb.api.dto.CombinedMoveDTO;
import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.dto.SyzygyMoveDTO;
import com.pepero.jcb.api.exception.game.VariantNotMatchException;
import com.pepero.jcb.api.exception.tablebase.TablebaseMissingFileException;
import com.pepero.jcb.api.exception.tablebase.TablebaseUnsupportedMaterialException;
import com.pepero.jcb.api.gaviota.GaviotaTablebase;
import com.pepero.jcb.api.syzygy.SyzygyTablebase;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.GameVariant;
import com.pepero.jcb.core.MoveGenerator;
import com.pepero.jcb.core.constant.MoveCache;
import com.pepero.jcb.core.encode.EncodeMove;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.pepero.jcb.core.constant.EncodedPieces.*;
import static com.pepero.jcb.core.constant.SideToMove.*;

public class CombinedAnalyzer {

    /**
     * Get Sorted moves based on Syzygy/Gaviota tablebase (first is best move, last is worst move) <br>
     * The order logic is under below. <br>
     * If {@code the calculated DTM + current half move clock} is more than 100, uses dtz to sort data. <br>
     * Otherwise, uses dtm to sort data.
     * <p>
     * WDL scale used here is -2~2 (Loss..Win), matching {@link SyzygyTablebase#getWdlData}. <br>
     * DTZ data is always contained, but the DTM data isn't contained when the game variant is not standard, or total piece count
     * is more than 5.
     *
     * @param containCastle do not throw exception when game has castling rights <br>
     *                      Warning : if you enable this, the position that contained castling rights
     *                      wdl probing might be inaccurate.
     * @return sorted move list
     *
     * @throws VariantNotMatchException if variant isn't standard chess, chess 960, suicide, giveaway, atomic
     * @throws TablebaseUnsupportedMaterialException if the position has castling rights (ignored when the 'containCastle' variable disabled)
     *         or this position's piece count is more than this class's supporting piece count
     * @throws TablebaseMissingFileException if no table file covers this material
     *         (for the original position, or for a position reached by playing
     *         a capture or promotion from it during probing)
     */
    public static List<CombinedMoveDTO> findRankedMoves(ChessGame game, SyzygyTablebase syzygy,
                                                        GaviotaTablebase gaviota, boolean containCastle)
            throws IOException {
        validateVariant(game);
        if (!containCastle) validateCastling(game);

        Chessboard board = game.getBoardSnapshot();
        int halfMoveClock = game.getHalfMove();

        int[] moveArray = new int[MoveCache.MAX_MOVE_SIZE];
        int moveCount = MoveGenerator.generateMoves(board, moveArray);
        if (moveCount == 0) return List.of();

        List<CombinedMoveDTO> ranked = new ArrayList<>();

        for (int i = 0; i < moveCount; i++) {
            int move = moveArray[i];
            boolean zeroing = EncodeMove.getMoveCapture(move)
                    || EncodeMove.getMovePiece(move) == P || EncodeMove.getMovePiece(move) == p;

            MoveGenerator.makeMove(board, move);

            SyzygyMoveDTO s = SyzygyAnalyzer.scoreMove(board, syzygy, move, zeroing, halfMoveClock);
            Integer rawDtm = tryGetDtm(board, gaviota);
            Integer dtm = (rawDtm == null) ? null : Math.abs(rawDtm);

            ranked.add(new CombinedMoveDTO(new MoveInfo(move), s.ourWdl(), s.distance(), zeroing, s.mate(), dtm));

            MoveGenerator.unmakeMove(board, move);
        }

        ranked.sort((a, b) -> {
            if (a.wdl() != b.wdl()) return b.wdl() - a.wdl();

            int da = (a.dtm() != null) ? a.dtm() : a.dtz();
            int db = (b.dtm() != null) ? b.dtm() : b.dtz();

            if (a.wdl() > 0) {
                if (a.mate() != b.mate()) return a.mate() ? -1 : 1;
                return da - db;
            }
            if (a.wdl() < 0) return db - da;
            return 0;
        });

        return ranked;
    }

    /**
     * Get Sorted moves based on Syzygy/Gaviota tablebase (first is best move, last is worst move) <br>
     * The order logic is under below. <br>
     * If {@code the calculated DTM + current half move clock} is more than 100, uses dtz to sort data. <br>
     * Otherwise, uses dtm to sort data.
     * <p>
     * WDL scale used here is -2~2 (Loss..Win), matching {@link SyzygyTablebase#getWdlData}. <br>
     * DTZ data is always contained, but the DTM data isn't contained when the game variant is not standard, or total piece count
     * is more than 5.
     *
     * @return sorted move list
     *
     * @throws VariantNotMatchException if variant isn't standard chess, chess 960, suicide, giveaway, atomic
     * @throws TablebaseUnsupportedMaterialException if the position has castling rights (ignored when the 'containCastle' variable disabled)
     *         or this position's piece count is more than this class's supporting piece count
     * @throws TablebaseMissingFileException if no table file covers this material
     *         (for the original position, or for a position reached by playing
     *         a capture or promotion from it during probing)
     */
    public static List<CombinedMoveDTO> findRankedMoves(ChessGame game, SyzygyTablebase syzygy,
                                                        GaviotaTablebase gaviota) throws IOException {
        return findRankedMoves(game, syzygy, gaviota, false);
    }

    /**
     * Try get DTM Data on gaviota. <br>
     * If the total piece count is more than 5 or given board's variant is not standard, return {@code null}.
     */
    private static Integer tryGetDtm(Chessboard board, GaviotaTablebase gaviota) {
        if(board.gameVariant != GameVariant.STANDARD) return null;
        if (Long.bitCount(board.occupancies[both]) > 5) return null;

        try {
            return gaviota.probeDtm(board);
        } catch (TablebaseUnsupportedMaterialException e) {
            return null;
        }
    }

    private static void validateVariant(ChessGame game) {
        if (game.getGameVariant() != GameVariant.STANDARD && game.getGameVariant() != GameVariant.ATOMIC &&
                game.getGameVariant() != GameVariant.GIVEAWAY && game.getGameVariant() != GameVariant.SUICIDE) {
            throw new VariantNotMatchException("Variant should be Standard chess or Chess 960!");
        }
    }

    private static void validateCastling(ChessGame game) {
        if (game.hasCastling()) throw new IllegalArgumentException("Tablebase probing should not contain Castling rights!");
    }
}
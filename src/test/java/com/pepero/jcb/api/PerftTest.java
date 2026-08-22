package com.pepero.jcb.api;

import com.pepero.jcb.api.perft.PerftDriver;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.GameVariant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PerftTest {
    @Test
    @DisplayName("스탠다드 체스 Perft 검증")
    void standard() {
        // 119060324

        Chessboard chessboard = new Chessboard(Chessboard.start_position);
        assertEquals(119060324L,
                PerftDriver.perftBitboardDriver(chessboard, 6, true));
        ChessGame chessGame = ChessGame.startPosition();
        assertEquals(119060324L,
                PerftDriver.perftAPIDriver(chessGame, 6, true));
    }

    @Test
    @DisplayName("크래이지 하우스 Perft 검증")
    void crazyhouse() {
        // 120812942

        Chessboard chessboard = new Chessboard(Chessboard.start_position, GameVariant.CRAZY_HOUSE);
        assertEquals(120812942L,
                PerftDriver.perftBitboardDriver(chessboard, 6, true));
        ChessGame chessGame = ChessGame.startPosition(GameVariant.CRAZY_HOUSE);
        assertEquals(120812942L,
                PerftDriver.perftAPIDriver(chessGame, 6, true));
    }

    @Test
    @DisplayName("3 Check Perft 검증")
    void threeCheck() {
        // 119060324

        Chessboard chessboard = new Chessboard(Chessboard.start_position, GameVariant.THREE_CHECK);
        assertEquals(119060324L,
                PerftDriver.perftBitboardDriver(chessboard, 6, true));
        ChessGame chessGame = ChessGame.startPosition(GameVariant.THREE_CHECK);
        assertEquals(119060324L,
                PerftDriver.perftAPIDriver(chessGame, 6, true));
    }

    @Test
    @DisplayName("Horde Perft 검증")
    void horde() {
        // 68441644

        Chessboard chessboard = new Chessboard(Chessboard.horde_start_position, GameVariant.HORDE);
        assertEquals(68441644L,
                PerftDriver.perftBitboardDriver(chessboard, 7, true));
        ChessGame chessGame = ChessGame.startPosition(GameVariant.HORDE);
        assertEquals(68441644L,
                PerftDriver.perftAPIDriver(chessGame, 7, true));
    }


    @Test
    @DisplayName("Giveaway Perft 검증")
    void giveaway() {
        // 46264162

        Chessboard chessboard = new Chessboard(Chessboard.antichess_start_position, GameVariant.GIVEAWAY);
        assertEquals(46264162L,
                PerftDriver.perftBitboardDriver(chessboard, 6, true));
        ChessGame chessGame = ChessGame.startPosition(GameVariant.GIVEAWAY);
        assertEquals(46264162L,
                PerftDriver.perftAPIDriver(chessGame, 6, true));
    }


    @Test
    @DisplayName("Suicide Perft 검증")
    void suicide() {
        // 46264162

        Chessboard chessboard = new Chessboard(Chessboard.antichess_start_position, GameVariant.SUICIDE);
        assertEquals(46264162L,
                PerftDriver.perftBitboardDriver(chessboard, 6, true));
        ChessGame chessGame = ChessGame.startPosition(GameVariant.SUICIDE);
        assertEquals(46264162L,
                PerftDriver.perftAPIDriver(chessGame, 6, true));
    }

    @Test
    @DisplayName("Atomic Perft 검증")
    void atomic() {
        // 118926425

        Chessboard chessboard = new Chessboard(Chessboard.start_position, GameVariant.ATOMIC);
        assertEquals(118926425L,
                PerftDriver.perftBitboardDriver(chessboard, 6, true));
        ChessGame chessGame = ChessGame.startPosition(GameVariant.ATOMIC);
        assertEquals(118926425L,
                PerftDriver.perftAPIDriver(chessGame, 6, true));
    }

    @Test
    @DisplayName("King of the hill Perft 검증")
    void koth() {
        // 119060324

        Chessboard chessboard = new Chessboard(Chessboard.start_position, GameVariant.KING_OF_THE_HILL);
        assertEquals(119060324L,
                PerftDriver.perftBitboardDriver(chessboard, 6, true));
        ChessGame chessGame = ChessGame.startPosition(GameVariant.KING_OF_THE_HILL);
        assertEquals(119060324L,
                PerftDriver.perftAPIDriver(chessGame, 6, true));
    }

    @Test
    @DisplayName("Racing kings Perft 검증")
    void racingKings() {
        // 298712641

        Chessboard chessboard = new Chessboard(Chessboard.racing_kings_start_position, GameVariant.RACING_KINGS);
        assertEquals(9472927L,
                PerftDriver.perftBitboardDriver(chessboard, 5, true));
        ChessGame chessGame = ChessGame.startPosition(GameVariant.RACING_KINGS);
        assertEquals(9472927L,
                PerftDriver.perftAPIDriver(chessGame, 5, true));
    }
}

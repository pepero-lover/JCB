package com.pepero.jcb.api;

import com.pepero.jcb.api.perft.PerftDriver;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.GameVariants;
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

        Chessboard chessboard = new Chessboard(Chessboard.start_position, GameVariants.CRAZY_HOUSE);
        assertEquals(120812942L,
                PerftDriver.perftBitboardDriver(chessboard, 6, true));
        ChessGame chessGame = ChessGame.startPosition(GameVariants.CRAZY_HOUSE);
        assertEquals(120812942L,
                PerftDriver.perftAPIDriver(chessGame, 6, true));
    }

    @Test
    @DisplayName("3 Check Perft 검증")
    void threeCheck() {
        // 119060324

        Chessboard chessboard = new Chessboard(Chessboard.start_position, GameVariants.THREE_CHECK);
        assertEquals(119060324L,
                PerftDriver.perftBitboardDriver(chessboard, 6, true));
        ChessGame chessGame = ChessGame.startPosition(GameVariants.THREE_CHECK);
        assertEquals(119060324L,
                PerftDriver.perftAPIDriver(chessGame, 6, true));
    }

    @Test
    @DisplayName("Horde Perft 검증")
    void horde() {
        // 68441644

        Chessboard chessboard = new Chessboard(Chessboard.horde_start_position, GameVariants.HORDE);
        assertEquals(68441644L,
                PerftDriver.perftBitboardDriver(chessboard, 7, true));
        ChessGame chessGame = ChessGame.startPosition(GameVariants.HORDE);
        assertEquals(68441644L,
                PerftDriver.perftAPIDriver(chessGame, 7, true));
    }


    @Test
    @DisplayName("Antichess Perft 검증")
    void antichess() {
        // 46264162

        Chessboard chessboard = new Chessboard(Chessboard.antichess_start_position, GameVariants.ANTICHESS);
        assertEquals(46264162L,
                PerftDriver.perftBitboardDriver(chessboard, 6, true));
        ChessGame chessGame = ChessGame.startPosition(GameVariants.ANTICHESS);
        assertEquals(46264162L,
                PerftDriver.perftAPIDriver(chessGame, 6, true));
    }

    @Test
    @DisplayName("Atomic Perft 검증")
    void atomic() {
        // 118926425

        Chessboard chessboard = new Chessboard(Chessboard.start_position, GameVariants.ATOMIC);
        assertEquals(118926425L,
                PerftDriver.perftBitboardDriver(chessboard, 6, true));
        ChessGame chessGame = ChessGame.startPosition(GameVariants.ATOMIC);
        assertEquals(118926425L,
                PerftDriver.perftAPIDriver(chessGame, 6, true));
    }

    @Test
    @DisplayName("King of the hill Perft 검증")
    void koth() {
        // 119060324

        Chessboard chessboard = new Chessboard(Chessboard.start_position, GameVariants.KING_OF_THE_HILL);
        assertEquals(119060324L,
                PerftDriver.perftBitboardDriver(chessboard, 6, true));
        ChessGame chessGame = ChessGame.startPosition(GameVariants.KING_OF_THE_HILL);
        assertEquals(119060324L,
                PerftDriver.perftAPIDriver(chessGame, 6, true));
    }

    @Test
    @DisplayName("Racing kings Perft 검증")
    void racingKings() {
        // 298712641

        Chessboard chessboard = new Chessboard(Chessboard.racing_kings_start_position, GameVariants.RACING_KINGS);
        assertEquals(298712641L,
                PerftDriver.perftBitboardDriver(chessboard, 6, true));
        ChessGame chessGame = ChessGame.startPosition(GameVariants.RACING_KINGS);
        assertEquals(298712641L,
                PerftDriver.perftAPIDriver(chessGame, 6, true));
    }
}

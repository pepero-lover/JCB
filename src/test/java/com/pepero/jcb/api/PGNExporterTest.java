package com.pepero.jcb.api;

import com.pepero.jcb.api.enums.GameResult;
import com.pepero.jcb.core.GameVariant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class PGNExporterTest {

    private static MoveNode parseToRootNode(String pgn) {
        return PGNParser.parse(pgn, 100_000, new AtomicLong()).rootNode();
    }

    @Test
    @DisplayName("GameResult 각 값이 올바른 PGN 결과 문자열로 매핑되어야 한다")
    void getGameResultStringMapsEachResult() {
        assertEquals("1-0", PGNExporter.getGameResultString(GameResult.WHITE_WON));
        assertEquals("0-1", PGNExporter.getGameResultString(GameResult.BLACK_WON));
        assertEquals("1/2-1/2", PGNExporter.getGameResultString(GameResult.DRAW));
        assertEquals("*", PGNExporter.getGameResultString(GameResult.UNKNOWN));
        assertEquals("*", PGNExporter.getGameResultString(GameResult.ABORTED));
    }

    @Test
    @DisplayName("GameResult가 null이면 '*' 를 반환해야 한다")
    void getGameResultStringNullIsAsterisk() {
        assertEquals("*", PGNExporter.getGameResultString(null));
    }

    @Test
    @DisplayName("export는 헤더, 기보, 결과를 한 줄의 PGN 문자열로 조립해야 한다")
    void exportAssemblesHeadersMovesAndResult() {
        MoveNode root = parseToRootNode("1. e4 e5 2. Nf3 Nc6 1-0");

        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        headers.put("Event", "Test Event");
        headers.put("White", "Alice");
        headers.put("Black", "Bob");

        ChessGame startGame = ChessGame.startPosition();
        PGNGame pgnGame = PGNExporter.createPGNGame(headers, startGame.getStartPositionFEN(),
                GameVariant.STANDARD, false, GameResult.WHITE_WON, root, 100_000);

        String exported = PGNExporter.export(startGame, pgnGame, false);

        assertTrue(exported.contains("[Event \"Test Event\"]"));
        assertTrue(exported.contains("[White \"Alice\"]"));
        assertTrue(exported.contains("[Black \"Bob\"]"));
        assertTrue(exported.contains("[Result \"1-0\"]"));
        assertTrue(exported.contains("1. e4 e5 2. Nf3 Nc6"));
        assertTrue(exported.endsWith("1-0"));
    }

    @Test
    @DisplayName("createPGNGame은 진행 중인 게임이라도 Result 헤더를 '*' 로 채워야 한다")
    void createPGNGameFillsAsteriskResultForOngoingGame() {
        MoveNode root = parseToRootNode("1. e4 *");

        PGNGame pgnGame = PGNExporter.createPGNGame(new LinkedHashMap<>(),
                ChessGame.startPosition().getStartPositionFEN(),
                GameVariant.STANDARD, false, GameResult.UNKNOWN, root, 100_000);

        assertEquals("*", pgnGame.headers().get("Result"));
    }

    @Test
    @DisplayName("isChess960이 true면 Variant 헤더가 'Chess960'으로 채워져야 한다")
    void chess960FlagAddsChess960VariantHeader() {
        MoveNode root = parseToRootNode("1. e4 *");

        PGNGame pgnGame = PGNExporter.createPGNGame(new LinkedHashMap<>(),
                ChessGame.startPosition().getStartPositionFEN(),
                GameVariant.STANDARD, true, GameResult.UNKNOWN, root, 100_000);

        assertEquals("Chess960", pgnGame.headers().get("Variant"));
    }

    @Test
    @DisplayName("GameVariant가 ATOMIC이면 Variant 헤더가 'Atomic'으로 채워져야 한다")
    void atomicVariantAddsAtomicVariantHeader() {
        MoveNode root = parseToRootNode("1. e4 *");

        PGNGame pgnGame = PGNExporter.createPGNGame(new LinkedHashMap<>(),
                ChessGame.startPosition().getStartPositionFEN(),
                GameVariant.ATOMIC, false, GameResult.UNKNOWN, root, 100_000);

        assertEquals("Atomic", pgnGame.headers().get("Variant"));
    }

    @Test
    @DisplayName("GameVariant가 SUICIDE면 Variant와 RuleVariants 헤더가 모두 채워져야 한다")
    void suicideVariantAddsRuleVariantsHeader() {
        MoveNode root = parseToRootNode("1. e4 *");

        PGNGame pgnGame = PGNExporter.createPGNGame(new LinkedHashMap<>(),
                ChessGame.startPosition().getStartPositionFEN(),
                GameVariant.SUICIDE, false, GameResult.UNKNOWN, root, 100_000);

        assertEquals("Antichess", pgnGame.headers().get("Variant"));
        assertEquals("FICS", pgnGame.headers().get("RuleVariants"));
    }

    @Test
    @DisplayName("표준 시작 위치가 아니면 SetUp/FEN 헤더가 채워져야 한다")
    void customStartPositionAddsSetUpAndFenHeaders() {
        MoveNode root = parseToRootNode("1. e4 *");
        String customFen = "8/8/8/4k3/8/8/4K3/8 w - - 0 1";

        PGNGame pgnGame = PGNExporter.createPGNGame(new LinkedHashMap<>(),
                customFen, GameVariant.STANDARD, false, GameResult.UNKNOWN, root, 100_000);

        assertEquals("1", pgnGame.headers().get("SetUp"));
        assertEquals(customFen, pgnGame.headers().get("FEN"));
    }

    @Test
    @DisplayName("표준 시작 위치면 SetUp/FEN 헤더가 없어야 한다")
    void standardStartPositionHasNoSetUpOrFenHeaders() {
        MoveNode root = parseToRootNode("1. e4 *");

        PGNGame pgnGame = PGNExporter.createPGNGame(new LinkedHashMap<>(),
                ChessGame.startPosition().getStartPositionFEN(),
                GameVariant.STANDARD, false, GameResult.UNKNOWN, root, 100_000);

        assertFalse(pgnGame.headers().containsKey("SetUp"));
        assertFalse(pgnGame.headers().containsKey("FEN"));
    }
}
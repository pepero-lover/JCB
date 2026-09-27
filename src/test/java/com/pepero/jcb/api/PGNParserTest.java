package com.pepero.jcb.api;

import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.api.enums.GameResult;
import com.pepero.jcb.api.exception.pgn.NodesOverflowException;
import com.pepero.jcb.api.exception.pgn.PGNConvertException;
import com.pepero.jcb.api.exception.type.PGNErrorType;
import com.pepero.jcb.core.GameVariant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class PGNParserTest {

    private static PGNParsedData parse(String pgn) {
        return PGNParser.parse(pgn, 100_000, new AtomicLong());
    }

    @Test
    @DisplayName("Variant 헤더가 없으면(null) STANDARD 여야 한다")
    void nullVariantHeaderIsStandard() {
        assertEquals(GameVariant.STANDARD, PGNParser.parseVariantHeader(null));
    }

    @Test
    @DisplayName("알 수 없는 Variant 문자열은 STANDARD로 처리해야 한다")
    void unknownVariantHeaderFallsBackToStandard() {
        assertEquals(GameVariant.STANDARD, PGNParser.parseVariantHeader("no-such-variant"));
    }

    @Test
    @DisplayName("'Chess960'은 별도 GameVariant가 아니라 STANDARD로 매핑되어야 한다 (isChess960 플래그로 별도 처리됨)")
    void chess960HeaderMapsToStandardVariant() {
        assertEquals(GameVariant.STANDARD, PGNParser.parseVariantHeader("Chess960"));
    }

    @Test
    @DisplayName("각 변형 이름과 그 축약/동의어들이 올바른 GameVariant로 매핑되어야 한다")
    void variantSynonymsMapToCorrectVariant() {
        assertEquals(GameVariant.CRAZY_HOUSE, PGNParser.parseVariantHeader("Crazyhouse"));

        assertEquals(GameVariant.THREE_CHECK, PGNParser.parseVariantHeader("Three-check"));
        assertEquals(GameVariant.THREE_CHECK, PGNParser.parseVariantHeader("3check"));

        assertEquals(GameVariant.KING_OF_THE_HILL, PGNParser.parseVariantHeader("King of the Hill"));
        assertEquals(GameVariant.KING_OF_THE_HILL, PGNParser.parseVariantHeader("koth"));

        assertEquals(GameVariant.HORDE, PGNParser.parseVariantHeader("Horde"));
        assertEquals(GameVariant.HORDE, PGNParser.parseVariantHeader("hd"));

        assertEquals(GameVariant.RACING_KINGS, PGNParser.parseVariantHeader("Racing Kings"));
        assertEquals(GameVariant.RACING_KINGS, PGNParser.parseVariantHeader("kr"));

        assertEquals(GameVariant.GIVEAWAY, PGNParser.parseVariantHeader("Antichess"));
        assertEquals(GameVariant.GIVEAWAY, PGNParser.parseVariantHeader("losing chess"));

        assertEquals(GameVariant.SUICIDE, PGNParser.parseVariantHeader("Suicide"));

        assertEquals(GameVariant.ATOMIC, PGNParser.parseVariantHeader("Atomic"));
        assertEquals(GameVariant.ATOMIC, PGNParser.parseVariantHeader("nuclear chess"));
    }

    @Test
    @DisplayName("Variant 문자열의 대소문자와 앞뒤 공백은 무시되어야 한다")
    void variantHeaderIsCaseAndWhitespaceInsensitive() {
        assertEquals(GameVariant.ATOMIC, PGNParser.parseVariantHeader("  ATOMIC  "));
        assertEquals(GameVariant.ATOMIC, PGNParser.parseVariantHeader("AtoMic"));
    }

    @Test
    @DisplayName("헤더와 기보를 정상적으로 파싱해야 한다")
    void parsesHeadersAndMoves() {
        PGNParsedData data = parse("""
                [Event "Test"]
                [White "Alice"]
                [Black "Bob"]

                1. e4 e5 2. Nf3 Nc6 1-0
                """);

        assertEquals("Test", data.header().get("Event"));
        assertEquals("Alice", data.header().get("White"));
        assertEquals("Bob", data.header().get("Black"));
        assertEquals(GameVariant.STANDARD, data.variant());
        assertFalse(data.isChess960());
        assertEquals(GameResult.WHITE_WON, data.gameResult());

        assertEquals(1, data.rootNode().children.size());
        assertEquals("e2e4", data.rootNode().children.getFirst().moveData.toLanString());
    }

    @Test
    @DisplayName("헤더에 결과가 없어도 마지막 결과 토큰으로부터 GameResult를 판별해야 한다")
    void resultTokenDeterminesGameResultWithoutHeader() {
        PGNParsedData data = parse("1. e4 e5 0-1");
        assertEquals(GameResult.BLACK_WON, data.gameResult());
    }

    @Test
    @DisplayName("진행 중인 게임('*' 또는 결과 토큰 없음)은 UNKNOWN/NOTGAMEOVER 여야 한다")
    void unfinishedGameHasUnknownResult() {
        PGNParsedData data = parse("1. e4 e5 *");
        assertEquals(GameResult.UNKNOWN, data.gameResult());
        assertEquals(GameOverReason.NOTGAMEOVER, data.gameOverReason());
    }

    @Test
    @DisplayName("체크메이트로 끝난 기보는 CHECKMATE 사유여야 한다")
    void checkmateEndingHasCheckmateReason() {
        PGNParsedData data = parse("1. e4 e5 2. Bc4 Nc6 3. Qh5 Nf6 4. Qxf7# 1-0");
        assertEquals(GameOverReason.CHECKMATE, data.gameOverReason());
    }

    @Test
    @DisplayName("변이(variation)는 부모 노드의 형제(sibling)로 트리에 추가되어야 한다")
    void variationsAreAddedAsSiblingsInTree() {
        PGNParsedData data = parse("1. e4 (1. d4 d5) e5 *");

        MoveNode root = data.rootNode();
        assertEquals(2, root.children.size(), "메인라인 e4 와 변이 d4 가 루트의 형제여야 함");
        assertEquals("e2e4", root.children.get(0).moveData.toLanString());
        assertEquals("d2d4", root.children.get(1).moveData.toLanString());
        assertEquals(1, root.children.get(1).children.size(), "변이 안의 d5 는 d4 의 자식이어야 함");
    }

    @Test
    @DisplayName("{[%clk]}, {[%eval]} 같은 주석 태그는 파싱되어 주석 본문에서 제거되어야 한다")
    void annotationTagsAreExtractedFromComment() {
        PGNParsedData data = parse("1. e4 { [%clk 0:01:00] [%eval 0.2] nice move } e5 *");

        MoveNode e4Node = data.rootNode().children.getFirst();
        assertEquals("0:01:00", e4Node.getAnnotation().clk);
        assertEquals("0.2", e4Node.getAnnotation().eval);
        assertEquals("nice move", e4Node.getAnnotation().comment);
    }

    @Test
    @DisplayName("PGN 문자열 맨 앞의 BOM은 무시되어야 한다")
    void leadingBomIsIgnored() {
        PGNParsedData data = parse("\uFEFF[Event \"Test\"]\n\n1. e4 *");
        assertEquals("Test", data.header().get("Event"));
    }

    @Test
    @DisplayName("빈 PGN 문자열은 PGN_EMPTY 타입의 PGNConvertException을 던져야 한다")
    void emptyPgnThrowsPgnEmpty() {
        PGNConvertException ex = assertThrows(PGNConvertException.class, () -> parse(""));
        assertEquals(PGNErrorType.PGN_EMPTY, ex.getErrorType());
    }

    @Test
    @DisplayName("null PGN 문자열도 PGN_EMPTY 타입의 PGNConvertException을 던져야 한다")
    void nullPgnThrowsPgnEmpty() {
        PGNConvertException ex = assertThrows(PGNConvertException.class, () -> parse(null));
        assertEquals(PGNErrorType.PGN_EMPTY, ex.getErrorType());
    }

    @Test
    @DisplayName("헤더 줄이 ']'로 닫히지 않으면 WRONG_HEADER 예외를 던져야 한다")
    void headerLineMissingClosingBracketThrowsWrongHeader() {
        PGNConvertException ex = assertThrows(PGNConvertException.class,
                () -> parse("[White \"Alice\"\n\n1. e4 *"));
        assertEquals(PGNErrorType.WRONG_HEADER, ex.getErrorType());
        assertTrue(ex.getRealValue().startsWith("[White"));
    }

    @Test
    @DisplayName("헤더 키와 값 사이에 공백이 없으면 WRONG_HEADER 예외를 던져야 한다")
    void headerLineMissingSpaceThrowsWrongHeader() {
        PGNConvertException ex = assertThrows(PGNConvertException.class,
                () -> parse("[WhiteAlice]\n\n1. e4 *"));
        assertEquals(PGNErrorType.WRONG_HEADER, ex.getErrorType());
    }

    @Test
    @DisplayName("헤더 값이 따옴표로 감싸져 있지 않으면 WRONG_HEADER 예외를 던져야 한다")
    void headerValueNotQuotedThrowsWrongHeader() {
        PGNConvertException ex = assertThrows(PGNConvertException.class,
                () -> parse("[White Alice]\n\n1. e4 *"));
        assertEquals(PGNErrorType.WRONG_HEADER, ex.getErrorType());
    }

    @Test
    @DisplayName("변이가 끝까지 닫히지 않으면 VARIATION_STACK_NOT_EMPTY 예외를 던져야 한다")
    void unclosedVariationThrowsVariationStackNotEmpty() {
        PGNConvertException ex = assertThrows(PGNConvertException.class,
                () -> parse("1. e4 e5 (1... c5 2. Nf3"));
        assertEquals(PGNErrorType.VARIATION_STACK_NOT_EMPTY, ex.getErrorType());
    }

    @Test
    @DisplayName("시작하지 않은 변이가 ')'로 닫히면 VARIATION_STACK_EMPTY_POP 예외를 던져야 한다")
    void extraClosingParenThrowsVariationStackEmptyPop() {
        PGNConvertException ex = assertThrows(PGNConvertException.class,
                () -> parse("1. e4 e5 )"));
        assertEquals(PGNErrorType.VARIATION_STACK_EMPTY_POP, ex.getErrorType());
    }

    @Test
    @DisplayName("수의 개수가 maxNodesCount를 초과하면 NodesOverflowException을 던져야 한다")
    void tooManyNodesThrowsNodesOverflowException() {
        NodesOverflowException ex = assertThrows(NodesOverflowException.class,
                () -> PGNParser.parse("1. e4 e5 2. Nf3 Nc6 *", 2, new AtomicLong()));
        assertEquals(2, ex.getMaxNodesCount());
    }
}
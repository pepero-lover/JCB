package com.pepero.jcb.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PGNLexerTest {

    private static PGNToken next(PGNLexer lexer) {
        return lexer.nextToken();
    }

    @Test
    @DisplayName("빈 문자열은 바로 EOF 토큰을 반환해야 한다")
    void emptyStringReturnsEof() {
        PGNLexer lexer = new PGNLexer("");
        assertEquals(TokenType.EOF, next(lexer).type());
    }

    @Test
    @DisplayName("공백만 있는 문자열도 EOF 토큰을 반환해야 한다")
    void blankStringReturnsEof() {
        PGNLexer lexer = new PGNLexer("   \n\t  ");
        assertEquals(TokenType.EOF, next(lexer).type());
    }

    @Test
    @DisplayName("수순 번호와 수는 별도의 토큰으로 분리되어야 한다 (마침표는 소비되고 값에는 남지 않음)")
    void numberIndicatorAndMoveAreSeparateTokens() {
        PGNLexer lexer = new PGNLexer("1. e4 e5");

        PGNToken t1 = next(lexer);
        assertEquals(TokenType.NUMBER_INDICATOR, t1.type());
        assertEquals("1", t1.value(), "마침표는 구분자로 소비되어 값에 남지 않아야 함");

        assertEquals(new PGNToken(TokenType.MOVE, "e4"), next(lexer));
        assertEquals(new PGNToken(TokenType.MOVE, "e5"), next(lexer));
        assertEquals(TokenType.EOF, next(lexer).type());
    }

    @Test
    @DisplayName("'1.e4' 처럼 공백 없이 붙어있어도 수순 번호와 수가 분리되어야 한다")
    void numberIndicatorWithoutSpaceStillSplits() {
        PGNLexer lexer = new PGNLexer("1.e4");

        assertEquals(new PGNToken(TokenType.NUMBER_INDICATOR, "1"), next(lexer));
        assertEquals(new PGNToken(TokenType.MOVE, "e4"), next(lexer));
        assertEquals(TokenType.EOF, next(lexer).type());
    }

    @Test
    @DisplayName("흑 차례 이어받기 표기('5...')도 마침표 개수와 상관없이 수순 번호 하나로 처리되어야 한다")
    void blackToMoveEllipsisCollapsesToSingleNumberIndicator() {
        PGNLexer lexer = new PGNLexer("5... Nf6");

        assertEquals(new PGNToken(TokenType.NUMBER_INDICATOR, "5"), next(lexer));
        assertEquals(new PGNToken(TokenType.MOVE, "Nf6"), next(lexer));
        assertEquals(TokenType.EOF, next(lexer).type());
    }

    @Test
    @DisplayName("중괄호 주석은 중괄호를 제외한 내용 그대로 COMMENT 토큰이어야 한다")
    void braceCommentIsExtractedVerbatim() {
        PGNLexer lexer = new PGNLexer("e4 { a comment } e5");

        assertEquals(new PGNToken(TokenType.MOVE, "e4"), next(lexer));
        assertEquals(new PGNToken(TokenType.COMMENT, " a comment "), next(lexer));
        assertEquals(new PGNToken(TokenType.MOVE, "e5"), next(lexer));
    }

    @Test
    @DisplayName("닫히지 않은 중괄호 주석은 파일 끝까지를 주석으로 처리해야 한다")
    void unterminatedBraceCommentReadsUntilEof() {
        PGNLexer lexer = new PGNLexer("e4 { unterminated");

        assertEquals(new PGNToken(TokenType.MOVE, "e4"), next(lexer));
        assertEquals(new PGNToken(TokenType.COMMENT, " unterminated"), next(lexer));
        assertEquals(TokenType.EOF, next(lexer).type());
    }

    @Test
    @DisplayName("세미콜론 주석은 줄바꿈 전까지만 COMMENT 토큰으로 처리해야 한다")
    void semicolonCommentReadsUntilNewline() {
        PGNLexer lexer = new PGNLexer("e4 ; rest of line\ne5");

        assertEquals(new PGNToken(TokenType.MOVE, "e4"), next(lexer));
        assertEquals(new PGNToken(TokenType.COMMENT, " rest of line"), next(lexer));
        assertEquals(new PGNToken(TokenType.MOVE, "e5"), next(lexer));
    }

    @Test
    @DisplayName("숫자로 시작하는 NAG(예: $1)는 NAG 토큰이어야 한다")
    void numericNagIsNagToken() {
        PGNLexer lexer = new PGNLexer("e4 $1 e5");

        assertEquals(new PGNToken(TokenType.MOVE, "e4"), next(lexer));
        assertEquals(new PGNToken(TokenType.NAG, "$1"), next(lexer));
        assertEquals(new PGNToken(TokenType.MOVE, "e5"), next(lexer));
    }

    @Test
    @DisplayName("'!', '?' 로만 이루어진 독립 토큰(예: '!!')은 NAG 토큰이어야 한다")
    void standaloneExclamationSymbolIsNagToken() {
        PGNLexer lexer = new PGNLexer("e4 !! e5");

        assertEquals(new PGNToken(TokenType.MOVE, "e4"), next(lexer));
        assertEquals(new PGNToken(TokenType.NAG, "!!"), next(lexer));
        assertEquals(new PGNToken(TokenType.MOVE, "e5"), next(lexer));
    }

    @Test
    @DisplayName("수에 '!!' 가 바로 붙어있으면(예: 'e4!!') 분리되지 않고 하나의 MOVE 토큰이어야 한다")
    void exclamationAttachedToMoveStaysInOneMoveToken() {
        PGNLexer lexer = new PGNLexer("e4!!");

        assertEquals(new PGNToken(TokenType.MOVE, "e4!!"), next(lexer));
        assertEquals(TokenType.EOF, next(lexer).type());
    }

    @Test
    @DisplayName("괄호는 각각 VARIATION_START / VARIATION_END 토큰이어야 한다")
    void parenthesesAreVariationTokens() {
        PGNLexer lexer = new PGNLexer("(1... c5 2. Nf3)");

        assertEquals(new PGNToken(TokenType.VARIATION_START, "("), next(lexer));
        assertEquals(new PGNToken(TokenType.NUMBER_INDICATOR, "1"), next(lexer));
        assertEquals(new PGNToken(TokenType.MOVE, "c5"), next(lexer));
        assertEquals(new PGNToken(TokenType.NUMBER_INDICATOR, "2"), next(lexer));
        assertEquals(new PGNToken(TokenType.MOVE, "Nf3"), next(lexer));
        assertEquals(new PGNToken(TokenType.VARIATION_END, ")"), next(lexer));
        assertEquals(TokenType.EOF, next(lexer).type());
    }

    @Test
    @DisplayName("'1-0', '0-1', '1/2-1/2', '*' 는 모두 RESULT 토큰이어야 한다")
    void allResultStringsAreResultTokens() {
        for (String result : new String[]{"1-0", "0-1", "1/2-1/2", "*"}) {
            PGNLexer lexer = new PGNLexer(result);
            assertEquals(new PGNToken(TokenType.RESULT, result), next(lexer),
                    "'" + result + "' 는 RESULT 토큰이어야 함");
        }
    }
}
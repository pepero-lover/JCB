package com.pepero.jcb.api;

import com.pepero.jcb.api.pgn.PGNDatabaseIterator;
import com.pepero.jcb.api.pgn.PGNGameStub;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

public class PGNDatabaseIteratorTest {

    @TempDir
    Path tempDir;

    private static final String SINGLE_GAME =
            """
            [Event "Test Event"]
            [Site "Test Site"]
            [White "Alice"]
            [Black "Bob"]
            [Result "1-0"]

            1. e4 e5 2. Nf3 Nc6 1-0
            """;

    private static final String TWO_GAMES =
            """
            [Event "Game One"]
            [White "Alice"]
            [Black "Bob"]
            [Result "1-0"]

            1. e4 e5 1-0

            [Event "Game Two"]
            [White "Carol"]
            [Black "Dave"]
            [Result "0-1"]

            1. d4 d5 0-1
            """;

    private Path writeFile(String fileName, String content) throws IOException {
        return Files.writeString(tempDir.resolve(fileName), content);
    }

    @Test
    @DisplayName("헤더와 기보를 정확히 파싱해야 한다")
    void parsesHeadersAndMovetext() throws IOException {
        Path path = writeFile("single.pgn", SINGLE_GAME);

        try (PGNDatabaseIterator it = new PGNDatabaseIterator(path)) {
            assertTrue(it.hasNext());

            PGNGameStub stub = it.next();
            Map<String, String> headers = stub.headers();

            assertEquals("Test Event", headers.get("Event"));
            assertEquals("Alice", headers.get("White"));
            assertEquals("Bob", headers.get("Black"));
            assertEquals("1-0", headers.get("Result"));
            assertTrue(stub.rawPGN().contains("1. e4 e5 2. Nf3 Nc6 1-0"));

            assertFalse(it.hasNext());
        }
    }

    @Test
    @DisplayName("여러 게임을 순서대로 스트리밍해야 한다")
    void streamsMultipleGamesInOrder() throws IOException {
        Path path = writeFile("two.pgn", TWO_GAMES);

        List<String> whites = new ArrayList<>();
        try (PGNDatabaseIterator it = new PGNDatabaseIterator(path)) {
            while (it.hasNext()) {
                whites.add(it.next().headers().get("White"));
            }
        }

        assertEquals(List.of("Alice", "Carol"), whites);
    }

    @Test
    @DisplayName("for-each(Iterable)로도 순회할 수 있어야 한다")
    void iterableForEach() throws IOException {
        Path path = writeFile("two.pgn", TWO_GAMES);

        int count = 0;
        try (PGNDatabaseIterator it = new PGNDatabaseIterator(path)) {
            for (PGNGameStub stub : it) {
                assertNotNull(stub.headers().get("Event"));
                count++;
            }
        }

        assertEquals(2, count);
    }

    @Test
    @DisplayName("더 이상 게임이 없을 때 next()는 NoSuchElementException을 던져야 한다")
    void nextThrowsWhenExhausted() throws IOException {
        Path path = writeFile("single.pgn", SINGLE_GAME);

        try (PGNDatabaseIterator it = new PGNDatabaseIterator(path)) {
            it.next();
            assertFalse(it.hasNext());
            assertThrows(NoSuchElementException.class, it::next);
        }
    }

    @Test
    @DisplayName("빈 파일은 hasNext()가 처음부터 false여야 한다")
    void emptyFileHasNoGames() throws IOException {
        Path path = writeFile("empty.pgn", "");

        try (PGNDatabaseIterator it = new PGNDatabaseIterator(path)) {
            assertFalse(it.hasNext());
            assertThrows(NoSuchElementException.class, it::next);
        }
    }

    @Test
    @DisplayName("게임 사이의 여분의 빈 줄은 무시해야 한다")
    void skipsExtraBlankLinesBetweenGames() throws IOException {
        String content =
                """
                [Event "Game One"]
                [Result "1-0"]


                1. e4 e5 1-0




                [Event "Game Two"]
                [Result "0-1"]

                1. d4 d5 0-1
                """;
        Path path = writeFile("blank.pgn", content);

        List<String> events = new ArrayList<>();
        try (PGNDatabaseIterator it = new PGNDatabaseIterator(path)) {
            while (it.hasNext()) {
                events.add(it.next().headers().get("Event"));
            }
        }

        assertEquals(List.of("Game One", "Game Two"), events);
    }

    @Test
    @DisplayName("따옴표로 감싸진 헤더 값에서 따옴표를 제거해야 한다")
    void stripsQuotesFromHeaderValue() throws IOException {
        Path path = writeFile("single.pgn", SINGLE_GAME);

        try (PGNDatabaseIterator it = new PGNDatabaseIterator(path)) {
            Map<String, String> headers = it.next().headers();
            assertFalse(headers.get("Event").startsWith("\""));
            assertFalse(headers.get("Event").endsWith("\""));
        }
    }

    @Test
    @DisplayName("여러 줄에 걸친 주석 안에서 '['로 시작하는 줄은 새 게임으로 취급하지 않아야 한다")
    void bracketInsideMultilineCommentDoesNotStartNewGame() throws IOException {
        String content =
                """
                [Event "Game One"]
                [Result "1-0"]

                1. e4 { long comment
                [%eval 0.3] continues } e5 1-0

                [Event "Game Two"]
                [Result "0-1"]

                1. d4 d5 0-1
                """;
        Path path = writeFile("comment.pgn", content);

        List<String> events = new ArrayList<>();
        String firstGameRawPgn = null;
        try (PGNDatabaseIterator it = new PGNDatabaseIterator(path)) {
            while (it.hasNext()) {
                PGNGameStub stub = it.next();
                if (firstGameRawPgn == null) firstGameRawPgn = stub.rawPGN();
                events.add(stub.headers().get("Event"));
            }
        }

        assertEquals(List.of("Game One", "Game Two"), events);
        assertTrue(firstGameRawPgn.contains("[%eval 0.3]"), "주석 안의 '[' 줄도 첫 게임의 기보에 포함되어야 함");
    }

    @Test
    @DisplayName("주석 안이어도 '[Event '로 시작하면 오타로 간주하고 새 게임 시작으로 취급해야 한다")
    void eventHeaderInsideCommentStillStartsNewGame() throws IOException {
        String content =
                """
                [Event "Game One"]
                [Result "1-0"]

                1. e4 { unterminated comment
                [Event "Game Two"]
                [Result "0-1"]

                1. d4 d5 0-1
                """;
        Path path = writeFile("typo.pgn", content);

        List<String> events = new ArrayList<>();
        try (PGNDatabaseIterator it = new PGNDatabaseIterator(path)) {
            while (it.hasNext()) {
                events.add(it.next().headers().get("Event"));
            }
        }

        assertEquals(List.of("Game One", "Game Two"), events);
    }

    @Test
    @DisplayName("파일 맨 앞 BOM은 첫 게임 헤더 인식을 방해하지 않아야 한다")
    void bomBeforeFirstHeaderIsStripped() throws IOException {
        Path path = tempDir.resolve("bom.pgn");
        String content = "\uFEFF" + SINGLE_GAME;
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));

        try (PGNDatabaseIterator it = new PGNDatabaseIterator(path)) {
            assertTrue(it.hasNext());
            PGNGameStub stub = it.next();
            assertEquals("Test Event", stub.headers().get("Event"));
        }
    }

    @Test
    @DisplayName("ISO-8859-1 등 지정한 Charset으로 파일을 읽어야 한다")
    void readsWithSpecifiedCharset() throws IOException {
        Path path = tempDir.resolve("latin1.pgn");
        String content = "[Event \"Caf\u00e9 Open\"]\n[Result \"1-0\"]\n\n1. e4 e5 1-0\n";
        Files.write(path, content.getBytes(StandardCharsets.ISO_8859_1));

        try (PGNDatabaseIterator it = new PGNDatabaseIterator(path, StandardCharsets.ISO_8859_1)) {
            PGNGameStub stub = it.next();
            assertEquals("Caf\u00e9 Open", stub.headers().get("Event"));
        }
    }

    @Test
    @DisplayName("close() 이후에는 hasNext()가 false를 반환해야 한다")
    void closeMakesHasNextFalse() throws IOException {
        Path path = writeFile("single.pgn", SINGLE_GAME);

        PGNDatabaseIterator it = new PGNDatabaseIterator(path);
        assertTrue(it.hasNext());
        it.close();

        assertFalse(it.hasNext());
    }

    @Test
    @DisplayName("close()를 여러 번 호출해도 예외가 발생하지 않아야 한다")
    void closeIsIdempotent() throws IOException {
        Path path = writeFile("single.pgn", SINGLE_GAME);

        PGNDatabaseIterator it = new PGNDatabaseIterator(path);
        it.close();

        assertDoesNotThrow(it::close);
    }

    @Test
    @DisplayName("존재하지 않는 파일 경로를 넘기면 UncheckedIOException이 발생해야 한다")
    void nonExistentFileThrowsUncheckedIOException() {
        Path missing = tempDir.resolve("does-not-exist.pgn");

        assertThrows(java.io.UncheckedIOException.class, () -> new PGNDatabaseIterator(missing));
    }

    @Test
    @DisplayName("PGNGameStub.toChessGame()으로 실제 ChessGame으로 변환할 수 있어야 한다")
    void gameStubConvertsToChessGame() throws IOException {
        Path path = writeFile("single.pgn", SINGLE_GAME);

        try (PGNDatabaseIterator it = new PGNDatabaseIterator(path)) {
            PGNGameStub stub = it.next();
            var chessGame = stub.toChessGame();

            assertNotNull(chessGame);
            assertEquals(5, chessGame.getTotalNodeCount());
        }
    }
}
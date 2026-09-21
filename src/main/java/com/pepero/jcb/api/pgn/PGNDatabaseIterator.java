package com.pepero.jcb.api.pgn;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Streams games out of a (potentially multi-gigabyte) PGN database file without
 * loading the whole file or any previously-yielded game into memory.
 * <p>
 * Each {@link #next()} call returns a {@link PGNGameStub}.
 * And headers are parsed eagerly, but movetext is kept as raw text and only parsed into a full
 * {@link com.pepero.jcb.api.ChessGame} on demand via {@link PGNGameStub#toChessGame()}.
 */
public class PGNDatabaseIterator implements Iterator<PGNGameStub>, Iterable<PGNGameStub>, AutoCloseable {

    // 64kb buffer
    private static final int BUFFER_SIZE = 1 << 16;
    // pgn game capacity
    private static final int INITIAL_GAME_CAPACITY = 2048;

    private final BufferedReader reader;

    // lookahead so we can peek at "does the next line start a new game" without consuming it
    private String lookahead_line;
    private boolean closed = false;

    public PGNDatabaseIterator(Path path) {
        this(path, StandardCharsets.UTF_8);
    }

    /**
     * @param path .pgn file
     * @param charset file encoding; use {@link StandardCharsets#ISO_8859_1} for old Latin-1 databases
     */
    public PGNDatabaseIterator(Path path, Charset charset) {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);

        BufferedReader bf;
        try {
            bf = new BufferedReader(new InputStreamReader(Files.newInputStream(path), decoder), BUFFER_SIZE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        try {
            String first = bf.readLine();
            // a BOM in front of the first '[' would hide the first game's headers
            if (first != null && !first.isEmpty() && first.charAt(0) == '\uFEFF')
                first = first.substring(1);
            this.lookahead_line = first;
        } catch (IOException e) {
            try {
                bf.close();
            } catch (IOException ignored) { }
            throw new UncheckedIOException(e);
        }
        this.reader = bf;
    }

    @Override
    public boolean hasNext() {
        return !closed && lookahead_line != null;
    }

    @Override
    public PGNGameStub next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No more games in PGN database");
        }

        Map<String, String> headers = new LinkedHashMap<>();
        StringBuilder raw_pgn = new StringBuilder(INITIAL_GAME_CAPACITY);

        try {
            skipBlankLines();

            // parse headers
            while (lookahead_line != null && lookahead_line.startsWith("[")) {
                parseHeaderLine(lookahead_line, headers);
                raw_pgn.append(lookahead_line).append('\n');
                lookahead_line = reader.readLine();
            }

            skipBlankLines();
            raw_pgn.append('\n');

            // raw movetext
            boolean insideComment = false;
            while (lookahead_line != null) {
                if (startsNewGame(lookahead_line, insideComment)) break;

                insideComment = scanComments(lookahead_line, insideComment);
                raw_pgn.append(lookahead_line).append('\n');
                lookahead_line = reader.readLine();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return new PGNGameStub(headers, raw_pgn.toString());
    }

    /**
     * Skip empty lines
     */
    private void skipBlankLines() throws IOException {
        while (lookahead_line != null && lookahead_line.isBlank()) {
            lookahead_line = reader.readLine();
        }
    }

    /**
     * Parse <code>[Key "Value"]</code> and put into the given map.
     */
    private static void parseHeaderLine(String line, Map<String, String> headers) {
        String s = line.strip();
        int n = s.length();
        if (n < 2 || s.charAt(n - 1) != ']') return;

        int space = s.indexOf(' ');
        if (space < 0) return;

        String key = s.substring(1, space);
        String value = s.substring(space + 1, n - 1).strip();
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            value = value.substring(1, value.length() - 1);
        }
        headers.put(key, value);
    }

    /**
     * Get whether this line starts another game <br>
     * If inside comment but starts with "[Event ", returns true because of the possibility of typo.
     */
    private static boolean startsNewGame(String line, boolean insideComment) {
        if (line.isEmpty() || line.charAt(0) != '[') return false;
        return !insideComment || line.startsWith("[Event ");
    }

    /**
     * Tracks whether the end of this line is still inside a <code>{ }</code> comment. Braces after a
     * <code>;</code> (rest-of-line comment) don't count.
     */
    private static boolean scanComments(String line, boolean insideComment) {
        for (int i = 0, n = line.length(); i < n; i++) {
            char c = line.charAt(i);
            if (insideComment) {
                if (c == '}') insideComment = false;
            } else if (c == '{') {
                insideComment = true;
            } else if (c == ';') {
                break;
            }
        }
        return insideComment;
    }

    @Override
    public Iterator<PGNGameStub> iterator() {
        return this;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            reader.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
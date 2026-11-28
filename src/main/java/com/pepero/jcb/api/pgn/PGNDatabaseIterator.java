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
            while (first != null && first.isBlank()) {
                first = bf.readLine();
            }
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
            while (lookahead_line != null && (lookahead_line.isBlank()
                    || lookahead_line.stripLeading().startsWith("["))) {
                if (!lookahead_line.isBlank()) parseHeaderLine(lookahead_line, headers);
                raw_pgn.append(lookahead_line).append('\n');
                lookahead_line = reader.readLine();
            }

            raw_pgn.append('\n');

            MoveTextScanner scanner = new MoveTextScanner();
            while (lookahead_line != null) {
                if (scanner.atTopLevel() && lookahead_line.stripLeading().startsWith("[")) break;

                // scans line
                int boundary = scanner.scan(lookahead_line);

                // if this line ends current pgn
                if (boundary >= 0) {
                    // add to raw_pgn string builder
                    raw_pgn.append(lookahead_line, 0, boundary);
                    lookahead_line = lookahead_line.substring(boundary);
                    break;
                }
                raw_pgn.append(lookahead_line).append('\n');
                lookahead_line = reader.readLine();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return new PGNGameStub(headers, raw_pgn.toString());
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

    private static final class MoveTextScanner {
        private boolean insideComment;
        private int variationDepth;
        private boolean terminated;

        boolean atTopLevel() {
            return !insideComment && variationDepth == 0;
        }

        /**
         * Scans given line, checking the line and changing the state (is inside comment, variation depth, is this game terminated) <br>
         * Returns next game's starting index, if there isn't next game terminator or something on this line, returns -1
         *
         * @param line one of the pgn lines
         * @return next game's starting index, if there isn't next game terminator or something on this line, returns -1
         */
        int scan(String line) {
            if (!insideComment && line.startsWith("%")) return -1;
            for (int i = 0; i < line.length();) {
                char c = line.charAt(i);
                if (insideComment) {
                    if (c == '}') insideComment = false;
                    i++;
                } else if (Character.isWhitespace(c)) {
                    i++;
                } else if (c == '{') {
                    insideComment = true;
                    i++;
                } else if (c == ';') {
                    return -1;
                } else if (terminated) {
                    return i;
                } else if (c == '(') {
                    variationDepth++;
                    i++;
                } else if (c == ')') {
                    if (variationDepth > 0) variationDepth--;
                    i++;
                } else {
                    int start = i++;

                    // get delimiter index
                    while (i < line.length() && !isDelimiter(line.charAt(i))) i++;

                    if (variationDepth == 0) {
                        // and check is this termination token
                        String token = line.substring(start, i);
                        terminated = token.equals("1-0") || token.equals("0-1")
                                || token.equals("1/2-1/2") || token.equals("*");
                    }
                }
            }
            return -1;
        }

        private static boolean isDelimiter(char c) {
            return Character.isWhitespace(c) || c == '{' || c == '}'
                    || c == '(' || c == ')' || c == ';' || c == '$' || c == '.';
        }
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

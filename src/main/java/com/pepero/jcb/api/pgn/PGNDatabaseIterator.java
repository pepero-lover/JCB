package com.pepero.jcb.api.pgn;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
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
 * Each {@link #next()} call returns a {@link PGNGameStub} — headers are parsed
 * eagerly (cheap), but movetext is kept as raw text and only parsed into a full
 * {@link com.pepero.jcb.api.ChessGame} on demand via {@link PGNGameStub#toChessGame()}.
 * <p>
 * Not thread-safe. Must be closed (try-with-resources) to release the underlying file handle.
 */
public class PGNDatabaseIterator implements Iterator<PGNGameStub>, Iterable<PGNGameStub>, AutoCloseable {

    private final BufferedReader reader;

    // lookahead so we can peek at "does the next line start a new game" without consuming it
    private String lookahead_line;
    private boolean closed = false;

    public PGNDatabaseIterator(Path path) {
        try {
            this.reader = Files.newBufferedReader(path);
            this.lookahead_line = reader.readLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean hasNext() {
        return lookahead_line != null;
    }

    @Override
    public PGNGameStub next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No more games in PGN database");
        }

        Map<String, String> headers = new LinkedHashMap<>();
        StringBuilder raw_pgn = new StringBuilder();

        try {
            // skip empty lines before headers
            while (lookahead_line != null && lookahead_line.isBlank()) {
                lookahead_line = reader.readLine();
            }

            // parse headers
            while (lookahead_line != null && lookahead_line.startsWith("[")) {
                String stripped = lookahead_line.substring(1, lookahead_line.length() - 1);
                String[] parts = stripped.split(" ", 2);
                if (parts.length == 2) {
                    headers.put(parts[0], parts[1].replace("\"", ""));
                }

                raw_pgn.append(lookahead_line).append('\n');
                lookahead_line = reader.readLine();
            }

            // skip empty lines between move text and headers
            while (lookahead_line != null && lookahead_line.isBlank()) {
                lookahead_line = reader.readLine();
            }

            raw_pgn.append('\n');

            // raw pgn parsing
            boolean insideComment = false;
            while (lookahead_line != null) {
                if (!insideComment && lookahead_line.startsWith("[")) break;

                for (int i = 0; i < lookahead_line.length(); i++) {
                    char c = lookahead_line.charAt(i);
                    if (c == '{') insideComment = true;
                    else if (c == '}') insideComment = false;
                }

                raw_pgn.append(lookahead_line).append('\n');
                lookahead_line = reader.readLine();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return new PGNGameStub(headers, raw_pgn.toString());
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
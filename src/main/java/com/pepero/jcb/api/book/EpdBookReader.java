package com.pepero.jcb.api.book;

import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads an EPD opening book file (one position per line) and picks
 * a starting FEN for each game.
 */
public class EpdBookReader {
    private final List<String> positions = new ArrayList<>();

    public EpdBookReader(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new RuntimeException("Could not find EPD opening book file! (file path : " + filePath + ")");
        }

        try {
            List<String> lines = Files.readAllLines(file.toPath());
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                positions.add(toFullFen(trimmed));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (positions.isEmpty()) {
            throw new RuntimeException("EPD opening book is empty! (file path : " + filePath + ")");
        }
    }

    private String toFullFen(String epdLine) {
        String[] tokens = epdLine.split("\\s+");
        if (tokens.length < 4) {
            throw new RuntimeException("Invalid EPD line: " + epdLine);
        }
        return tokens[0] + " " + tokens[1] + " " + tokens[2] + " " + tokens[3] + " 0 1";
    }

    public int size() {
        return positions.size();
    }

    /**
     * Pick random position from .epd data (non-reproducible)
     *
     * @return picked random position
     */
    public String pickRandomPosition() {
        return positions.get(new SecureRandom().nextInt(positions.size()));
    }

    /**
     * Pick a position deterministically from an arbitrary seed value
     * (round number, hash-derived seed, etc). Reproducible given the same seed.
     *
     * @param seed arbitrary seed value (can be negative, e.g. from Objects.hash)
     * @return picked position
     */
    public String pickSequentialPosition(int seed) {
        int index = Math.floorMod(seed, positions.size());
        return positions.get(index);
    }
}
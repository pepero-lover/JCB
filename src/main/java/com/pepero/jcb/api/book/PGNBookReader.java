package com.pepero.jcb.api.book;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.List;

/**
 * Reads a multi-game PGN opening book file (loaded fully into memory) and picks
 * a random game's raw PGN text to use as an opening.
 */
public class PGNBookReader {
    private final List<String> games;

    public PGNBookReader(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new RuntimeException("Could not find PGN opening book file! (file path : " + filePath + ")");
        }

        try {
            String pgnText = Files.readString(file.toPath());
            this.games = PGNSplitter.splitGames(pgnText);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (games.isEmpty()) {
            throw new RuntimeException("PGN opening book is empty! (file path : " + filePath + ")");
        }
    }

    public int size() {
        return games.size();
    }

    /**
     * Pick random game's raw PGN text (non-reproducible)
     *
     * @return picked random game
     */
    public String pickRandomGame() {
        return games.get(new SecureRandom().nextInt(games.size()));
    }

    /**
     * Pick a game deterministically from an arbitrary seed value
     * (round number, hash-derived seed, etc.). Reproducible given the same seed.
     *
     * @param seed arbitrary seed value (can be negative, e.g. from Objects.hash)
     * @return picked game
     */
    public String pickSequentialGame(int seed) {
        int index = Math.floorMod(seed, games.size());
        return games.get(index);
    }
}
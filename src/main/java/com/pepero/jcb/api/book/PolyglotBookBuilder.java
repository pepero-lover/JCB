package com.pepero.jcb.api.book;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class PolyglotBookBuilder {

    /**
     * Build a polyglot opening book (.bin) from a multi-game PGN file.
     *
     * @param pgnFilePath input PGN file path
     * @param outBinPath  output .bin file path
     * @param maxPly      max ply depth to include per game (e.g. 20~30)
     */
    public static void build(String pgnFilePath, String outBinPath, int maxPly) throws IOException {
        String pgnText = Files.readString(Path.of(pgnFilePath));

        List<String> games = PGNSplitter.splitGames(pgnText);
        System.out.println("Parsed " + games.size() + " games from PGN.");

        Map<PGNBookAggregator.BookKey, Integer> weightMap = PGNBookAggregator.aggregate(games, maxPly);
        System.out.println("Collected " + weightMap.size() + " unique (position, move) entries.");

        PolyglotBookWriter.write(weightMap, outBinPath);
        System.out.println("Book written to " + outBinPath);
    }
}
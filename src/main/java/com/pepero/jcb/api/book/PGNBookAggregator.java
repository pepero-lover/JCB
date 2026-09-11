package com.pepero.jcb.api.book;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PGNBookAggregator {

    record BookKey(long hash, int polyMove) {}

    /**
     * Aggregate (hash, polyMove) occurrences across multiple games into weights.
     *
     * @param gamePGNs list of single-game PGN strings (from PGNSplitter.splitGames)
     * @param maxPly   max ply depth to extract per game
     * @return map of (hash, polyMove) -> occurrence count (weight)
     */
    public static Map<BookKey, Integer> aggregate(List<String> gamePGNs, int maxPly) {
        Map<BookKey, Integer> weightMap = new HashMap<>();

        int failedCount = 0;

        for (String gamePGN : gamePGNs) {
            List<PGNBookExtractor.BookMove> moves = PGNBookExtractor.extract(gamePGN, maxPly);

            if (moves.isEmpty() && !gamePGN.isBlank()) {
                failedCount++;
                continue;
            }

            for (PGNBookExtractor.BookMove bm : moves) {
                BookKey key = new BookKey(bm.hash(), bm.polyMove());
                weightMap.merge(key, 1, Integer::sum);
            }
        }

        if (failedCount > 0) {
            System.err.println("[PGNBookAggregator] " + failedCount + " games failed to parse and were skipped.");
        }

        return weightMap;
    }
}
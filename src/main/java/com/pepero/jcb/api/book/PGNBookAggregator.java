package com.pepero.jcb.api.book;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PGNBookAggregator {

    record BookKey(long hash, int polyMove) {}

    /**
     * Aggregate (hash, polyMove) occurrences across multiple games into weights.
     *
     * @param gamePgns list of single-game PGN strings (from PGNSplitter.splitGames)
     * @param maxPly   max ply depth to extract per game
     * @return map of (hash, polyMove) -> occurrence count (weight)
     */
    public static Map<BookKey, Integer> aggregate(List<String> gamePgns, int maxPly) {
        Map<BookKey, Integer> weightMap = new HashMap<>();

        int failedCount = 0;

        for (String gamePgn : gamePgns) {
            List<PGNBookExtractor.BookMove> moves = PGNBookExtractor.extract(gamePgn, maxPly);

            if (moves.isEmpty() && !gamePgn.isBlank()) {
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
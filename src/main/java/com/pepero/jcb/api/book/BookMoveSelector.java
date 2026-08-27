package com.pepero.jcb.api.book;

import java.security.SecureRandom;
import java.util.List;

/**
 * Selects a move from a list of {@link BookEntry} using various strategies
 * (pure random, weighted random, or a deterministic seed).
 * Decoupled from how the entries were obtained (e.g. {@link PolyglotBookReader#findMoves(long)}).
 */
public class BookMoveSelector {

    /**
     * Pick uniformly at random among all entries, ignoring weight.
     *
     * @return picked move (null if entries is empty)
     */
    public static String pickUniformRandom(List<BookEntry> entries) {
        if (entries.isEmpty()) return null;
        return entries.get(new SecureRandom().nextInt(entries.size())).lanMove();
    }

    /**
     * Pick weighted-randomly: entries with higher weight are proportionally
     * more likely to be picked. Falls back to the first entry if total weight is 0.
     *
     * @return picked move (null if entries is empty)
     */
    public static String pickWeightedRandom(List<BookEntry> entries) {
        if (entries.isEmpty()) return null;

        int totalWeight = entries.stream().mapToInt(BookEntry::weight).sum();
        if (totalWeight == 0) return entries.getFirst().lanMove();

        int randomVal = new SecureRandom().nextInt(totalWeight);
        int currentSum = 0;
        for (BookEntry entry : entries) {
            currentSum += entry.weight();
            if (randomVal < currentSum) return entry.lanMove();
        }
        return entries.getFirst().lanMove();
    }

    /**
     * Pick a move deterministically from an arbitrary seed value, weighted
     * by move weight. Reproducible given the same seed and same entries.
     *
     * @param entries book entries
     * @param seed    arbitrary seed value (can be negative, e.g. from Objects.hash)
     * @return picked move (null if entries is empty)
     */
    public static String pickWeightedBySeed(List<BookEntry> entries, int seed) {
        if (entries.isEmpty()) return null;

        int totalWeight = entries.stream().mapToInt(BookEntry::weight).sum();
        if (totalWeight == 0) return entries.getFirst().lanMove();

        int seededVal = Math.floorMod(seed, totalWeight);
        int currentSum = 0;
        for (BookEntry entry : entries) {
            currentSum += entry.weight();
            if (seededVal < currentSum) return entry.lanMove();
        }
        return entries.getFirst().lanMove();
    }

    /**
     * Pick the move with the highest weight (most common move in the source games).
     * Fully deterministic, no randomness or seed involved.
     *
     * @return picked move (null if entries is empty)
     */
    public static String pickBestMove(List<BookEntry> entries) {
        if (entries.isEmpty()) return null;

        BookEntry best = entries.getFirst();
        for (BookEntry entry : entries) {
            if (entry.weight() > best.weight()) {
                best = entry;
            }
        }
        return best.lanMove();
    }
}
package com.pepero.jcb.api.book;

/**
 * Book entry dto for {@link PolyglotBookReader}
 *
 * @param key polyglot hash key on this position (not yet played)
 * @param lanMove lan move data
 * @param weight weight / preference of this move
 */
public record BookEntry(
        long key,
        String lanMove,
        int weight
) {}
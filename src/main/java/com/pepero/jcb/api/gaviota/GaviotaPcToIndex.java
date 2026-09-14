package com.pepero.jcb.api.gaviota;

/** One index-computing function per material class, matching a sub-table's piece order. */
@FunctionalInterface
interface GaviotaPcToIndex {
    long apply(GaviotaRequest req);
}

/**
 * Bundles what gtb-probe.c's {@code egkey[]} entry stores per material class:
 * {@code maxindex} (index-space size) and {@code pctoi} (the index-computing
 * function pointer). {@code sliceN} ("pawn slice" count — 24 for one
 * pawn-file-normalized pawn, a PP/PPP-index max for two/three pawns, 1 for
 * pawnless materials) mirrors the C's {@code slice_n} field, used the same
 * way when partitioning a table's block-cache space by pawn slice.
 */
record GaviotaEndgameKey(long maxIndex, int sliceN, GaviotaPcToIndex pctoi) {
}
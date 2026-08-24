package com.pepero.jcb.api.gaviota;

/** One index-computing function per material class, matching a sub-table's piece order. */
@FunctionalInterface
interface GaviotaPcToIndex {
    long apply(GaviotaRequest req);
}

/**
 * Ported from gaviota.py's EndgameKey. maxIndex bounds the index space for
 * this material (used to compute block counts); sliceN is the "pawn slice"
 * count (24 for materials with exactly one pawn-file-normalized pawn, etc,
 * 1 for pawnless materials) used the same way gaviota.py uses it.
 */
record GaviotaEndgameKey(long maxIndex, int sliceN, GaviotaPcToIndex pctoi) {
}
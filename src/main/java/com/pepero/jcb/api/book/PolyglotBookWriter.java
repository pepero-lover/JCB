package com.pepero.jcb.api.book;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class PolyglotBookWriter {

    /**
     * Write aggregated (hash, polyMove) -> weight map into a polyglot .bin file.
     *
     * @param weightMap aggregated weight map (from PGNBookAggregator.aggregate)
     * @param outPath   output .bin file path
     */
    public static void write(Map<PGNBookAggregator.BookKey, Integer> weightMap, String outPath) {
        List<Map.Entry<PGNBookAggregator.BookKey, Integer>> entries = new ArrayList<>(weightMap.entrySet());

        entries.sort((a, b) -> Long.compareUnsigned(a.getKey().hash(), b.getKey().hash()));

        try (RandomAccessFile raf = new RandomAccessFile(outPath, "rw")) {
            raf.setLength(0);

            for (var entry : entries) {
                long hash = entry.getKey().hash();
                int polyMove = entry.getKey().polyMove();
                int weight = Math.min(entry.getValue(), 0xFFFF); // unsigned short clamp

                raf.writeLong(hash);
                raf.writeShort(polyMove);
                raf.writeShort(weight);
                raf.writeInt(0); // learn (not used)
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write polyglot book to " + outPath, e);
        }
    }
}
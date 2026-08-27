package com.pepero.jcb.api.book;

import com.pepero.jcb.core.Chessboard;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public class PolyglotBookReader {
    private final File bookFile;

    public PolyglotBookReader(String filePath) {
        this.bookFile = new File(filePath);
        if (!this.bookFile.exists()) {
            throw new RuntimeException("Could not find opening book file! (file path : " + filePath + ")");
        }
    }

    /**
     * Get suggested all opening moves on chess opening data
     *
     * @param polyglotHash chess board hash which can be gotten on {@link PolyglotHashUtils#getPolyglotHash(Chessboard)}
     */
    public List<BookEntry> findMoves(long polyglotHash) {
        List<BookEntry> entries = new ArrayList<>();

        // readonly
        try (RandomAccessFile raf = new RandomAccessFile(bookFile, "r")) {
            long low = 0;
            long high = (raf.length() / 16) - 1;
            long match_index = -1;

            // binary search
            while (low <= high) {
                long mid = (low + high) >>> 1;
                raf.seek(mid * 16);
                long key = raf.readLong();

                if (Long.compareUnsigned(key, polyglotHash) < 0) {
                    low = mid + 1;
                } else if (Long.compareUnsigned(key, polyglotHash) > 0) {
                    high = mid - 1;
                } else {
                    match_index = mid;
                    break;
                }
            }

            if (match_index != -1) {
                long startIdx = match_index;
                while (startIdx > 0) {
                    raf.seek((startIdx - 1) * 16);
                    if (raf.readLong() == polyglotHash) {
                        startIdx--;
                    } else {
                        break;
                    }
                }

                raf.seek(startIdx * 16);
                while (raf.getFilePointer() < raf.length()) {
                    long key = raf.readLong();
                    if (key != polyglotHash) break;

                    int moveData = raf.readUnsignedShort();
                    int weight = raf.readUnsignedShort();
                    raf.readInt();

                    String lanMove = PolyglotHashUtils.decodePolyglotMove(moveData);
                    entries.add(new BookEntry(key, lanMove, weight));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return entries;
    }

    /**
     * Pick random book move
     *
     * @param polyglotHash position polyglot hash
     * @return lan (or uci) book move
     */
    public String pickRandomMove(long polyglotHash) {
        List<BookEntry> entries = findMoves(polyglotHash);
        if (entries.isEmpty()) return null;

        int totalWeight = entries.stream().mapToInt(BookEntry::weight).sum();

        if (totalWeight == 0) return entries.getFirst().lanMove();

        int randomVal = new SecureRandom().nextInt(totalWeight);
        int currentWeightSum = 0;

        for (BookEntry entry : entries) {
            currentWeightSum += entry.weight();
            if (randomVal < currentWeightSum) {
                return entry.lanMove();
            }
        }
        return entries.getFirst().lanMove();
    }

    public String pickSequentialMove(long polyglotHash, int roundNumber) {
        List<BookEntry> entries = findMoves(polyglotHash);
        if (entries.isEmpty()) return null;

        entries.sort((a, b) -> Integer.compare(b.weight(), a.weight()));

        int index = (roundNumber - 1) % entries.size();

        return entries.get(index).lanMove();
    }
}

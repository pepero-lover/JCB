package com.pepero.jcb.api.syzygy;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

/**
 * A random-access view over a memory-mapped file that can exceed
 * {@link Integer#MAX_VALUE} bytes.
 * <p>
 * {@link FileChannel#map} returns a {@link MappedByteBuffer}, whose addressing
 * is {@code int}-based, capping a single mapping at ~2GB. Syzygy 7-piece
 * tablebase files can be tens of GB, so this stitches together several
 * {@link MappedByteBuffer} windows and exposes long-offset accessors instead.
 * All multi-byte reads are little-endian, matching the Syzygy header format
 * (the compressed pair data itself is big-endian and is read via
 * {@link #getUnsignedByte}-based helpers in the reader, unaffected by this
 * class's default order).
 */
final class SyzygyMappedFile {

    // 1 GiB per window — comfortably under the 2GB cap, power-of-two for shift/mask.
    private static final int WINDOW_BITS = 30;
    private static final long WINDOW_SIZE = 1L << WINDOW_BITS;
    private static final long WINDOW_MASK = WINDOW_SIZE - 1;

    private final MappedByteBuffer[] windows;
    private final long size;

    private SyzygyMappedFile(MappedByteBuffer[] windows, long size) {
        this.windows = windows;
        this.size = size;
        for (MappedByteBuffer w : windows) {
            w.order(ByteOrder.LITTLE_ENDIAN);
        }
    }

    static SyzygyMappedFile map(Path path) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r");
             FileChannel channel = raf.getChannel()) {

            long size = channel.size();
            int windowCount = (int) Math.max(1, (size + WINDOW_SIZE - 1) / WINDOW_SIZE);

            MappedByteBuffer[] windows = new MappedByteBuffer[windowCount];
            for (int i = 0; i < windowCount; i++) {
                long offset = (long) i * WINDOW_SIZE;
                long length = Math.min(WINDOW_SIZE, size - offset);
                windows[i] = channel.map(FileChannel.MapMode.READ_ONLY, offset, length);
            }
            return new SyzygyMappedFile(windows, size);
        }
    }

    public long size() {
        return size;
    }

    public byte getByte(long offset) {
        int w = (int) (offset >>> WINDOW_BITS);
        int localOffset = (int) (offset & WINDOW_MASK);
        return windows[w].get(localOffset);
    }

    public int getUnsignedByte(long offset) {
        return getByte(offset) & 0xFF;
    }

    /** Little-endian 16-bit read; falls back to byte-assembly across a window boundary. */
    public int getUnsignedShort(long offset) {
        int w = (int) (offset >>> WINDOW_BITS);
        int localOffset = (int) (offset & WINDOW_MASK);
        if (localOffset <= windows[w].limit() - 2) {
            return windows[w].getShort(localOffset) & 0xFFFF;
        }
        return getUnsignedByte(offset) | (getUnsignedByte(offset + 1) << 8);
    }

    /** Little-endian 32-bit read; falls back to byte-assembly across a window boundary. */
    public int getInt(long offset) {
        int w = (int) (offset >>> WINDOW_BITS);
        int localOffset = (int) (offset & WINDOW_MASK);
        if (localOffset <= windows[w].limit() - 4) {
            return windows[w].getInt(localOffset);
        }
        return getUnsignedByte(offset)
                | (getUnsignedByte(offset + 1) << 8)
                | (getUnsignedByte(offset + 2) << 16)
                | (getUnsignedByte(offset + 3) << 24);
    }

    /** Little-endian 64-bit read, same boundary fallback as {@link #getInt}. */
    public long getLong(long offset) {
        int w = (int) (offset >>> WINDOW_BITS);
        int localOffset = (int) (offset & WINDOW_MASK);
        if (localOffset <= windows[w].limit() - 8) {
            return windows[w].getLong(localOffset);
        }
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result |= ((long) getUnsignedByte(offset + i)) << (8 * i);
        }
        return result;
    }

    /** Big-endian 32-bit read (for the compressed pair data stream). */
    public long getBEUnsignedInt(long offset) {
        return ((long) getUnsignedByte(offset) << 24)
                | (getUnsignedByte(offset + 1) << 16)
                | (getUnsignedByte(offset + 2) << 8)
                | getUnsignedByte(offset + 3);
    }

    /** Big-endian 64-bit read (for the compressed pair data stream). */
    public long getBEUnsignedLong(long offset) {
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result = (result << 8) | getUnsignedByte(offset + i);
        }
        return result;
    }

    /**
     * Bulk copy for reading raw compressed-block data; spans window boundaries transparently.
     */
    public void get(long offset, byte[] dst) {
        get(offset, dst, 0, dst.length);
    }

    public void get(long offset, byte[] dst, int dstOffset, int length) {
        int remaining = length;
        long pos = offset;
        int outPos = dstOffset;
        while (remaining > 0) {
            int w = (int) (pos >>> WINDOW_BITS);
            int localOffset = (int) (pos & WINDOW_MASK);
            MappedByteBuffer window = windows[w];
            int chunk = Math.min(window.limit() - localOffset, remaining);

            ByteBuffer dup = window.duplicate(); // don't disturb the shared window
            dup.position(localOffset);
            dup.get(dst, outPos, chunk);

            pos += chunk;
            outPos += chunk;
            remaining -= chunk;
        }
    }
}
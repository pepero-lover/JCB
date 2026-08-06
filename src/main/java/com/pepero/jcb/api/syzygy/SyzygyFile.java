package com.pepero.jcb.api.syzygy;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

/**
 * Reads a Syzygy tablebase file (.rtbw / .rtbz) and identifies its type
 * by checking the magic number in the first 4 bytes.
 * <p>
 * The file's contents are exposed as a MappedByteBuffer (via mmap), not a
 * loaded-into-heap byte[] — this is what lets us handle multi-GB/TB
 * tablebase files without ever needing that much Java heap. The OS pages
 * in only the parts we actually touch, on demand.
 */
class SyzygyFile {

    // enough bytes to safely read magic(4) + flags(1) without mapping the whole file
    // just to check the type; the real data access later goes through mapFile().
    private static final int TYPE_CHECK_SIZE = 5;

    private final Path syzygyPath;
    private final SyzygyType syzygyType;
    private final boolean split;

    private SyzygyFile(Path path, SyzygyType type, boolean split) {
        this.syzygyPath = path;
        this.syzygyType = type;
        this.split = split;
    }

    public Path getPath() {
        return syzygyPath;
    }

    public SyzygyType getType() {
        return syzygyType;
    }

    public boolean isSplit() {
        return split;
    }

    /**
     * Open a tablebase file and verify its magic number.
     *
     * @param path path to a .rtbw or .rtbz file
     * @return SyzygyFile if the magic number is valid and matches a known type
     * @throws IOException if the file can't be read
     * @throws IllegalArgumentException if the magic number doesn't match any known tablebase type
     */
    public static SyzygyFile open(Path path) throws IOException {
        byte[] header = readSmallHeader(path, TYPE_CHECK_SIZE);

        int magic = ByteBuffer.wrap(header, 0, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt();

        SyzygyType type = SyzygyType.fromMagic(magic);
        if (type == null) {
            throw new IllegalArgumentException(
                    "Not a valid Syzygy tablebase file (unrecognized magic number: 0x"
                            + Integer.toHexString(magic) + "): " + path);
        }

        boolean split = computeSplit(header, type);

        return new SyzygyFile(path, type, split);
    }

    /**
     * Map the entire file into memory (via mmap) for random-access reads.
     * This does NOT load the file content into Java heap — only touched
     * pages get paged in by the OS as they're actually read.
     *
     * @param path path to the file to map
     * @return a MappedByteBuffer covering the whole file
     */
    public static MappedByteBuffer mapFile(Path path) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r");
             FileChannel channel = raf.getChannel()) {
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
        }
    }

    /**
     * split = (type != DTZ) && (byte4 bit0 set). DTZ is always single-sided.
     */
    private static boolean computeSplit(byte[] header, SyzygyType type) {
        if (type == SyzygyType.DTZ) {
            return false;
        }
        int byte4 = header[4] & 0xff;
        return (byte4 & 0x01) != 0;
    }

    /**
     * Read just the first `size` bytes — enough to identify type/split
     * without mapping (or reading) the whole file.
     */
    private static byte[] readSmallHeader(Path path, int size) throws IOException {
        byte[] header = new byte[size];
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            int bytesRead = raf.read(header);
            if (bytesRead < size) {
                throw new IOException("File too small to be a valid tablebase file: " + path);
            }
        }
        return header;
    }
}
package com.pepero.jcb.api.syzygy;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;

/**
 * Read a single Syzygy tablebase file (.rtbw, .rtbz file) and get type
 * by checking the magic number in the first 4 bytes
 */
public class SyzygyFile {
    // syzygy file path
    private final Path syzygyPath;

    // Syzygy type
    private final SyzygyType syzygyType;

    // false if both pieces type is symmetrical like KRvKR, otherwise like KPvK, true
    private final boolean split;

    // how many header bytes we read up front
    private static final int HEADER_SIZE = 64;

    // [subtable index][side (0 = wtm) (1 = btm)]
    private SyzygyPairsHeader[][] pairsHeaders;

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
     * Open syzygy file and identify its type by magic number
     *
     * @param path path to syzygy file
     * @return Syzygy file
     * @throws IOException when reading file failed
     * @throws IllegalArgumentException when syzygy magic number not matches
     */
    public static SyzygyFile open(Path path) throws IOException {
        // get headers
        byte[] header = readHeader(path);

        // magic number to identify type
        int magic = ByteBuffer.wrap(header, 0, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt();

        SyzygyType type = SyzygyType.fromMagic(magic);
        if (type == null) {
            throw new IllegalArgumentException(
                    "Not a valid Syzygy tablebase file (unrecognized magic number: 0x"
                            + Integer.toHexString(magic) + "): " + path);
        }

        // get spilt flag
        boolean split = computeSplit(header, type);

        return new SyzygyFile(path, type, split);
    }

    /**
     * Read the first HEADER_SIZE bytes of the file.
     */
    public static byte[] readHeader(Path path) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            long fileLength = raf.length();
            byte[] header = new byte[(int) fileLength];
            raf.readFully(header);
            return header;
        }
    }

    /**
     * Get Split value
     *
     * @param header headers
     * @param type syzygy type
     * @return split value
     */
    private static boolean computeSplit(byte[] header, SyzygyType type) {
        // if DTZ mode, returns false
        if (type == SyzygyType.DTZ) {
            return false;
        }

        // when WDL mode, get spilt flag from header
        int byte4 = header[4] & 0xff; // mask to avoid sign-extension issues
        return (byte4 & 0x01) != 0;
    }
}

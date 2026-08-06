package com.pepero.jcb.api.syzygy;

/**
 * Syzygy tablebase file type (WDL / DTZ) <p>
 */
enum SyzygyType {
    WDL(0x5d23e871, ".rtbw"),
    DTZ(0xa50c66d7, ".rtbz");

    private final int magic;        // magic number to identify type
    private final String extension; // file extension string

    SyzygyType(int magic, String extension) {
        this.magic = magic;
        this.extension = extension;
    }

    public int getMagic() {
        return magic;
    }

    public String getExtension() {
        return extension;
    }

    /**
     * Get Syzygy type by the magic number
     *
     * @param magic magic number
     * @return Syzygy type
     */
    public static SyzygyType fromMagic(int magic) {
        for (SyzygyType type : values()) {
            if (type.magic == magic) {
                return type;
            }
        }
        return null;
    }
}

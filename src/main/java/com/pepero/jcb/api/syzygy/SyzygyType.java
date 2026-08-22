package com.pepero.jcb.api.syzygy;

/**
 * Syzygy tablebase file type (WDL / DTZ) <p>
 */
enum SyzygyType {
    // for standard
    WDL(0x5d23e871, ".rtbw", false),
    DTZ(0xa50c66d7, ".rtbz", false),

    // for atomic
    ATOMIC_WDL(0x49a48d55, ".atbw", true),
    ATOMIC_DTZ(0xeb5ea991, ".atbz", true);

    private final int magic;
    private final String extension;
    private final boolean connectedKings;

    SyzygyType(int magic, String extension, boolean connectedKings) {
        this.magic = magic;
        this.extension = extension;
        this.connectedKings = connectedKings;
    }

    public int getMagic() { return magic; }
    public String getExtension() { return extension; }
    public boolean isConnectedKings() { return connectedKings; }

    public boolean isWdl() { return this == WDL || this == ATOMIC_WDL; }
    public boolean isDtz() { return this == DTZ || this == ATOMIC_DTZ; }

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

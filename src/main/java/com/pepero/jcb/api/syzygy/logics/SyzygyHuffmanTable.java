package com.pepero.jcb.api.syzygy.logics;

public class SyzygyHuffmanTable {
    private byte[] symPat;
    private int numSyms;
    private int[] symLen;
    private int[] offset;
    private long[] base;
    private int minLen;

    public SyzygyHuffmanTable(byte[] symPat, int numSyms, int[] symLen, int[] offset, long[] base, int minLen) {
        this.symPat = symPat;
        this.numSyms = numSyms;
        this.symLen = symLen;
        this.offset = offset;
        this.base = base;
        this.minLen = minLen;
    }

    public static SyzygyHuffmanTable build(byte[] symPat, int numSyms, int[] offsetRaw, int minLen) {
        int[] symLen = new int[numSyms];
        boolean[] visited = new boolean[numSyms];

        for (int s = 0; s < numSyms; s++) {
            if (!visited[s]) {
                calcSymLen(s, symPat, symLen, visited);
            }
        }

        // calculate base
        int h = offsetRaw.length;
        long[] base = new long[h];
        base[h - 1] = 0;
        for (int i = h - 2; i >= 0; i--) {
            base[i] = (base[i + 1] + offsetRaw[i] - offsetRaw[i + 1]) / 2;
        }
        for (int i = 0; i < h; i++) {
            base[i] <<= (64 - (minLen + i));
        }

        return new SyzygyHuffmanTable(symPat, numSyms, symLen, offsetRaw, base, minLen);
    }

    public byte[] getSymPat() {
        return symPat;
    }

    public int getNumSyms() {
        return numSyms;
    }

    public int[] getSymLen() {
        return symLen;
    }

    public int[] getOffset() {
        return offset;
    }

    public long[] getBase() {
        return base;
    }

    public int getMinLen() {
        return minLen;
    }

    /**
     * Calculate Sym Length
     *
     * @param s s value
     * @param symPat symPat array
     * @param symLen result symLen array
     * @param visited visited array
     */
    private static void calcSymLen(int s, byte[] symPat, int[] symLen, boolean[] visited) {
        int w0 = symPat[3*s] & 0xFF;
        int w1 = symPat[3*s+1] & 0xFF;
        int w2 = symPat[3*s+2] & 0xFF;

        // calculate s2 (child)
        int s2 = (w2 << 4) | (w1 >> 4);

        if(s2 == 0x0FFF) { // when leaf node found
            symLen[s] = 0;
        } else {
            // calculate s1 (child)
            int s1 = ((w1 & 0x0F) << 8) | w0;

            // when not visited s1, s2, visit
            if(!visited[s1]) calcSymLen(s1, symPat, symLen, visited);
            if(!visited[s2]) calcSymLen(s2, symPat, symLen, visited);
            symLen[s] = symLen[s1] + symLen[s2] + 1;
        }

        visited[s] = true;
    }
}

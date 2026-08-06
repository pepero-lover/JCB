package com.pepero.jcb.api.syzygy;

class SyzygyHuffmanTable {
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
     * Calculate Sym Length (iterative version)
     * <p>
     * This was originally a recursive function, but DTZ files can have much deeper
     * RePair symbol chains than WDL (more distinct distance values -> richer alphabet
     * -> deeper pairing trees), which can exceed Java's default thread stack size
     * even though the equivalent C recursion never overflowed (C's default stack
     * is usually much larger). Using an explicit heap-allocated stack instead of
     * the JVM call stack avoids that limit entirely, with identical results.
     *
     * @param startS starting symbol index
     * @param symPat symPat array
     * @param symLen result symLen array
     * @param visited visited array
     */
    private static void calcSymLen(int startS, byte[] symPat, int[] symLen, boolean[] visited) {
        java.util.Deque<Integer> stack = new java.util.ArrayDeque<>();
        stack.push(startS);

        while (!stack.isEmpty()) {
            int s = stack.peek();

            if (visited[s]) {
                stack.pop();
                continue;
            }

            int w0 = symPat[3 * s] & 0xFF;
            int w1 = symPat[3 * s + 1] & 0xFF;
            int w2 = symPat[3 * s + 2] & 0xFF;

            // calculate s2 (child)
            int s2 = (w2 << 4) | (w1 >> 4);

            if (s2 == 0x0FFF) { // leaf node
                symLen[s] = 0;
                visited[s] = true;
                stack.pop();
                continue;
            }

            // calculate s1 (child)
            int s1 = ((w1 & 0x0F) << 8) | w0;

            // if either child isn't resolved yet, push it and come back to `s` later
            // (both get pushed if both unresolved; whichever is processed first will
            // still leave `s` on the stack until BOTH children are done)
            boolean waitingOnChild = false;
            if (!visited[s1]) {
                stack.push(s1);
                waitingOnChild = true;
            }
            if (!visited[s2]) {
                stack.push(s2);
                waitingOnChild = true;
            }

            if (!waitingOnChild) {
                symLen[s] = symLen[s1] + symLen[s2] + 1;
                visited[s] = true;
                stack.pop();
            }
        }
    }
}

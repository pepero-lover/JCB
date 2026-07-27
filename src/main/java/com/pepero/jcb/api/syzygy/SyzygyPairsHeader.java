package com.pepero.jcb.api.syzygy;

public record SyzygyPairsHeader(
        boolean isConstant, // when constant
        int constValue,     // const value


        int flags,          // 1 byte
        int blockSize,      // 1 byte
        int idxBits,        // 1 byte
        int delta,          // 1 byte
        long realNumBlocks,  // 4 bytes
        int maxLen,         // 1 byte
        int minLen,         // 1 byte
        // 나중에 허프만 길이 배열 추가
        int numSyms         // 2 bytes
){
    public int totalByteSize() {
        if (isConstant) {
            return 2;
        }

        return
                12 // fixed header size
                + 2 * (maxLen - minLen + 1) // Huffman length
                + 3 * numSyms
                + (numSyms % 2 == 1 ? 1 : 0);
    }
}

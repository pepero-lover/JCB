package com.pepero.jcb.api.syzygy;

import java.util.Arrays;

public record SyzygyPairsHeader(
        boolean isConstant, // when constant
        int constValue,     // const value


        int flags,          // 1 byte
        int blockSize,      // 1 byte
        int idxBits,        // 1 byte
        int delta,          // 1 byte
        long realNumBlocks, // 4 bytes
        int maxLen,         // 1 byte
        int minLen,         // 1 byte
        SyzygyHuffmanTable huffmanTable
){
    public int totalByteSize() {
        if (isConstant) {
            return 2;
        }

        return
                12 // fixed header size
                + 2 * (maxLen - minLen + 1) // Huffman length
                + 3 * huffmanTable().getNumSyms()
                + (huffmanTable().getNumSyms() % 2 == 1 ? 1 : 0);
    }

    @Override
    public String toString() {
        return "SyzygyPairsHeader{" +
                "isConstant=" + isConstant +
                ", constValue=" + constValue +
                ", flags=" + flags +
                ", blockSize=" + blockSize +
                ", idxBits=" + idxBits +
                ", delta=" + delta +
                ", realNumBlocks=" + realNumBlocks +
                ", maxLen=" + maxLen +
                ", minLen=" + minLen +
                ", numSyms=" + huffmanTable().getNumSyms() +
                ", symPat=" + Arrays.toString(huffmanTable().getSymPat()) +
                '}';
    }
}

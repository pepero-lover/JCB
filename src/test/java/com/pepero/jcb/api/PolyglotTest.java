package com.pepero.jcb.api;

import com.pepero.jcb.api.book.BookEntry;
import com.pepero.jcb.api.book.PolyglotBookReader;

public class PolyglotTest {
    public static void main(String[] args) {
        System.out.println("오프닝 북 로딩 테스트 시작...");

        PolyglotBookReader reader = new PolyglotBookReader("polyglot/gm2001.bin");

        long startPosHash = 0x463B96181691FC9CL;

        long startTime = System.currentTimeMillis();

        var moves = reader.findMoves(startPosHash);

        long endTime = System.currentTimeMillis();

        System.out.println("탐색 시간: " + (endTime - startTime) + "ms");
        System.out.println("찾아낸 오프닝 수: " + moves.size() + "개");

        for (BookEntry entry : moves) {
            System.out.println("수(LAN): " + entry.lanMove() + " / 가중치(Weight): " + entry.weight());
        }
    }
}
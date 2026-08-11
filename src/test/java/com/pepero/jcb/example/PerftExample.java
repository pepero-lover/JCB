package com.pepero.jcb.example;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.perft.PerftDriver;
import com.pepero.jcb.core.Chessboard;

public class PerftExample {
    public static void main(String[] args) {
        String fen = ChessGame.START_POSITION;

        ChessGame chessGame = ChessGame.fromFEN(fen);

        System.out.println("--------------------");
        System.out.println("Perft API 1 threads");
        System.out.println("--------------------");

        // Perft 를 실행하고 결과를 저장합니다.

        // Javadoc 설명에도 나와 있듯이, perft(int depth) 는 싱글 스레드, JVM warmup 을 적용한 결과입니다.
        chessGame.perft(5);

        // 이번에는 스레드를 4개로 했을 때의 결과를 출력해보겠습니다.
        System.out.println();
        System.out.println("--------------------");
        System.out.println("Perft API 4 threads");
        System.out.println("--------------------");

        chessGame.perft(
                6, // perft 깊이
                4 // 사용할 스레드 수
        );

        // 이제는 Chessboard 기준으로 Perft 를 진행해보겠습니다.
        Chessboard chessboard = new Chessboard(fen);

        System.out.println();
        System.out.println("-------------------------");
        System.out.println("Perft Bitboard 1 threads");
        System.out.println("-------------------------");

        PerftDriver.perftBitboardTest(
                chessboard,
                6, // perft 깊이
                1, // 사용할 스레드 수
                false // 테스트 결과 및 출력을 하지 않을 것인지
                );

        System.out.println();
        System.out.println("-------------------------");
        System.out.println("Perft Bitboard 4 threads");
        System.out.println("-------------------------");

        PerftDriver.perftBitboardTest(
                chessboard,
                7, // perft 깊이
                4, // 사용할 스레드 수
                false // 테스트 결과 및 출력을 하지 않을 것인지
        );
    }
}

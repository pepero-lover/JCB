package com.pepero.jcb.example;

import com.pepero.jcb.api.ChessGame;

public class JumpExample {
    public static void main(String[] args) {
        ChessGame chessGame = ChessGame.startPosition();

        // 포지션 설정 ...

        chessGame.makeMove("e2e4");
        chessGame.makeMove("e7e5");
        chessGame.makeMove("g1f3");
        long uuid_g1f3 = chessGame.getCurrentNodeId();

        chessGame.makeMove("b8c6");

        // 무브 히스토리는 이렇게 됩니다.
        // e4 e5 Nf3 Nc6

        System.out.println("Position e4 e5 Nf3 Nf6");
        chessGame.printBoard();

        // 이때 Jump to node 를 호출 합니다.
        chessGame.jumpToNode(uuid_g1f3);
        // 이렇게 되면 e4 e5 Nf3 <-- 여기로 이동하게 됩니다

        System.out.println("Position e4 e5 Nf3");
        chessGame.printBoard();
    }
}

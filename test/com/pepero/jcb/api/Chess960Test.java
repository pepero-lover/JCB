package com.pepero.jcb.api;

import com.pepero.jcb.core.GameVariants;
import com.pepero.jcb.core.chess960.Chess960Utils;

public class Chess960Test {
    public static void main(String[] args) {
        // 체스 960 용 보드를 생성합니다. (체스 보드 fen 랜덤
        ChessGame chessGame = new ChessGame(
                Chess960Utils.generateRandom960Fen(),GameVariants.CHESS960);

        // 체스 보드를 프린트합니다.
        System.out.println(chessGame);

        // 그리고 참고할 것,
        // 체스 960 모드에서는 사용자가 캐슬링 입력을 킹이 룩을 잡는 입력으로 해야 합니다.
        // 예시 : - R - - - K - R 포지션일 때, f1h1 으로 해야 됩니다. f1g1으로 하면 예외가 발생합니다.
    }
}

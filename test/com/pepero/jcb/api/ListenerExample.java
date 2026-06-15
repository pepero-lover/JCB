package com.pepero.jcb.api;

import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.api.enums.GameResult;
import com.pepero.jcb.api.event.ChessGameListener;

public class ListenerExample {
    public static void main(String[] args) {
        // 먼저 체스 게임 겍체를 생성합니다.
        ChessGame game = new ChessGame();

        // 체스 보드 리스너를 추가합니다.
        game.addChessGameListener(new ChessGameListener() {

            // 수가 두어졌을 때 리스너입니다. 수가 두어지면 호출됩니다.
            @Override
            public void onMoveMade(MoveInfo moveInfo) {
                System.out.println("수가 발생하였습니다! ( 수 : " + moveInfo + " )");
            }

            // 게임이 종료되었을 때 리스너입니다. 게임이 종료되면 호출됩니다.
            @Override
            public void onGameOver(GameResult result, GameOverReason reason) {
                System.out.println("게임 종료!");
                System.out.println("결과 : " + result);
                System.out.println("이유 : " + reason);
            }
        });

        // 테스트용 수 두기
        game.makeMove("e2e4");
        game.forceEndGameExternal(GameResult.DRAW, GameOverReason.AGREEMENTDRAW);
    }
}

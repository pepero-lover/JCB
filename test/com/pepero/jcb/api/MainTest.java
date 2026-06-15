package com.pepero.jcb.api;

import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.api.enums.GameResult;
import com.pepero.jcb.api.event.ChessGameListener;
import com.pepero.jcb.api.tablebase.TablebaseUtils;
import com.pepero.jcb.core.chess960.Chess960Utils;

public class MainTest {
    public static void main(String[] args) {
        ChessGame game = new ChessGame();

        game.addChessGameListener(new ChessGameListener() {
            @Override
            public void onMoveMade(MoveInfo moveInfo) {
                System.out.println("효과음 재생: 틱! (" + moveInfo + " 이동 완료)");
            }

            @Override
            public void onGameOver(GameResult result, GameOverReason reason) {
                System.out.println("🚨 게임 종료 팝업 띄우기!");
                System.out.println("결과: " + result + " / 사유: " + reason);
            }
        });

        game.makeMove("e2e4");
        game.forceEndGameExternal(GameResult.DRAW, GameOverReason.AGREEMENTDRAW);
    }
}

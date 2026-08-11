package com.pepero.jcb.example;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.enums.GameOverReason;

public class GameStateExample {
    public static void main(String[] args) {
        // FEN 스트링으로 게임을 시작할 수 있습니다.
        // 예: 체크메이트 직전 상태의 FEN
        String scholarMateFen = "r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4";
        ChessGame game = ChessGame.fromFEN(scholarMateFen);

        // 백이 체크메이트 하는 상황을 가정합니다.
        game.makeMove("h5f7"); // e4 e5 Qh5 Nc6 Bc4 Nf6 Qxf7#

        // 게임 종료 여부 판단
        GameOverReason reason = game.isGameOver();
        if (reason != GameOverReason.NOTGAMEOVER) {
            System.out.println("게임 종료! 사유: " + reason);
        }
        
        // 개별 상태 체크도 가능 합니다.
        System.out.println("체크메이트 상태인가? : " + game.isCheckmate());
        System.out.println("체크 상태인가? : " + game.isCheck());
    }
}
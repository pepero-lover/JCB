package com.pepero.jcb.example;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.enums.Square;
import java.util.List;

public class GUIExample {
    public static void main(String[] args) {
        ChessGame chessGame = ChessGame.startPosition();

        // 사용자가 e2 칸에 있는 백 폰을 클릭했다고 가정합니다.
        Square clickedSquare = Square.e2;

        // e2 기물이 이동 할 수 있는 모든 수를 가져옵니다.
        List<MoveInfo> legalMoves = chessGame.getLegalMovesForSource(clickedSquare);

        System.out.println("e2 기물이 갈 수 있는 수 목록 ( LAN )");
        for (MoveInfo move : legalMoves) {
            System.out.println("- " + move.toString());
        }

        // 현재 체스판의 기물 점수 확인 (백 유리: 양수 / 흑 유리: 음수)
        System.out.println("현재 백 기준 기물 점수: " + chessGame.getPieceScore());
    }
}
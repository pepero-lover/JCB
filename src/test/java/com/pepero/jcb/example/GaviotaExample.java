package com.pepero.jcb.example;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.GaviotaAnalyzer;
import com.pepero.jcb.api.gaviota.GaviotaTablebase;
import com.pepero.jcb.api.dto.GaviotaMoveDTO;

import java.nio.file.Path;

public class GaviotaExample {

    public static void main(String[] args) {
        // 테이블 베이스 폴더를 가져옵니다.
        Path gaviotaDir = Path.of("gaviota/");
        // 테이블 베이스를 로드합니다.
        GaviotaTablebase tb = new GaviotaTablebase(gaviotaDir);

        // 예시 포지션
        ChessGame game = ChessGame.fromFEN("8/8/4r3/3k4/8/8/2Q5/7K w - - 0 1");

        game.printBoard();

        // WDL, DTM 결과를 보입니다.

        // WDL 데이터는 -1 ~ 1의 범위를 가집니다. (두는 쪽 기준)
        //  1 : 승리
        //  0 : 무승부
        // -1 : 패배

        // DTM 은 메이트까지 남은 하프무브 수를 알려줍니다.
        // 지고 있을 때는 (wdl 이 음수일 때) DTM 도 음수로 나옵니다.

        System.out.println("WDL : " + GaviotaAnalyzer.probeWdl(game, tb));
        System.out.println("DTM : " + GaviotaAnalyzer.probeDtm(game, tb));
        System.out.println();

        // 현재 포지션에서의 최선의 수를 찾을 수도 있습니다.
        System.out.println("Gaviota best move : " + GaviotaAnalyzer.findBestMove(game, tb));
        System.out.println();

        // 가능한 수들의 WDL DTM 결과를 전부 보여주는 메서드도 있습니다.
        for (GaviotaMoveDTO move : GaviotaAnalyzer.findRankedMoves(game, tb)) {
            System.out.println(move.move() + "  WDL" + move.ourWdl() + "  DTM" + move.distance());
        }
    }
}
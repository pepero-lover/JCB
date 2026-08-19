package com.pepero.jcb.example;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.syzygy.SyzygyTablebase;
import com.pepero.jcb.api.dto.SyzygyMoveDTO;

import java.io.IOException;
import java.nio.file.Path;

public class SyzygyExample {

    public static void main(String[] args) throws IOException {
        // 테이블 베이스 폴더를 가져옵니다.
        Path syzygyDir = Path.of("syzygy/");
        // 테이블 베이스를 로드합니다.
        SyzygyTablebase tb = new SyzygyTablebase(syzygyDir);

        // 예시 포지션
        ChessGame game = ChessGame.fromFEN("8/8/4r3/3k4/8/8/2Q5/7K w - - 0 1");

        game.printBoard();

        // WDL, DTZ 결과를 보입니다.

        // WDL 데이터는 -2 ~ 2의 범위를 가집니다. (두는 쪽 기준)
        //  2 : 완벽하게 플레이할 경우 승리함
        //  1 : 승리하지만 50수 규칙에 의해 무승부가 됨
        //  0 : 무승부
        // -1 : 패배하지만 50수 규칙에 의해 무승부가 됨
        // -2 : 완벽하게 플레이해도 패배함

        // DTZ 는 폰이나 기물을 잡기까지 얼마나 수가 남았는지를 알려줍니다.

        System.out.println("WDL : " + game.probeSyzygyWdl(tb));
        System.out.println("DTZ : " + game.probeSyzygyDtz(tb));
        System.out.println();

        // 현재 포지션에서의 최선의 수를 찾을 수도 있습니다.
        System.out.println("Syzygy best move : " + game.findBestMoveSyzygy(tb));
        System.out.println();

        // 가능한 수들의 WDL DTZ 결과를 전부 보여주는 메서드도 있습니다.
        for(SyzygyMoveDTO move : game.findRankedSyzygyMoves(tb)) {
            System.out.println(move.move() + "  WDL" + move.ourWdl() + "  DTZ" + move.distance());
        }
    }
}
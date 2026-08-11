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

        // WDL 데이터는 -2 ~ 2 의 범위를 가지고, 각각
        // -2 는 양쪽 모두 최선의 플레이를 했을 때, 두는 쪽 기준 진다는 것이고,
        // -1 은 지지만, 최선의 플레이를 했을 때, 50수 규칙으로 무승부가 되고,
        // 0 은 무승부,
        // 1은 두는 쪽 기준 이기지만 최선의 플레이를 했을 때, 50수 규칙으로 무승부가 되고,
        // 2는 두는 쪽 기준 이긴다는 것입니다.

        // DTZ 는 폰이나 기물을 잡기까지 얼마나 수가 남았는지를 알려줍니다.

        System.out.println("WDL : " + game.probeSyzygyWdl(tb));
        System.out.println("DTZ : " + game.probeSyzygyDtz(tb));
        System.out.println();

        // 지금 상황에서 최선수도 보일 수 있습니다.
        System.out.println("Syzygy best move : " + game.findBestMoveSyzygy(tb));
        System.out.println();

        // 가능한 수들의 WDL DTZ 결과를 전부 보여주는 메서드도 있습니다.
        for(SyzygyMoveDTO move : game.findRankedSyzygyMoves(tb)) {
            System.out.println(move.move() + "  WDL" + move.ourWdl() + "  DTZ" + move.distance());
        }
    }
}
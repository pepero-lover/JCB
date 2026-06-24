package com.pepero.jcb.api;

import com.pepero.jcb.api.arena.EngineArena;
import com.pepero.jcb.api.arena.MatchConfig;
import com.pepero.jcb.api.dto.MatchResult;
import com.pepero.jcb.api.uci.UCIEngineWrapper;

public class EngineTest {
    public static void main(String[] args) {
        // 외부 UCI 엔진 파일의 경로를 입력하여 Wrapper 를 생성합니다.
        UCIEngineWrapper engine1 = new UCIEngineWrapper("engine1/path/engine.exe", 100, null);
        UCIEngineWrapper engine2 = new UCIEngineWrapper("engine2/path/engine.exe", 100, null);

        // 대전 환경을 구성합니다. (단, 시간 제한과 depth 제한은 동시에 설정 할 수 없습니다.)
        MatchConfig config = new MatchConfig.Builder()
                .openingBook("opening/path/opening.bin") // 오프닝 북도 가져올 수 있습니다.
                .depthLimit(10) // 고정 깊이 탐색
                //.timeControl(300_000, 2_000) // 만약 시간 제한을 사용하고 싶다면 이렇게 하시면 됩니다.
                // 300_000 밀리 세컨드 = 300초 = 5분
                // 2_000 밀리 세컨드 = 2초 = 2초 증가분
                .build();

        // 10 경기를 진행합니다.
        for(int i = 0; i < 10; i++) {
            ChessGame chessGame = new ChessGame();

            EngineArena arena = new EngineArena(
                    chessGame, // 체스 게임
                    engine1, // 엔진 1 Wrapper
                    engine2, // 엔진 2 Wrapper
                    config // 대전 환경
            );

            System.out.println("엔진 매치를 시작합니다.");

            try {
                // 여기까지는 이전과 같지만 여기에서 MatchResult 로 DTO 를 받습니다

                // 대전을 시작합니다.
                MatchResult matchResult = arena.startMatch(); // 대전이 끝나면 내부적으로 최종 결과 및 PGN 기보를 DTO 로 저장합니다.
                System.out.println("경기 결과: " + matchResult.result());
                System.out.println("기보(PGN):\n" + matchResult.pgn());
            } catch (Exception e) {
                // 만약 예외가 발생할 경우
                engine1.close();
                engine2.close();
                throw new RuntimeException(e);
            }
        }
    }
}
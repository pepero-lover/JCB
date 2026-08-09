package com.pepero.jcb.example;

import com.pepero.jcb.api.arena.*;
import com.pepero.jcb.api.dto.MatchResult;

import java.io.File;
import java.util.List;
import java.util.Map;

public class EngineExample {
    public static void main(String[] args) {
        // 엔진들의 실행 경로를 지정합니다.
        String engine1Path = new File("engine/stockfish").getAbsolutePath();
        String engine2Path = new File("engine/stockfish").getAbsolutePath();

        // 엔진의 작업 폴더를 지정합니다.
        String folder = new File("engine/").getAbsolutePath();

        try {
            // 엔진 1의 설정을 생성합니다.
            EngineConfig engine1Config = new EngineConfig(
                    "Stockfish 18", // 엔진 표시용 이름
                    engine1Path, // 엔진 실행 경로
                    folder, // 작업 폴더
                    List.of(), // 엔진의 args 설정
                    EngineConfig.Protocol.UCI, // 프로토콜 종류
                    Map.of(), // 옵션 설정
                    new EngineLimit(10) // 엔진의 제한사항 (타임 컨트롤 및 depth 설정)
                    // 지금에서는 10 depth 만 하지만 만약 시간 제한을 두고 하고 싶다면
                    // new EngineLimit(10_000, 300) 으로 한다면 10000 밀리초 에 피셔 300 밀리초로 10+0.3 초 로 설정 할 수 있습니다.
            );

            // 엔진 2의 설정을 생성합니다.
            EngineConfig engine2Config = new EngineConfig(
                    "Stockfish 18",
                    engine2Path,
                    folder,
                    List.of(),
                    EngineConfig.Protocol.UCI,
                    Map.of(),
                    new EngineLimit(10)
            );

            // 메치의 설정을 생성합니다.
            MatchConfig config = new MatchConfig.Builder()
                    .openingBook("engine/opening.bin") // 오프닝 북을 설정할 수 있습니다.
                    .randomBookMove(false) // 오프닝을 고를 때 랜덤성을 제거합니다. 매 판 오프닝이 같은 것이 아닌 라운드 수 기준으로
                    .repeatOpening(true) // 오프닝을 백흑 바꿔서 똑같이 둡니다.
                    .totalGames(10) // 총 진행할 게임 수
                    .concurrency(1) // 사용할 스레드 수 (지금은 1개만 설정했습니다.)
                    .engine1Config(engine1Config) // 엔진 1의 설정을 가져옵니다.
                    .engine2Config(engine2Config) // 엔진 2의 설정을 가져옵니다.
                    .build();

            // 메치 진행 클래스를 생성합니다.
            ArenaRunner arena = new ArenaRunner(config);

            // 아레나 메치를 시작합니다.
            // 리스너로 게임이 끝났을 때 PGN 을 내보내도록 해보겠습니다.
            MatchStatistics statistics = arena.run(new ArenaRunner.RunnerListener() {
                @Override
                public void onGameFinished(int roundNumber, MatchResult result, MatchStatistics runningStats) {
                    System.out.println("Round " + roundNumber);
                    System.out.println("RESULT : " + result.result() + "(" + result.engineWinner() + ")");
                    System.out.println("PGN : ");
                    System.out.println(result.pgn());
                    System.out.println();
                }
            });

            System.out.println("Engine 1 WDL");
            System.out.println("WIN  :  " + statistics.getEngine1Wins());
            System.out.println("DRAW :  " + statistics.getDraws());
            System.out.println("LOSE :  " + statistics.getEngine2Wins());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
package com.pepero.jcb.api.tactic;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.analyze.TacticFinding;
import com.pepero.jcb.api.arena.EngineArena;
import com.pepero.jcb.api.arena.MatchConfig;
import com.pepero.jcb.api.arena.MatchFinishedEvent;
import com.pepero.jcb.api.arena.MoveEvent;
import com.pepero.jcb.api.dto.MatchResult;
import com.pepero.jcb.api.dto.MoveDataDTO;
import com.pepero.jcb.api.uci.UCIEngineWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class EngineTacticTest {
    public static void main(String[] args) {
        String enginePath = new File("engine/stockfish-18.exe").getAbsolutePath();

        List<String> pgnDatas = new ArrayList<>();

        // 프로세스 빌더를 이용하여 Wrapper 를 생성합니다.
        try (
                UCIEngineWrapper engine1 = new UCIEngineWrapper(
                        new ProcessBuilder(enginePath),
                        100,
                        null
                );
                UCIEngineWrapper engine2 = new UCIEngineWrapper(
                        new ProcessBuilder(enginePath),
                        100,
                        null
                )
        ) {
            // 대전 환경을 구성합니다. (단, 시간 제한과 depth 제한은 동시에 설정 할 수 없습니다.)
            MatchConfig config = new MatchConfig.Builder()
                    .openingBook("engine/opening.bin") // 오프닝 북도 가져올 수 있습니다.
                    .randomBookMove(false) // 오프닝 북의 랜덤성을 제거합니다.
                    .timeControl(10_000, 100) // 만약 시간 제한을 사용하고 싶다면 이렇게 하시면 됩니다.
                    // 300_000 밀리 세컨드 = 300초 = 5분
                    // 2_000 밀리 세컨드 = 2초 = 2초 증가분
                    .build();

            for (int i = 0; i < 3; i++) {
                ChessGame chessGame = ChessGame.startPosition();

                EngineArena arena = new EngineArena(
                        chessGame, // 체스 게임
                        engine1, // 엔진 1 Wrapper
                        engine2, // 엔진 2 Wrapper
                        config // 대전 환경
                );

                arena.setArenaListener(new EngineArena.ArenaListener() {
                    @Override
                    public void onMovePlayed(MoveEvent event) {
                        ChessGame.fromFEN(event.fen()).printBoard();
                        System.out.println(event.moveSan());
                    }

                    @Override
                    public void onMatchFinished(MatchFinishedEvent event) {

                    }
                });

                System.out.println("엔진 매치를 시작합니다.");

                // 대전을 시작합니다.
                // 대전이 끝나면 내부적으로 최종 결과 및 PGN 기보를 DTO 로 저장합니다.
                MatchResult matchResult = arena.startMatch(); // 만약 라운드 수를 정하고 싶다면 arena.startMatch(roundNum)

                System.out.println("경기 결과: " + matchResult.result());
                System.out.println("기보(PGN):\n" + matchResult.pgn());

                pgnDatas.add(matchResult.pgn());

                arena.swapEngine(); // 백흑 바꿔서 진행
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        for(String pgn : pgnDatas) {
            ChessGame chessGame = ChessGame.fromPGN(pgn);
            System.out.println(chessGame.getMainlineData());
            for(MoveDataDTO moveDataDTO : chessGame.getMainlineData()) {
                chessGame.makeMove(moveDataDTO.moveData());
                List<TacticFinding> tactics = chessGame.findImmediateTactics(chessGame.getTurn());
                if(!tactics.isEmpty()) {
                    chessGame.printBoard();
                    System.out.println(tactics);
                    System.out.println();
                }
            }
        }

        // 참고로 예외가 발생하거나 정상종료 됬을 때 try-with-resources 가 engine1/engine2를 자동으로 close() 해줍니다.
    }
}
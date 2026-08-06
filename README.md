## 참고 및 출처
* 이 체스 엔진의 수 생성 메서드 또는 비트보드 로직 ('com.pepero.jcb.api' 이외의 거의 모든 것들) 는 **Code Monkey King** 님이 만드신 튜토리얼에서 깊은 영감을 받았습니다.

## JCB 에 대해
* 원본의 C 코드에서 Java 로 객체 지향적으로 만들고, 내부 무브 제너레이팅 로직에서는 절차 지향의 C 코드를 가져와 효율을 높이었습니다.
* 동시에 API 코드 안에서는 Enum 으로 기물 종류, 체스 보드 칸등의 클래스를 사용하였고, 예외 처리를 강화하여 API 를 더 쉽게 사용 할 수 있도록 만들었습니다.
* 빌드된 jar 라이브러리 파일의 크기가 **200KB** 로 체스 모든 규칙과 프레임 워크를 구현하였습니다.
* 코어 비트보드 탐색 성능은 **70 MNPS (초당 7,000만 노드)** 입니다. (cpu i7-14700KF 기준)
* Syzygy 테이블 베이스 디코더가 포함되어 있습니다.
* 이 라이브러리에서는 단 하나의 외부 라이브러리를 쓰지 않습니다. (단, 테스트용 JUnit 제외)

## 설치 방법

### 1. jar 파일 다운로드
[JCB 릴리스](https://github.com/pepero-lover/JCB/releases) 에서 최신 릴리즈의 
`JCB-*.jar` 를 다운로드 받습니다.

### 2. 프로젝트에 추가하기
### Gradle 프로젝트의 경우

1. `build.gradle` 파일에 JitPack 저장소를 추가합니다.
2. dependencies 에 라이브러리를 추가합니다.
```groovy
repositories {
    maven { url 'https://jitpack.io' }
}
dependencies {
    implementation 'com.github.pepero-lover:JCB:1.3.1'
}
```

### Maven 프로젝트의 경우

1. `pom.xml`에 JitPack 저장소를 추가합니다.
2. 그리고 dependency 를 추가합니다.

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.pepero-lover</groupId>
    <artifactId>JCB</artifactId>
    <version>1.3.1</version>
</dependency>
```
```

## 사용 예시

### 1. 가장 기본적인 게임 플레이
체스 게임을 생성하고, 콘솔이나 입력값을 받아 차례대로 수를 두는 가장 표준적인 방법입니다. (LAN 포멧 사용)

```java
import com.pepero.jcb.api.ChessGame;

public class MainExample {
    public static void main(String[] args) {
        // 기본 시작 포지션으로 초기화 합니다.
        ChessGame chessGame = new ChessGame();

        // 수 두기
        chessGame.makeMove("e2e4");
        chessGame.makeMove("e7e5");
        chessGame.makeMove("g1f3");

        // 현재 턴 및 FEN 데이터 확인
        System.out.println("현재 차례: " + chessGame.getTurn());
        System.out.println("현재 FEN: " + chessGame.getFEN());

        // 무르기 및 다시두기 테스트
        if (chessGame.canUndo()) {
            System.out.println("무르기 전 포지션 : ");
            chessGame.toAscii();
            System.out.println();

            chessGame.unmakeMove(); // g1f3 무르기

            System.out.println("무른 후 포지션 : ");
            chessGame.toAscii();
        }
    }
}
```

### 2. 체스 GUI 개발자를 위한 힌트 기능

사용자가 체스판의 기물을 마우스로 클릭했을 때, 갈 수 있는 기물의 칸 표시와 점수 계산을 구현하는 예시입니다.

```java
import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.enums.Square;
import java.util.List;

public class GUIExample {
    public static void main(String[] args) {
        ChessGame chessGame = new ChessGame();

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
```

### 3. 게임 종료 조건 및 상태 체크

```java
import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.enums.GameOverReason;

public class GameStateExample {
    public static void main(String[] args) {
        // FEN 스트링으로 게임을 시작할 수 있습니다.
        // 예: 체크메이트 직전 상태의 FEN
        String scholarMateFen = "r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4";
        ChessGame game = new ChessGame(scholarMateFen);

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
```

### 4. 엔진끼리 대결하기

```java
package com.pepero.jcb.api;

import com.pepero.jcb.api.arena.EngineArena;
import com.pepero.jcb.api.arena.MatchConfig;
import com.pepero.jcb.api.dto.MatchResult;
import com.pepero.jcb.api.uci.UCIEngineWrapper;

public class EngineTest {
    public static void main(String[] args) {
        // 프로세스 빌더를 이용하여 Wrapper 를 생성합니다.
        try (
                UCIEngineWrapper engine1 = new UCIEngineWrapper(
                        new ProcessBuilder(
                                "java",
                                "-Xmx1024m",
                                "-jar",
                                "my_engine.jar"
                        ),
                        100, // 분석용 틱 레이트 (여기에서는 그렇게 중요하지 않습니다)
                        null // 리스너
                );
                UCIEngineWrapper engine2 = new UCIEngineWrapper(
                        new ProcessBuilder(
                                "java",
                                "-Xmx1024m",
                                "-jar",
                                "my_engine.jar"
                        ),
                        100, // 분석용 틱 레이트 (여기에서는 그렇게 중요하지 않습니다)
                        null // 리스너
                )
        ) {
            // 대전 환경을 구성합니다. (단, 시간 제한과 depth 제한은 동시에 설정 할 수 없습니다.)
            MatchConfig config = new MatchConfig.Builder()
                    .openingBook("opening/path/opening.bin") // 오프닝 북도 가져올 수 있습니다.
                    .randomBookMove(false) // 오프닝 북의 랜덤성을 제거합니다.
                    .depthLimit(10) // 고정 깊이 탐색
                    //.timeControl(300_000, 2_000) // 만약 시간 제한을 사용하고 싶다면 이렇게 하시면 됩니다.
                    // 300_000 밀리 세컨드 = 300초 = 5분
                    // 2_000 밀리 세컨드 = 2초 = 2초 증가분
                    .build();

            // 10 경기를 진행합니다.
            for (int i = 0; i < 10; i++) {
                ChessGame chessGame = new ChessGame();

                EngineArena arena = new EngineArena(
                        chessGame, // 체스 게임
                        engine1, // 엔진 1 Wrapper
                        engine2, // 엔진 2 Wrapper
                        config // 대전 환경
                );

                System.out.println("엔진 매치를 시작합니다.");

                // 대전을 시작합니다.
                // 대전이 끝나면 내부적으로 최종 결과 및 PGN 기보를 DTO 로 저장합니다.
                MatchResult matchResult = arena.startMatch(); // 만약 라운드 수를 정하고 싶다면 arena.startMatch(roundNum)

                System.out.println("경기 결과: " + matchResult.result());
                System.out.println("기보(PGN):\n" + matchResult.pgn());

                arena.swapEngine(); // 백흑 바꿔서 진행
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 참고로 예외가 발생하거나 정상종료 됬을 때 try-with-resources 가 engine1/engine2를 자동으로 close() 해줍니다.
    }
}
```
### 5. Syzygy 테이블베이스 사용하기
```java
import com.pepero.jcb.api.ChessGame;
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
        ChessGame game = new ChessGame("8/8/4r3/3k4/8/8/2Q5/7K w - - 0 1");

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
        // 물론 최선수 기준으로 정렬된 결과로요.
        for(SyzygyMoveDTO move : game.findRankedSyzygyMoves(tb)) {
            System.out.println(move.move() + "  WDL" + move.ourWdl() + "  DTZ" + move.distance());
        }
    }
}
```

## 라이선스
이 프로젝트는 MIT License 에 따라 라이선스가 부여됩니다.
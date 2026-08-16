# JCB (Java Chess Board)
![Java](https://img.shields.io/badge/Java-21%2B-blue)
![License](https://img.shields.io/badge/License-MIT-green)
![Size](https://img.shields.io/badge/Size-240KB-orange)

## 요구 사항
- Java 21 이상
## 지원 기능

- 체스 변형 지원 (Standard / Chess960 / CrazyHouse / Three-check / King of the Hill)
- Syzygy tablebase 프로빙 (WDL / DTZ)
- PGN 파싱 및 export, variation tree
- 엔진 대전 (EngineArena)
- Perft (싱글/멀티스레드)
- 외부 라이브러리 의존성 없음

## JCB 에 대해
* 원본의 C 코드에서 Java 로 객체 지향적으로 만들고, 내부 무브 제너레이팅 로직에서는 절차 지향의 C 코드를 가져와 효율을 높였습니다.
* 동시에 API 코드 안에서는 Enum 으로 기물 종류, 체스 보드 칸등의 클래스를 사용하였고, 예외 처리를 강화하여 API 를 더 쉽게 사용 할 수 있도록 만들었습니다.
* 빌드된 jar 라이브러리 파일의 크기가 **240KB** 로 체스 모든 규칙과 프레임 워크를 구현하였습니다.
* 코어 비트보드 탐색 성능은 **70 MNPS (초당 7,000만 노드)** 입니다. (cpu i7-14700KF 기준)
* Syzygy 테이블 베이스 디코더가 포함되어 있습니다.
* 이 프로젝트는 외부 라이브러리 의존성이 전혀 없습니다. (단, 테스트용 JUnit 제외)

## 지원하는 변형 체스들
| 변형               | FEN | UCI 연동             |
|------------------|-----|--------------------|
| Standard         | ✅   | ✅ (기본 UCI 설정)      |
| Chess960         | ✅   | ✅ (`UCI_Chess960`) |
| Crazyhouse       | ✅   | ✅ (`UCI_Variant`)  |
| Three-check      | ✅   | ✅ (`UCI_Variant`)  |
| King of the Hill | ✅   | ✅ (`UCI_Variant`)  |

## 참고 및 출처
* 이 체스 엔진의 수 생성 메서드 또는 비트보드 로직 ('com.pepero.jcb.api' 이외의 거의 모든 것들) 는 **Code Monkey King** 님이 만드신 튜토리얼에서 깊은 영감을 받았습니다.

## 설치 방법

### Gradle 프로젝트의 경우

1. settings.gradle 에 이 코드를 삽입하세요.
```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

2. 의존성을 build.gradle 에 추가하세요.
```groovy
dependencies {
    implementation 'com.github.pepero-lover:JCB:v1.5.0'
}
```

### Maven 프로젝트의 경우
1. pom.xml 에 이 코드를 삽입하세요.
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```
2. 의존성을 추가하세요.
```xml
<dependency>
    <groupId>com.github.pepero-lover</groupId>
    <artifactId>JCB</artifactId>
    <version>v1.5.0</version>
</dependency>
```

## 사용 예시

> ⚠️ 아래 내용은 최신 릴리즈가 아닌 현재 `master` 브랜치(개발 버전) 기준으로 작성되었습니다. 일부 예시의 메서드가 바뀌었을 수 있습니다.

### 1. 가장 기본적인 게임 플레이
체스 게임을 생성하고, 콘솔이나 입력값을 받아 차례대로 수를 두는 가장 표준적인 방법입니다. (LAN 포멧 사용)

```java
import com.pepero.jcb.api.ChessGame;

public class MainExample {
    public static void main(String[] args) {
        // 기본 시작 포지션으로 초기화 합니다.
        ChessGame chessGame = ChessGame.startPosition();

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
```

### 4. 엔진끼리 대결하기

```java
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

            // 매치의 설정을 생성합니다.
            MatchConfig config = new MatchConfig.Builder()
                    .openingBook("engine/opening.bin") // 오프닝 북을 설정할 수 있습니다.
                    .repeatOpening(true) // 오프닝을 백흑 바꿔서 똑같이 둡니다.
                    .totalGames(10) // 총 진행할 게임 수
                    .concurrency(1) // 사용할 스레드 수 (지금은 1개만 설정했습니다.)
                    .engine1Config(engine1Config) // 엔진 1의 설정을 가져옵니다.
                    .engine2Config(engine2Config) // 엔진 2의 설정을 가져옵니다.
                    .build();

            // 매치 진행 클래스를 생성합니다.
            ArenaRunner arena = new ArenaRunner(config);

            // 아레나 매치를 시작합니다.
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
```
### 5. Syzygy 테이블베이스 사용하기
```java
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
```
### 6. Perft 사용하기
```java
import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.perft.PerftDriver;
import com.pepero.jcb.core.Chessboard;

public class PerftExample {
    public static void main(String[] args) {
        String fen = ChessGame.START_POSITION;

        ChessGame chessGame = ChessGame.fromFEN(fen);

        System.out.println("--------------------");
        System.out.println("Perft API 1 threads");
        System.out.println("--------------------");

        // Perft 를 실행하고 결과를 저장합니다.

        // Javadoc 설명에도 나와 있듯이, perft(int depth) 는 싱글 스레드, JVM warmup 을 적용한 결과입니다.
        chessGame.perft(5);

        // 이번에는 스레드를 4개로 했을 때의 결과를 출력해보겠습니다.
        System.out.println();
        System.out.println("--------------------");
        System.out.println("Perft API 4 threads");
        System.out.println("--------------------");

        chessGame.perft(
                6, // perft 깊이
                4 // 사용할 스레드 수
        );

        // 이제는 Chessboard 기준으로 Perft 를 진행해보겠습니다.
        Chessboard chessboard = new Chessboard(fen);

        System.out.println();
        System.out.println("-------------------------");
        System.out.println("Perft Bitboard 1 threads");
        System.out.println("-------------------------");

        PerftDriver.perftBitboardTest(
                chessboard,
                6, // perft 깊이
                1, // 사용할 스레드 수
                false, // 테스트 결과 및 출력을 하지 않을 것인지
                false // 벌크 카운팅을 할 것인지
        );

        System.out.println();
        System.out.println("-------------------------");
        System.out.println("Perft Bitboard 4 threads");
        System.out.println("-------------------------");

        PerftDriver.perftBitboardTest(
                chessboard,
                7, // perft 깊이
                4, // 사용할 스레드 수
                false, // 테스트 결과 및 출력을 하지 않을 것인지
                false // 벌크 카운팅을 할 것인지
        );
    }
}
```

## 성능
JCB 에는 2가지 단계의 API가 있습니다.

- `ChessGame` - 고수준, 객체지향적으로 만들어져 사용하기 편합니다.
- `Chessboard` - 저수준 비트보드로 만들어져 성능이 중요한 프로젝트 (엔진 개발 등) 에 사용됩니다.

*아래 벤치마크 결과는 i7-14700KF CPU, 싱글스레드 기준,
JIT warmup 을 하고 3회 평균을 낸 결과입니다. (벌크 카운팅 없음)*

*(참고: 얕은 depth 쪽이 캐시 친화적이라 오히려 더 높은 NPS가 나올 수 있습니다.)*

#### Perft `ChessGame` 기준

| 스레드   | NPS (5 ply) | NPS (6 ply) |
|-------|-------------|-------------|
| 1 스레드 | 7.22MNPS    | 7.48MNPS    |
| 2 스레드 | 14.56MNPS   | 14.33MNPS   |
| 4 스레드 | 28.32MNPS   | 27.33MNPS   |
| 8 스레드 | 48.14MNPS   | 46.81MNPS   |

#### Perft `Chessboard` 기준

| 스레드   | NPS (6 ply) | NPS (7 ply) |
|-------|-------------|-------------|
| 1 스레드 | 59.11MNPS   | 61.57MNPS   |
| 2 스레드 | 122.62MNPS  | 120.81MNPS  |
| 4 스레드 | 236.69MNPS  | 233.51MNPS  |
| 8 스레드 | 409.37MNPS  | 419.33MNPS  |

> 엔진 개발처럼 성능이 중요한 프로젝트라면 `Chessboard`를 직접 사용하는 걸 권장합니다.

> `ChessGame`은 툴링, 스크립팅, 분석용으로 적합하며, 저수준 API 대비 약 8배 정도의 오버헤드가 있습니다.

<details>
   <summary>벤치마크 재현 코드 보기</summary>

```java
import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.perft.PerftDriver;
import com.pepero.jcb.api.perft.PerftResult;
import com.pepero.jcb.core.Chessboard;

import java.util.ArrayList;
import java.util.List;

public class PerftResultTest {
    public static void main(String[] args) {
        Chessboard chessboard = new Chessboard(Chessboard.start_position);
        ChessGame chessGame = ChessGame.startPosition();

        int available_processor = Runtime.getRuntime().availableProcessors();

        System.out.println("Available Processors : " + available_processor);

        int averageCount = 3;

        System.out.println();
        System.out.println();
        System.out.println("---- BITBOARD TEST ---- ");
        System.out.println();
        System.out.println();


        for(int thread : new int[]{1,2,4,8}) {
            for (int depth : new int[]{6,7}) {
                if(available_processor < thread) break;

                // nps 의 평균을 구합니다.
                List<Long> nps = new ArrayList<>();
                for(int i=1;i<=averageCount;i++) {
                    PerftResult result =
                            PerftDriver.perftBitboardTest(chessboard, depth, thread, true, false);
                    nps.add(result.nps());
                    System.out.println("Calculated perft(" + depth + ") with " + thread + " thread(s) (" + i + "/" + averageCount + ")");
                }
                double npsAverage = nps.stream().mapToLong(Long::longValue)
                        .average()
                        .orElse(0.0);


                System.out.println();
                System.out.println();
                System.out.println("Calculated perft(" + depth + ") * " + averageCount
                        + " with " + thread + " thread(s)");
                System.out.println("Average NPS : " + String.format(
                        "%.2f", npsAverage / 1_000_000.
                ) + "MNPS ( " +
                        (long) npsAverage + "nps )");
                System.out.println();
            }
        }
        System.out.println();
        System.out.println();
        System.out.println("---- API TEST ---- ");
        System.out.println();
        System.out.println();

        for(int thread : new int[]{1,2,4,8}) {
            for (int depth : new int[]{5,6}) {
                if(available_processor < thread) break;

                List<Long> nps = new ArrayList<>();
                for(int i=1;i<=averageCount;i++) {
                    PerftResult result =
                            PerftDriver.perftAPITest(chessGame, depth, thread, true, false);
                    nps.add(result.nps());
                    System.out.println("Calculated perft(" + depth + ") with " + thread + " thread(s) (" + i + "/" + averageCount + ")");
                }
                double npsAverage = nps.stream().mapToLong(Long::longValue)
                        .average()
                        .orElse(0.0);


                System.out.println();
                System.out.println();
                System.out.println("Calculated perft(" + depth + ") * " + averageCount
                        + " with " + thread + " thread(s)");
                System.out.println("Average NPS : " + String.format(
                        "%.2f", npsAverage / 1_000_000.
                ) + "MNPS ( " +
                        (long) npsAverage + "nps )");
                System.out.println();
            }
        }
    }
}
```
</details>

## 라이선스
이 프로젝트는 MIT License 에 따라 라이선스가 부여됩니다.
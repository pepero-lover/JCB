# JCB (Java Chess Board)
![Java](https://img.shields.io/badge/Java-21%2B-blue)
![License](https://img.shields.io/badge/License-MIT-green)
![Size](https://img.shields.io/badge/Size-388KB-orange)
[![](https://jitpack.io/v/pepero-lover/JCB.svg)](https://jitpack.io/#pepero-lover/JCB)

한국어 | [English](README.md)

## 요구 사항
- Java 21 이상

## JCB 에 대해
* Java로 구현한 비트보드 기반 체스 라이브러리로, 성능을 위한 저수준 API와 사용 편의성을 위한 객체 지향 API를 제공합니다.
* 동시에 API 코드 안에서는 Enum 으로 기물 종류, 체스 보드 칸등의 클래스를 사용하였고, 예외 처리를 강화하여 API 를 더 쉽게 사용 할 수 있도록 만들었습니다.
* 빌드된 jar 라이브러리 파일 크기가 388KB에 불과하지만, 체스의 모든 규칙과 엔진 프레임워크를 완벽히 구현했습니다.
* 코어 비트보드 탐색 성능은 **60 MNPS (초당 6,000만 노드)** 입니다. (cpu i7-14700KF 기준)
* Syzygy / Gaviota 테이블베이스 디코더가 포함되어 있습니다.
* 이 프로젝트는 외부 라이브러리 의존성이 전혀 없습니다. (단, 테스트용 JUnit 제외)

## 지원 기능

- 체스 변형 지원 (Standard / Chess960 / CrazyHouse /
  Three-check / King of the Hill / Horde / Atomic / Giveaway / Suicide /
  Racing Kings)
- Syzygy tablebase 프로빙 (WDL / DTZ) 그리고 Standard, Atomic, Giveaway, Suicide 변형 지원
- Gaviota tablebase 프로빙 (WDL / DTM)
- PGN 파싱, export, variation tree, 수 GB급 대용량 PGN 데이터베이스 스트리밍 지원 (`PGNDatabaseIterator`)
- 엔진 대전 (`MatchRunner`)
- 오프닝북: PGN 게임들을 Polyglot(`.bin`) 북으로 빌드, Polyglot / EPD(.epd) / PGN(.pgn) 오프닝북 읽기 지원
- Perft (싱글/멀티스레드)
- 외부 라이브러리 의존성 없음

## 지원하는 변형 체스들
| 변형               | FEN | UCI 연동             |
|------------------|-----|--------------------|
| Standard         | ✅   | ✅ (기본 UCI 설정)      |
| Chess960         | ✅   | ✅ (`UCI_Chess960`) |
| Crazyhouse       | ✅   | ✅ (`UCI_Variant`)  |
| Three-check      | ✅   | ✅ (`UCI_Variant`)  |
| King of the Hill | ✅   | ✅ (`UCI_Variant`)  |
| Horde            | ✅   | ✅ (`UCI_Variant`)  |
| Atomic           | ✅   | ✅ (`UCI_Variant`)  |
| Giveaway         | ✅   | ✅ (`UCI_Variant`)  |
| Suicide          | ✅   | ✅ (`UCI_Variant`)  |
| Racing Kings     | ✅   | ✅ (`UCI_Variant`)  |

## 참고 및 출처
* 이 라이브러리는 초기 개발 당시에 [BBC](https://github.com/maksimKorzh/bbc) (by [Code Monkey King](https://github.com/maksimKorzh))
  을 자바로 포팅하는 과정에서 시작하였습니다. 이후 수 생성, 보드 관리 등 기능을 지속적으로 수정 및 확장하여 지금의 체스 라이브러리로 발전했습니다.
  초기 비트보드 구현의 기반과 학습 자료를 제공한 [**Code Monkey King**](https://github.com/maksimKorzh) 에게 감사드립니다.
* Syzygy 테이블베이스 프로빙(`com.pepero.jcb.api.syzygy`)은 Ronald de Man과 Jon Dart의 **Fathom**(MIT 라이선스, https://github.com/jdart1/Fathom ) 에서 파생되었습니다.
* Gaviota 테이블베이스 프로빙(`com.pepero.jcb.api.gaviota`)은 Miguel A. Ballicora의 **Gaviota Tablebases** 프로빙 코드(X11/MIT 라이선스, https://github.com/michiguel/Gaviota-Tablebases ) 에서 파생되었습니다.

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
  implementation 'com.github.pepero-lover:JCB:v1.16.0'
}
```

### gradle.kts 의 경우

1. settings.gradle.kts 에 이 코드를 삽입하세요.
```kotlin
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
  }
}
```

2. 의존성을 build.gradle.kts 에 추가하세요.
```kotlin
dependencies {
  implementation("com.github.pepero-lover:JCB:v1.16.0")
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
  <version>v1.16.0</version>
</dependency>
```

## 사용 예시

> 간단한 UCI 엔진 뼈대 예제(`SimpleEngine`)를 포함한 모든 실행 가능한 예제 코드는 저장소의 [`src/test/java/com/pepero/jcb/example`](https://github.com/pepero-lover/JCB/tree/main/src/test/java/com/pepero/jcb/example) 경로에서 확인하실 수 있습니다.

### 1. 가장 기본적인 게임 플레이
_전체 소스: [`MainExample`](https://github.com/pepero-lover/JCB/blob/main/src/test/java/com/pepero/jcb/example/MainExample.java)_

체스 게임을 생성하고, 콘솔이나 입력값을 받아 차례대로 수를 두는 가장 표준적인 방법입니다. (LAN 포멧 사용)

```java
import com.pepero.jcb.api.ChessGame;

public class MainExample {
    public static void main(String[] args) {
        // 기본 시작 포지션으로 초기화 합니다.
        ChessGame chessGame = ChessGame.startPosition();

        // 수 두기
        chessGame.makeMoveLan("e2e4");
        chessGame.makeMoveLan("e7e5");
        chessGame.makeMoveLan("g1f3");

        // 현재 턴 및 FEN 데이터 확인
        System.out.println("현재 차례: " + chessGame.getTurn());
        System.out.println("현재 FEN: " + chessGame.getFEN());

        // 무르기 및 다시두기 테스트
        if (chessGame.canUndo()) {
            System.out.println("무르기 전 포지션 : ");
            chessGame.printBoard();
            System.out.println();

            chessGame.unmakeMove(); // g1f3 무르기

            System.out.println("무른 후 포지션 : ");
            chessGame.printBoard();
        }
    }
}
```

### 더 많은 예제

전체 소스는 [`example`](https://github.com/pepero-lover/JCB/tree/main/src/test/java/com/pepero/jcb/example) 폴더에서 확인하실 수 있습니다.

| 예제                                                                                                                              | 내용                                             | 주요 클래스                                       |
|---------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------|----------------------------------------------|
| [체스 GUI 힌트 기능](https://github.com/pepero-lover/JCB/blob/main/src/test/java/com/pepero/jcb/example/GUIExample.java)              | 기물을 클릭했을 때 갈 수 있는 칸 표시와 점수 계산                  | `ChessGame`, `MoveInfo`, `Square`            |
| [Swing 체스 GUI](https://github.com/pepero-lover/JCB/blob/main/src/test/java/com/pepero/jcb/example/SwingChess.java) | 기물 이동, 합법 수 표시, 승격 선택, 무르기·다시 두기를 지원하는 보드 | `ChessGame`, `MoveInfo`, `Square` |
| [Swing 테이블베이스 뷰어](https://github.com/pepero-lover/JCB/blob/main/src/test/java/com/pepero/jcb/example/SwingChessTablebase.java) | 기물을 움직이며 후보 수별 WDL / DTZ와 가능한 경우 DTM을 확인하는 보드 | `CombinedAnalyzer`, `SyzygyTablebase`, `GaviotaTablebase` |
| [게임 종료 조건 및 상태 체크](https://github.com/pepero-lover/JCB/blob/main/src/test/java/com/pepero/jcb/example/GameStateExample.java)    | 체크메이트, 스테일메이트, 무승부 등 상태 확인                     | `ChessGame`, `GameOverReason`                |
| [엔진끼리 대결하기](https://github.com/pepero-lover/JCB/blob/main/src/test/java/com/pepero/jcb/example/EngineExample.java)              | 동시 실행, 판정 규칙, PGN 출력을 포함한 UCI 엔진 대전            | `MatchRunner`, `MatchConfig`, `EngineConfig` |
| [Syzygy 테이블베이스](https://github.com/pepero-lover/JCB/blob/main/src/test/java/com/pepero/jcb/example/SyzygyExample.java)          | Syzygy 테이블베이스로 WDL / DTZ 조회와 수 평가              | `SyzygyTablebase`, `SyzygyAnalyzer`          |
| [Gaviota 테이블베이스](https://github.com/pepero-lover/JCB/blob/main/src/test/java/com/pepero/jcb/example/GaviotaExample.java)        | Gaviota 테이블베이스로 WDL / DTM 조회와 수 평가             | `GaviotaTablebase`, `GaviotaAnalyzer`        |
| [Perft](https://github.com/pepero-lover/JCB/blob/main/src/test/java/com/pepero/jcb/example/PerftExample.java)                   | `ChessGame`, `Chessboard` 기준 싱글/멀티스레드 Perft    | `PerftDriver`, `Chessboard`                  |
| [오프닝북](https://github.com/pepero-lover/JCB/blob/main/src/test/java/com/pepero/jcb/example/PGNtoPolyglotConvertExample.java)     | PGN 게임들로 Polyglot `.bin` 북 빌드, 엔진 대전에서 오프닝북 사용 | `PolyglotBookBuilder`, `MatchConfig`         |
| [대용량 PGN 데이터베이스 스트리밍](https://github.com/pepero-lover/JCB/blob/main/src/test/java/com/pepero/jcb/example/PGNStreamExample.java) | 수 GB 단위 PGN 파일을 한 번에 올리지 않고 게임 단위로 순회          | `PGNDatabaseIterator`, `PGNGameStub`         |

`SwingChessTablebase`를 실행하려면 예제 소스의 Syzygy·Gaviota 디렉터리 경로와 시작 FEN을 로컬 테이블베이스 파일에 맞게 설정하세요. `SwingChess`는 외부 파일 없이 실행할 수 있습니다.

`MatchConfig.Builder.openingBook(Path)`는 파일 확장자를 보고 자동으로 오프닝북 종류를 인식합니다.
- `.bin` &rarr; Polyglot 오프닝북, 게임 진행 중 매 수마다 조회
- `.epd` &rarr; EPD 오프닝북, 포지션 목록 중 하나를 골라 게임 시작 포지션으로 사용
- `.pgn` &rarr; PGN 오프닝북, PGN 중 랜덤으로 골라 사용자가 설정한 최대 수 길이만큼 재생 후 시작

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
| 1 스레드 | 6.94MNPS    | 7.26MNPS    |
| 2 스레드 | 14.02MNPS   | 14.05MNPS   |
| 4 스레드 | 26.78MNPS   | 27.25MNPS   |
| 8 스레드 | 45.28MNPS   | 50.72MNPS   |

#### Perft `Chessboard` 기준

| 스레드   | NPS (6 ply) | NPS (7 ply) |
|-------|-------------|-------------|
| 1 스레드 | 57.61MNPS   | 57.99MNPS   |
| 2 스레드 | 112.62MNPS  | 112.27MNPS  |
| 4 스레드 | 220.68MNPS  | 217.74MNPS  |
| 8 스레드 | 385.11MNPS  | 398.76MNPS  |

> 엔진 개발처럼 성능이 중요한 프로젝트라면 `Chessboard`를 직접 사용하는 걸 권장합니다.

> `ChessGame`은 툴링, 스크립팅, 분석용으로 적합하며, 저수준 API 대비 약 8배 정도의 오버헤드가 있습니다.

#### SAN / LAN 무브 변환 (`ConvertStringMoveUtils`)

`ConvertStringMoveUtils`는 PGN 파싱, 오프닝북 빌드, UCI 통신 과정에서 매우 빈번하게 호출되는 클래스라 변환 처리량도 벤치마크했습니다 (싱글스레드, JIT warmup 이후, 랜덤 플레이로 생성한 게임들의 무브 시퀀스 기준).

| 변환                 | 처리량                    |
|----------------------|---------------------------|
| SAN &rarr; move data | 4,694,884 conversions/sec |
| LAN &rarr; move data | 4,710,466 conversions/sec |

재현 코드:
[`PerftResultTest`](https://github.com/pepero-lover/JCB/blob/main/src/test/java/com/pepero/jcb/perft/PerftResultTest.java) ·
[`ConvertStringMoveUtilsBenchmark`](https://github.com/pepero-lover/JCB/blob/main/src/test/java/com/pepero/jcb/api/convert/ConvertStringMoveUtilsBenchmark.java) ·
[`RandomGameGenerator`](https://github.com/pepero-lover/JCB/blob/main/src/test/java/com/pepero/jcb/api/convert/RandomGameGenerator.java)

## 피드백 & 이슈
예상대로 작동하지 않는 경우, 사용 중인 Java 버전과 운영 체제, 그리고 가능하다면 문제를 재현할 수 있는 최소한의 코드를 포함하여 [GitHub Issues](https://github.com/pepero-lover/JCB/issues)에 이슈를 등록해 주시면 감사하겠습니다.

## 라이선스
이 프로젝트는 MIT License 에 따라 라이선스가 부여됩니다.

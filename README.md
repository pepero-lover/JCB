## 참고 및 출처
* 이 체스 엔진의 수 생성 메서드 또는 비트보드 로직 ('com.pepero.jcb.api' 이외의 거의 모든 것들) 는 **Code Monkey King** 님이 만드신 튜토리얼에서 깊은 영감을 받았습니다.

## JCB 에 대해
* 원본의 C 코드에서 Java 로 객체 지향적으로 만들고, 내부 무브 제너레이팅 로직에서는 절차 지향의 C 코드를 가져와 효율을 높이었습니다.
* 동시에 API 코드 안에서는 Enum 으로 기물 종류, 체스 보드 칸등의 클래스를 사용하였고, 예외 처리를 강화하여 API 를 더 쉽게 사용 할 수 있도록 만들었습니다.

## 설치 방법

### 1. jar 파일 다운로드
[JCB 릴리스](https://github.com/pepero-lover/JCB/releases) 에서 최신 릴리즈의 
`JCB-*.jar` 를 다운로드 받습니다.

### 2. 프로젝트에 추가하기
### Gradle 프로젝트의 경우

1. 프로젝트 루트 또는 `libs/` 폴더에 `JCB-*.jar` 파일을 넣습니다.
2. `build.gradle` 파일의 `dependencies`에 아래 코드를 추가합니다.
```groovy
dependencies {
    // libs 폴더 안에 JCB.jar 추가
    implementation files('libs/JCB-*.jar')

    // 또는 libs 폴더 안의 모든 jar 파일을 한 번에 포함할 경우
    // implementation fileTree(dir: 'libs', include: ['*.jar'])
}
```

### Maven 프로젝트의 경우

1. 프로젝트 루트에 `lib/` 폴더를 만들고 `JCB-*.jar` 를 넣습니다.
2. `pom.xml` 파일에 아래와 같이 `system` 스코프로 의존성을 추가합니다.
```xml
<dependency>
    <groupId>com.pepero</groupId>
    <artifactId>jcb</artifactId>
    <version>1.0.1</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/libs/JCB-*.jar</systemPath>
</dependency>
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

        // 현제 턴 및 FEN 데이터 확인
        System.out.println("현제 차례: " + chessGame.getTurn());
        System.out.println("현제 FEN: " + chessGame.getFEN());

        // 무르기 및 다시두기 테스트
        if (chessGame.canUndo()) {
            System.out.println("무르기 전 포지션 : ");
            System.out.println(chessGame);
            System.out.println();

            chessGame.unmakeMove(); // g1f3 무르기

            System.out.println("무른 후 포지션 : ");
            System.out.println(chessGame);
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
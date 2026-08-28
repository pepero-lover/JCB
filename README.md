# JCB (Java Chess Board)
![Java](https://img.shields.io/badge/Java-21%2B-blue)
![License](https://img.shields.io/badge/License-MIT-green)
![Size](https://img.shields.io/badge/Size-316KB-orange)
[![](https://jitpack.io/v/pepero-lover/JCB.svg)](https://jitpack.io/#pepero-lover/JCB)

[한국어](README.ko.md) | English

## Requirements
- Java 21 or higher

## About JCB
* This project ports the original C code into Java in an object-oriented style, while keeping the procedural C-style approach for the internal move-generation logic to maximize efficiency.
* At the same time, the API layer uses Enum classes for piece types, chessboard squares, and more, along with strengthened exception handling to make the API easier to use.
* The built jar library is only **316KB** in size, yet implements the complete rules and framework for chess.
* Core bitboard search performance is **60 MNPS (60 million nodes per second)**. (Benchmarked on an i7-14700KF CPU)
* Includes a built-in Syzygy / Gaviota tablebase decoder.
* This project has zero external library dependencies (except JUnit, used only for testing).

## Supported Features

- Chess variant support (Standard / Chess960 / Crazyhouse /
  Three-check / King of the Hill / Horde / Atomic / Giveaway / Suicide /
  Racing Kings)
- Syzygy tablebase probing (WDL / DTZ) and supports Standard, Atomic, Giveaway, Suicide chess variants
- Gaviota tablebase probing (WDL / DTM)
- PGN parsing and export, variation tree
- Engine matches (EngineArena)
- Opening books: build a Polyglot (`.bin`) book from PGN games, and read Polyglot / EPD (`.epd`) opening books
- Perft (single/multi-threaded)
- No external library dependencies

## Supported Chess Variants
| Variant          | FEN | UCI Integration      |
|------------------|-----|-----------------------|
| Standard         | ✅   | ✅ (default UCI setup) |
| Chess960         | ✅   | ✅ (`UCI_Chess960`)    |
| Crazyhouse       | ✅   | ✅ (`UCI_Variant`)     |
| Three-check      | ✅   | ✅ (`UCI_Variant`)     |
| King of the Hill | ✅   | ✅ (`UCI_Variant`)     |
| Horde            | ✅   | ✅ (`UCI_Variant`)     |
| Atomic           | ✅   | ✅ (`UCI_Variant`)     |
| Giveaway         | ✅   | ✅ (`UCI_Variant`)     |
| Suicide          | ✅   | ✅ (`UCI_Variant`)     |
| Racing Kings     | ✅   | ✅ (`UCI_Variant`)     |

## Credits and References
* The move generation methods and bitboard logic in this chess engine (nearly everything outside of `com.pepero.jcb.api`) were deeply inspired by a tutorial created by **Code Monkey King**.

## Installation

### For Gradle projects

1. Add the following to your `settings.gradle`.
```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

2. Add the dependency to your `build.gradle`.
```groovy
dependencies {
    implementation 'com.github.pepero-lover:JCB:v1.7.2'
}
```

### For gradle.kts

1. Add the following to your `settings.gradle.kts`.
```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

2. Add the dependency to your `build.gradle.kts`.
```kotlin
dependencies {
    implementation("com.github.pepero-lover:JCB:v1.7.2")
}
```

### For Maven projects
1. Add the following to your `pom.xml`.
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```
2. Add the dependency.
```xml
<dependency>
    <groupId>com.github.pepero-lover</groupId>
    <artifactId>JCB</artifactId>
    <version>v1.7.2</version>
</dependency>
```

## Usage Examples

> More runnable examples, including a minimal UCI engine skeleton (`SimpleEngine`), are available under [`src/test/java/com/pepero/jcb/example`](https://github.com/pepero-lover/JCB/tree/main/src/test/java/com/pepero/jcb/example) in the repository.

### 1. Basic Gameplay
This is the most standard way to create a chess game and make moves in sequence, whether from the console or from user input (using LAN format).

```java
import com.pepero.jcb.api.ChessGame;

public class MainExample {
    public static void main(String[] args) {
        // Initialize with the default starting position.
        ChessGame chessGame = ChessGame.startPosition();

        // Make moves
        chessGame.makeMove("e2e4");
        chessGame.makeMove("e7e5");
        chessGame.makeMove("g1f3");

        // Check the current turn and FEN
        System.out.println("Current turn: " + chessGame.getTurn());
        System.out.println("Current FEN: " + chessGame.getFEN());

        // Test undo and redo
        if (chessGame.canUndo()) {
            System.out.println("Position before undo: ");
            chessGame.toAscii();
            System.out.println();

            chessGame.unmakeMove(); // undo g1f3

            System.out.println("Position after undo: ");
            chessGame.toAscii();
        }
    }
}
```

### 2. Move Hints for Chess GUI Developers

An example implementation for when a user clicks on a piece on the chessboard, showing which squares the piece can move to along with score calculation.

```java
import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.enums.Square;
import java.util.List;

public class GUIExample {
    public static void main(String[] args) {
        ChessGame chessGame = ChessGame.startPosition();

        // Assume the user clicked on the white pawn on e2.
        Square clickedSquare = Square.e2;

        // Get all legal moves for the piece on e2.
        List<MoveInfo> legalMoves = chessGame.getLegalMovesForSource(clickedSquare);

        System.out.println("List of moves available for the piece on e2 (LAN)");
        for (MoveInfo move : legalMoves) {
            System.out.println("- " + move.toString());
        }

        // Check the current piece score (positive: White is ahead / negative: Black is ahead)
        System.out.println("Current piece score (from White's perspective): " + chessGame.getPieceScore());
    }
}
```

### 3. Game-Over Conditions and State Checks

```java
import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.enums.GameOverReason;

public class GameStateExample {
    public static void main(String[] args) {
        // A game can be started from a FEN string.
        // Example: a position right before checkmate
        String scholarMateFen = "r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4";
        ChessGame game = ChessGame.fromFEN(scholarMateFen);

        // Assume White delivers checkmate.
        game.makeMove("h5f7"); // e4 e5 Qh5 Nc6 Bc4 Nf6 Qxf7#

        // Check whether the game has ended
        GameOverReason reason = game.isGameOver();
        if (reason != GameOverReason.NOTGAMEOVER) {
            System.out.println("Game over! Reason: " + reason);
        }

        // Individual state checks are also available.
        System.out.println("Is it checkmate? : " + game.isCheckmate());
        System.out.println("Is it check? : " + game.isCheck());
    }
}
```

### 4. Engine vs. Engine Matches

```java
import com.pepero.jcb.api.arena.*;
import com.pepero.jcb.api.arena.MatchResult;

import java.io.File;
import java.util.List;
import java.util.Map;

public class EngineExample {
    public static void main(String[] args) {
        // Specify the executable paths for the engines.
        String engine1Path = new File("engine/stockfish").getAbsolutePath();
        String engine2Path = new File("engine/stockfish").getAbsolutePath();

        // Specify the engines' working directory.
        String folder = new File("engine/").getAbsolutePath();

        try {
            // Create the configuration for Engine 1.
            EngineConfig engine1Config = new EngineConfig(
                    "Stockfish 18", // Display name of the engine
                    engine1Path, // Engine executable path
                    folder, // Working directory
                    List.of(), // Engine args
                    EngineConfig.Protocol.UCI, // Protocol type
                    Map.of(), // Option settings
                    new EngineLimit(10) // Engine limits (time control and depth settings)
                    // Here we only use a depth of 10, but if you want a time control instead,
                    // you could use new EngineLimit(10_000, 300) for a 10+0.3 setup
                    // (10000 ms base time, 300 ms Fischer increment).
            );

            // Create the configuration for Engine 2.
            EngineConfig engine2Config = new EngineConfig(
                    "Stockfish 18",
                    engine2Path,
                    folder,
                    List.of(),
                    EngineConfig.Protocol.UCI,
                    Map.of(),
                    new EngineLimit(10)
            );

            // Create the match configuration.
            MatchConfig config = new MatchConfig.Builder()
                    .openingBook("engine/opening.bin") // You can set an opening book.
                    .repeatOpening(true) // Play each opening twice, swapping colors.
                    .totalGames(10) // Total number of games to play
                    .concurrency(1) // Number of threads to use (only 1 here)
                    .engine1Config(engine1Config) // Pull in Engine 1's configuration
                    .engine2Config(engine2Config) // Pull in Engine 2's configuration
                    .build();

            // Create the match runner class.
            ArenaRunner arena = new ArenaRunner(config);

            // Start the arena match.
            // As a listener, we'll export the PGN whenever a game finishes.
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
### 5. Using Syzygy Tablebases
```java
import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.SyzygyAnalyzer;
import com.pepero.jcb.api.syzygy.SyzygyTablebase;
import com.pepero.jcb.api.dto.SyzygyMoveDTO;

import java.io.IOException;
import java.nio.file.Path;

public class SyzygyExample {

    public static void main(String[] args) throws IOException {
        // Get the tablebase directory.
        Path syzygyDir = Path.of("syzygy/");
        // Load the tablebase.
        SyzygyTablebase tb = new SyzygyTablebase(syzygyDir);

        // Example position
        ChessGame game = ChessGame.fromFEN("8/8/4r3/3k4/8/8/2Q5/7K w - - 0 1");

        game.printBoard();

        // Show the WDL and DTZ results.

        // WDL values range from -2 to 2 (from the side to move's perspective).
        //  2 : a win with perfect play
        //  1 : a win, but drawn under the 50-move rule
        //  0 : a draw
        // -1 : a loss, but drawn under the 50-move rule
        // -2 : a loss with perfect play

        // DTZ tells you how many moves remain until a pawn move or capture.
        // If this position is lost (If wdl is negative), DTZ is negative.

        System.out.println("WDL : " + SyzygyAnalyzer.probeWdl(game, tb));
        System.out.println("DTZ : " + SyzygyAnalyzer.probeDtz(game, tb));
        System.out.println();

        // You can also find the best move in the current position.
        System.out.println("Syzygy best move : " + SyzygyAnalyzer.findBestMove(game, tb));
        System.out.println();

        // There's also a method that shows the WDL/DTZ results for every available move.
        for(SyzygyMoveDTO move : SyzygyAnalyzer.findRankedMoves(game, tb)) {
            System.out.println(move.move() + "  WDL" + move.ourWdl() + "  DTZ" + move.distance());
        }
    }
}
```
### 6. Using Gaviota Tablebases
```java
import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.GaviotaAnalyzer;
import com.pepero.jcb.api.gaviota.GaviotaTablebase;
import com.pepero.jcb.api.dto.GaviotaMoveDTO;

import java.nio.file.Path;

public class GaviotaExample {

    public static void main(String[] args) {
        // Get the tablebase directory.
        Path gaviotaDir = Path.of("gaviota/");
        // Load the tablebase.
        GaviotaTablebase tb = new GaviotaTablebase(gaviotaDir);

        // Example position
        ChessGame game = ChessGame.fromFEN("8/8/4r3/3k4/8/8/2Q5/7K w - - 0 1");

        game.printBoard();

        // Show the WDL and DTM results.

        // WDL values range from -1 to 1 (from the side to move's perspective).
        //  1 : a win
        //  0 : a draw
        // -1 : a loss

        // DTM tells you the distance to mate, in half-moves.
        // If this position is lost (if wdl is negative), DTM is negative too.

        System.out.println("WDL : " + GaviotaAnalyzer.probeWdl(game, tb));
        System.out.println("DTM : " + GaviotaAnalyzer.probeDtm(game, tb));
        System.out.println();

        // You can also find the best move in the current position.
        System.out.println("Gaviota best move : " + GaviotaAnalyzer.findBestMove(game, tb));
        System.out.println();

        // There's also a method that shows the WDL/DTM results for every available move.
        for (GaviotaMoveDTO move : GaviotaAnalyzer.findRankedMoves(game, tb)) {
            System.out.println(move.move() + "  WDL" + move.ourWdl() + "  DTM" + move.distance());
        }
    }
}
```

### 7. Using Perft
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

        // Run Perft and store the result.

        // As noted in the Javadoc, perft(int depth) is single-threaded and includes JVM warmup.
        chessGame.perft(5);

        // Now let's see the result with 4 threads.
        System.out.println();
        System.out.println("--------------------");
        System.out.println("Perft API 4 threads");
        System.out.println("--------------------");

        chessGame.perft(
                6, // Perft depth
                4 // Number of threads to use
        );

        // Now let's run Perft using Chessboard instead.
        Chessboard chessboard = new Chessboard(fen);

        System.out.println();
        System.out.println("-------------------------");
        System.out.println("Perft Bitboard 1 threads");
        System.out.println("-------------------------");

        PerftDriver.perftBitboardTest(
                chessboard,
                6, // Perft depth
                1, // Number of threads to use
                false, // Whether to suppress the test result and output
                false // Whether to use bulk counting
        );

        System.out.println();
        System.out.println("-------------------------");
        System.out.println("Perft Bitboard 4 threads");
        System.out.println("-------------------------");

        PerftDriver.perftBitboardTest(
                chessboard,
                7, // Perft depth
                4, // Number of threads to use
                false, // Whether to suppress the test result and output
                false // Whether to use bulk counting
        );
    }
}
```

### 8. Building and Using Opening Books

JCB can build a Polyglot opening book (`.bin`) directly from a multi-game PGN file, and use either a Polyglot (`.bin`) or an EPD (`.epd`) file as an opening book for engine matches.

**Building a `.bin` book from PGN games:**

```java
import com.pepero.jcb.api.book.PolyglotBookBuilder;

import java.io.IOException;

public class PGNtoPolyglotConvertExample {
    public static void main(String[] args) throws IOException {
        String inputPgn = "games.pgn";
        String outputBin = "opening.bin";
        int maxPly = 30; // 15 moves

        PolyglotBookBuilder.build(inputPgn, outputBin, maxPly);

        System.out.println("Opening book built successfully: " + outputBin);
    }
}
```

**Using an opening book in `MatchConfig`:**

`openingBook(String)` auto-detects the opening book type from the file extension:
- `.bin` &rarr; a Polyglot opening book, queried move by move during the game
- `.epd` &rarr; an EPD opening book, a list of positions where one is picked as the game's starting position
- `.pgn` &rarr; not supported directly; throws a helpful error pointing to `PolyglotBookBuilder`, which converts PGN games into a `.bin` book first

```java
MatchConfig config = new MatchConfig.Builder()
        .openingBook("engine/opening.bin") // or "engine/opening.epd"
        .repeatOpening(true) // play each opening twice, swapping colors
        .totalGames(10)
        .concurrency(1)
        .engine1Config(engine1Config)
        .engine2Config(engine2Config)
        .build();
```

## Performance
JCB provides two levels of API.

- `ChessGame` - High-level and object-oriented, designed for ease of use.
- `Chessboard` - Low-level bitboard-based, designed for performance-critical projects (such as engine development).
  *The benchmark results below were measured on an i7-14700KF CPU, single-threaded,
  after JIT warmup, averaged over 3 runs (no bulk counting).*

*(Note: shallower depths are more cache-friendly and can actually yield higher NPS.)*

#### Perft with `ChessGame`

| Threads   | NPS (5 ply) | NPS (6 ply) |
|-----------|-------------|-------------|
| 1 thread  | 6.94MNPS    | 7.26MNPS    |
| 2 threads | 14.02MNPS   | 14.05MNPS   |
| 4 threads | 26.78MNPS   | 27.25MNPS   |
| 8 threads | 45.28MNPS   | 50.72MNPS   |

#### Perft with `Chessboard`

| Threads   | NPS (6 ply) | NPS (7 ply) |
|-----------|-------------|-------------|
| 1 thread  | 57.61MNPS   | 57.99MNPS   |
| 2 threads | 112.62MNPS  | 112.27MNPS  |
| 4 threads | 220.68MNPS  | 217.74MNPS  |
| 8 threads | 385.11MNPS  | 398.76MNPS  |

> For performance-critical projects like engine development, using `Chessboard` directly is recommended.

> `ChessGame` is well suited for tooling, scripting, and analysis, but carries roughly 8x the overhead compared to the low-level API.

#### SAN / LAN Move Conversion (`ConvertStringMoveUtils`)

Since `ConvertStringMoveUtils` is called heavily during PGN parsing, opening book building, and UCI communication, its conversion throughput was also benchmarked (single-threaded, after JIT warmup, converting move sequences from randomly-played games).

| Conversion            | Throughput                 |
|------------------------|-----------------------------|
| SAN &rarr; move data   | 2,246,771 conversions/sec  |
| LAN &rarr; move data   | 2,603,415 conversions/sec  |

> LAN conversion is faster because SAN parsing also has to resolve move ambiguity (e.g. disambiguating `Nbd2` from another knight) and determine whether to append `+`/`#`, on top of the move generation both conversions share.

Reproduction code:
[`PerftResultTest`](https://github.com/pepero-lover/JCB/blob/main/src/test/java/com/pepero/jcb/perft/PerftResultTest.java)
[`ConvertStringMoveUtilsBenchmark`](https://github.com/pepero-lover/JCB/blob/main/src/test/java/com/pepero/jcb/api/convert/ConvertStringMoveUtilsBenchmark.java)
[`RandomGameGenerator`](https://github.com/pepero-lover/JCB/blob/main/src/test/java/com/pepero/jcb/api/convert/RandomGameGenerator.java)
## License
This project is licensed under the MIT License.
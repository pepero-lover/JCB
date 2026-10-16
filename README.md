# JCB (Java Chess Board)
![Java](https://img.shields.io/badge/Java-21%2B-blue)
![License](https://img.shields.io/badge/License-MIT-green)
![Size](https://img.shields.io/badge/Size-376KB-orange)
[![](https://jitpack.io/v/pepero-lover/JCB.svg)](https://jitpack.io/#pepero-lover/JCB)

[한국어](README.ko.md) | English

## Requirements
- Java 21 or higher

## About JCB
* This project ports the original C code into Java in an object-oriented style, while keeping the procedural C-style approach for the internal move-generation logic to maximize efficiency.
* At the same time, the API layer uses Enum classes for piece types, chessboard squares, and more, along with strengthened exception handling to make the API easier to use.
* The built jar library is only **376KB** in size, yet implements the complete rules and framework for chess.
* Core bitboard search performance is **60 MNPS (60 million nodes per second)**. (Benchmarked on an i7-14700KF CPU)
* Includes a built-in Syzygy / Gaviota tablebase decoder.
* This project has zero external library dependencies (except JUnit, used only for testing).

## Supported Features

- Chess variant support (Standard / Chess960 / Crazyhouse /
  Three-check / King of the Hill / Horde / Atomic / Giveaway / Suicide /
  Racing Kings)
- Syzygy tablebase probing (WDL / DTZ) and supports Standard, Atomic, Giveaway, Suicide chess variants
- Gaviota tablebase probing (WDL / DTM)
- PGN parsing, export, variation tree, and memory-efficient streaming for multi-gigabyte databases (`PGNDatabaseIterator`)
- Engine matches (`MatchRunner`)
- Opening books: build a Polyglot (`.bin`) book from PGN games, and read Polyglot / EPD (`.epd`) / PGN (`.pgn`) opening books
- Perft (single/multi-threaded)
- No external library dependencies

## Supported Chess Variants
| Variant          | FEN | UCI Integration        |
|------------------|-----|------------------------|
| Standard         | ✅  | ✅ (default UCI setup) |
| Chess960         | ✅  | ✅ (`UCI_Chess960`)    |
| Crazyhouse       | ✅  | ✅ (`UCI_Variant`)     |
| Three-check      | ✅  | ✅ (`UCI_Variant`)     |
| King of the Hill | ✅  | ✅ (`UCI_Variant`)     |
| Horde            | ✅  | ✅ (`UCI_Variant`)     |
| Atomic           | ✅  | ✅ (`UCI_Variant`)     |
| Giveaway         | ✅  | ✅ (`UCI_Variant`)     |
| Suicide          | ✅  | ✅ (`UCI_Variant`)     |
| Racing Kings     | ✅  | ✅ (`UCI_Variant`)     |

## Credits and References
* The move generation methods and bitboard logic in this chess engine (nearly everything outside of `com.pepero.jcb.api`) were deeply inspired by a tutorial created by **Code Monkey King**.
* Syzygy tablebase probing (`com.pepero.jcb.api.syzygy`) is derived from **Fathom** by Ronald de Man and Jon Dart (MIT license, https://github.com/jdart1/Fathom).
* Gaviota tablebase probing (`com.pepero.jcb.api.gaviota`) is derived from the **Gaviota Tablebases** probing code by Miguel A. Ballicora (X11/MIT license, https://github.com/michiguel/Gaviota-Tablebases).

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
  implementation 'com.github.pepero-lover:JCB:v1.15.0'
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
  implementation("com.github.pepero-lover:JCB:v1.15.0")
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
  <version>v1.15.0</version>
</dependency>
```

## Usage Examples

> All runnable examples are available under [`src/test/java/com/pepero/jcb/example`](https://github.com/pepero-lover/JCB/tree/main/src/test/java/com/pepero/jcb/example) in the repository, including a minimal UCI engine skeleton (`SimpleEngine`).

### 1. Basic Gameplay
_Full source: [`MainExample`](https://github.com/pepero-lover/JCB/blob/main/src/test/java/com/pepero/jcb/example/MainExample.java)_

This is the most standard way to create a chess game and make moves in sequence, whether from the console or from user input (using LAN format).

```java
import com.pepero.jcb.api.ChessGame;

public class MainExample {
    public static void main(String[] args) {
        // Initialize with the default starting position.
        ChessGame chessGame = ChessGame.startPosition();

        // Make moves
        chessGame.makeMoveLan("e2e4");
        chessGame.makeMoveLan("e7e5");
        chessGame.makeMoveLan("g1f3");

        // Check the current turn and FEN
        System.out.println("Current turn: " + chessGame.getTurn());
        System.out.println("Current FEN: " + chessGame.getFEN());

        // Test undo and redo
        if (chessGame.canUndo()) {
            System.out.println("Position before undo: ");
            chessGame.printBoard();
            System.out.println();

            chessGame.unmakeMove(); // undo g1f3

            System.out.println("Position after undo: ");
            chessGame.printBoard();
        }
    }
}
```

### More Examples

Browse the full source in the [`example`](https://github.com/pepero-lover/JCB/tree/main/src/test/java/com/pepero/jcb/example) folder.

| Example                            | What it shows                                                               | Key classes                                  |
|------------------------------------|-----------------------------------------------------------------------------|----------------------------------------------|
| [Move hints for chess GUIs](https://github.com/pepero-lover/JCB/blob/main/src/test/java/com/pepero/jcb/example/GUIExample.java) | Show the squares a clicked piece can move to, with score calculation        | `ChessGame`, `MoveInfo`, `Square`            |
| [Game-over conditions](https://github.com/pepero-lover/JCB/blob/main/src/test/java/com/pepero/jcb/example/GameStateExample.java) | Detect checkmate, stalemate, draws and other state checks                   | `ChessGame`, `GameOverReason`                |
| [Engine vs. Engine matches](https://github.com/pepero-lover/JCB/blob/main/src/test/java/com/pepero/jcb/example/EngineExample.java) | Run UCI engine matches with concurrency, adjudication, and PGN output       | `MatchRunner`, `MatchConfig`, `EngineConfig` |
| [Syzygy tablebases](https://github.com/pepero-lover/JCB/blob/main/src/test/java/com/pepero/jcb/example/SyzygyExample.java) | Probe WDL / DTZ and rank moves from a Syzygy tablebase                      | `SyzygyTablebase`, `SyzygyAnalyzer`          |
| [Gaviota tablebases](https://github.com/pepero-lover/JCB/blob/main/src/test/java/com/pepero/jcb/example/GaviotaExample.java) | Probe WDL / DTM and rank moves from a Gaviota tablebase                     | `GaviotaTablebase`, `GaviotaAnalyzer`        |
| [Perft](https://github.com/pepero-lover/JCB/blob/main/src/test/java/com/pepero/jcb/example/PerftExample.java) | Single / multi-threaded perft with `ChessGame` and `Chessboard`             | `PerftDriver`, `Chessboard`                  |
| [Opening books](https://github.com/pepero-lover/JCB/blob/main/src/test/java/com/pepero/jcb/example/PGNtoPolyglotConvertExample.java) | Build a Polyglot `.bin` book from PGN games and use books in engine matches | `PolyglotBookBuilder`, `MatchConfig`         |
| [Streaming large PGN databases](https://github.com/pepero-lover/JCB/blob/main/src/test/java/com/pepero/jcb/example/PGNStreamExample.java) | Iterate multi-gigabyte PGN files game by game without loading them at once  | `PGNDatabaseIterator`, `PGNGameStub`         |

`MatchConfig.Builder.openingBook(Path)` auto-detects the opening book type from the file extension:
- `.bin` &rarr; a Polyglot opening book, queried move by move during the game
- `.epd` &rarr; an EPD opening book, a list of positions where one is picked as the game's starting position
- `.pgn` &rarr; a PGN opening book, pick random of pgn games and plays it up to a user-defined maximum move count

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

| Conversion           | Throughput                |
|----------------------|---------------------------|
| SAN &rarr; move data | 4,694,884 conversions/sec |
| LAN &rarr; move data | 4,710,466 conversions/sec |

Reproduction code:
[`PerftResultTest`](https://github.com/pepero-lover/JCB/blob/main/src/test/java/com/pepero/jcb/perft/PerftResultTest.java)
[`ConvertStringMoveUtilsBenchmark`](https://github.com/pepero-lover/JCB/blob/main/src/test/java/com/pepero/jcb/api/convert/ConvertStringMoveUtilsBenchmark.java)
[`RandomGameGenerator`](https://github.com/pepero-lover/JCB/blob/main/src/test/java/com/pepero/jcb/api/convert/RandomGameGenerator.java)
## License
This project is licensed under the MIT License.
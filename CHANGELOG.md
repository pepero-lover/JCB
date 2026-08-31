# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- `ChessGame.setListenerExceptionHandler(BiConsumer<ChessGameListener, Throwable>)` to
  customize how exceptions thrown by listener callbacks are handled. Defaults to logging
  via `java.util.logging.Logger`.
- Added `getListeners` method on `ChessGame`
- `ChessGame.getZobristHash()` — exposes JCB's internal Zobrist hash, which (unlike
  `getPolyglotHash()`) encodes variant-specific state such as Crazyhouse pocket contents
  and Atomic captured-piece state.
- `ChessGame.samePosition(ChessGame other)` — explicit position-equality check based on
  `getZobristHash()`, replacing the old `equals()`-based position comparison.
- Added `printBoard(PrintStream out)` and `printHistory(PrintStream out)` /
  `printHistory(int maxNodeSize, PrintStream out)` overloads, allowing callers to
  redirect board/history output (e.g. to a log file or an in-memory stream for
  testing) instead of always writing to `System.out`.

### Changed
- `ChessGame.equals()`/`hashCode()` now use identity comparison (default `Object` behavior)
  instead of comparing the internal Zobrist hash. Comparing by position while `ChessGame` is
  mutable violated the general hash-collection contract (mutating the position after adding
  a `ChessGame` to a `HashSet`/`HashMap` made it unreachable). Use the new `samePosition()`
  method to compare positions explicitly. **This is a breaking change** if you relied on
  position-based `equals`/`hashCode`.
- Refactored `ChessGame.getGameoverReason()` to `ChessGame.getGameOverReason()`
- `ChessGameListener` callbacks (`onMoveMade`, `onMoveUnmade`, `onMoveRemade`,
  `onPositionJumped`, `onGameOver`, `onHistoryChanged`) now receive the source `ChessGame`
  as their first parameter. Previously there was no way to tell which game an event came
  from when a single listener instance was shared across multiple `ChessGame`s (e.g. logging
  or PGN-recording listeners reused across `ArenaRunner` matches). **This is a breaking
  change** for any existing `ChessGameListener` implementations.
- `printBoard()` and `printHistory()` (and its `maxNodeSize` overload) now delegate
  to their new `PrintStream` overloads with `System.out` as the default, removing
  the hardcoded `System.out.println` calls from the internal implementation.

### Fixed
- Added read lock on `getPieceCount` methods on `ChessGame` class
- `ChessGame` no longer lets an exception thrown by a `ChessGameListener` callback
  (`onMoveMade`, `onMoveUnmade`, `onMoveRemade`, `onPositionJumped`, `onGameOver`,
  `onHistoryChanged`) propagate out of `makeMove()`/`unmakeMove()`/etc. Previously, a
  misbehaving listener could both prevent subsequent listeners from being notified and
  cause the caller to receive an exception even though the move/state change had already
  been applied successfully.
- Listener notifications for moves (`makeMove*`) now always fire after `writeLock` is
    released, matching the existing behavior of `unmakeMove`/`remakeMove`/`jumpToNode`/etc.
    Previously the two families were inconsistent, and the same call (e.g. `unmakeMove()`)
    could notify with or without the lock held depending on whether it was invoked directly
    or via `goBackward()`.
- `getGameResult()`/`getGameoverReason()` no longer re-fire `onGameOver` on every call once
  the game has ended; the event now only fires the first time a terminal state is discovered.
- Fixed a bug where `goForward()` and `goBackward()` invoked listener callbacks
  (`onMoveMade`, `onMoveUnmade`, `onGameOver`) while `ChessGame`'s internal write
  lock was still held, due to reentrant locking around `remakeMove()` /
  `unmakeMove()`. This could block concurrent readers for the duration of
  listener execution, contrary to the class's documented concurrency contract.
- Converted `printHistory` and `removeNodeFromCache` internal recursion to
  iterative implementations to avoid potential `StackOverflowError` on very
  deep, mostly-linear move histories (e.g. long imported PGNs with few
  variations).
- Fixed `deleteVariation()` invoking `onPositionJumped` / `onGameOver` while the write
    lock was still held, when deleting the current node caused an internal jump to its
    parent. `jumpToNode()`'s logic was split into an internal step (runs under the lock)
    and a notification step (runs after the lock is released), and `deleteVariation()`
    now uses the internal step directly and defers notification until it releases its
    own lock, alongside its own notifications.
- Fixed `deleteVariation()` and `promoteVariationLocal()` not firing `onGameOver` when
  the operation caused the game to become newly over (e.g. promoting a variation that
  ends in checkmate to the mainline). Both now use the same evaluate-then-notify-after-
  unlock pattern as the rest of the class.
- Fixed `getGameResult()` and `getGameOverReason()` invoking `onGameOver` while the
  write lock was still held; notification is now deferred until after the lock is
  released.

### Performance


## [1.8.0] - 2026-08-30

### Added
- `CrazyhouseExample` demonstrating drop moves via LAN/SAN/manual placement (`makeDropMove`).
- `EncodedPieces.normalizePieceColor(pieceType, side)` to standardize color-agnostic
  piece constants across drop/move validation and LAN/SAN parsing, replacing scattered
  inline `+6`/`-6` color-flip logic in `ConvertStringMoveUtils`.
- Automated Syzygy verification test suite, with dedicated test cases for the Atomic
  and Antichess variants (both verified against reference data).
- `GaviotaTest` with verification coverage for the Gaviota DTM decoder.
- `ChessGameTest` suite.
### Fixed
- `isLegalDrop` not validating the dropped piece's color against the side to move.
- `isLegalMove` not checking the move's piece field against generated moves — both
  bugs could allow a move to be accepted even if it didn't actually match a legal
  move in the move list.
- Multiple bugs and a crash in `SyzygyTablebase`/`SyzygyEncoder`.
- DTZ bug specific to the Antichess variant.
- Bug in `ConvertStringMoveUtils` surfaced during Syzygy test verification.
- Timing bug in `ChessClock`.
- Bug in `LongObjectOpenHashMap`.
- Check-then-act race condition in `ChessGame.goForward()` / `goBackward()`,
  resolved with locking.
- Horde-variant validation bug in `FENValidator`, and strengthened Racing Kings
  FEN validation.
- Incorrect Javadoc comments on the `makeMoveAll` methods.
### Changed
- `LongObjectOpenHashMap` moved from `core` to a new `util` package.
- `ChessGame`: general refactoring alongside the above bug fixes.
- `Chessboard`'s print/output method now also prints the FEN string.
### Internal
- Moved public classes under `api/parse/pgn` into `api/` and made them
  package-private, tightening the public API surface.

[Unreleased]: https://github.com/pepero-lover/JCB/compare/v1.8.0...HEAD
[1.8.0]: https://github.com/pepero-lover/JCB/compare/v1.7.2...v1.8.0
[1.7.2]: https://github.com/pepero-lover/JCB/compare/v1.7.1...v1.7.2
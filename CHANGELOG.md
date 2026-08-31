# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- `ChessGame.setListenerExceptionHandler(BiConsumer<ChessGameListener, Throwable>)` to
  customize how exceptions thrown by listener callbacks are handled. Defaults to logging
  via `java.util.logging.Logger`.

### Changed

### Fixed
- Added read lock on `getPieceCount` methods on `ChessGame` class
- `ChessGame` no longer lets an exception thrown by a `ChessGameListener` callback
  (`onMoveMade`, `onMoveUnmade`, `onMoveRemade`, `onPositionJumped`, `onGameOver`,
  `onHistoryChanged`) propagate out of `makeMove()`/`unmakeMove()`/etc. Previously, a
  misbehaving listener could both prevent subsequent listeners from being notified and
  cause the caller to receive an exception even though the move/state change had already
  been applied successfully.

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
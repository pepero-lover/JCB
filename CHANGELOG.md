# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- On `ChessGame`, added `getMainLinePGN()` method to get only main line move data pgn string.
- On `MoveDataDTO`, added `ply`, `fullMovePly`, `san` parameter
- Added overload on `getMainlineData(int maxNodes)`, throwing `NodeOverflowException` when mainline data is bigger than
  `maxNodes`. and on `getMainlineData()`, the default is `MAX_PGN_NODE_COUNT`.
- Added `onGameStateChecked(ChessGame source, GameResult result, GameOverReason reason)` on `ChessGameListener` which is called at making a move or
  unmaking, remaking, deleting/promoting variation, jumping to node.

### Changed
- On `ChessGame`, the write lock on `getLegalMoves*` methods replaced to read lock.
- On `MoveNode` and `MoveNodeDTO`, added `ply`, `fullMovePly` parameter.
  `ply` is starting 0 at root node and `ply` also refers to depth.
  `fullMovePly` is used to show full move counter data, but increases 1 per move.
  (on FEN full move data, increments only black has moved)
- On `printHistory` at `ChessGame`, removed uci-code text on variation printing '└'
- and also on `printHistory`, now prints node id right next to the move string. (e.g. "e4 [#2]")
  If you want to use node id printing, use `printHistory*(boolean showNodeId)` methods.
- On `MoveNode` class (package-private, not exposed), removed not using `san` parameter.
- On every `nodeCounter` variables, changed `incrementAndGet` to `getAndIncrement`.
- On every `gameoverReason` variables, refactored `gameOverReason`. (private variable)
- On `loadPGN` at `ChessGame`, added notifying listeners (`notifyHistoryChanged`,
  `notifyPositionJumped`, `notifyStateChecked`, `notifyGameOver`)
- On `evaluateGameState` at `ChessGame`, changed `isGameOver()` with claimable draws to 
  `isGameOver(false)` excluding claimable draws.
- On `isGameOver()`, changed the sequence of checking game over of claim draws (moved to the very end)

### Fixed
- On `loadPGN` at `ChessGame`, fixed the `loadPGN` method didn't update node id counter.
- On `ChessGame`, fixed the bug not checking the last node position but checking 
  current position at `getGameResult()`, `getGameOverReason()`, `getPGN()`, `getPurePGN()`, `deleteVariation()`, `promoteVariationLocal()`,
  `forceEndGame()`
  methods.
- On `ChessGame`, fixed bug not initializing `gameOverReason` on `loadPGN` method.
- On `ChessGame`, fixed `jumpToMainlinePly` always notifying the game over listener.
- On `ChessGame`, fixed bug `dispatchMoveNotifications`, `dispatchUndoNotifications`, `dispatchRedoNotifications`
  aren't thread-safe because of using current board game result instead of using `MoveOutcome outcome`.
- On `ChessGame`, fixed bug always calling `onGameOver()` listener on 
  `internalMakeMove`, `internalMakeMoveValidated`, `internalUnmakeMove`, `internalRemakeMove` methods
- On `ChessGame.jumpToMainlinePly`, fixed not getting fen for `notifyPositionJumped` inside of `writeLock`, but outside of `writeLock`.
- On `ChessGame.setCurrentMoveClockMilliSeconds`, added zero-padding

### Performance
- On `MoveGenerator`, replaced `if` cases on piece attack finding to `switch-case`.
- On `MoveGenerator`, added `isRacingKings` to store this variant is racing king variant.
- On `internalJumpToNode` at `ChessGame`, instead of resetting `Chessboard` to start position
  and going target node, replaced with getting and going to LCA node and going target node
  (O(root node distance) to O(distance current node between target node))
- On `jumpToMainlinePly` at `ChessGame`, replaced calculating `currentPly` constant with
  `MoveNode.ply` and removed list to storing history
- On `MoveNode`, added caching `san`, `fen` string (used on `ChessGame.getMainLineData`,
  `PGNExporter.buildPGNTreeWithSan`)
- On `ChessGame.printHistory`, fixed overhead calling `getCurrentNodeId` and locking read lock again.

## [1.9.0]

### Added
- `ChessGame.setListenerExceptionHandler(BiConsumer<ChessGameListener, Throwable>)` to
  customize how exceptions thrown by listener callbacks are handled. Defaults to logging
  via `java.util.logging.Logger`.
- `ChessGame.getListeners()` — returns a read-only snapshot view of the currently
  registered `ChessGameListener`s.
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
- Refactored `ChessGame.getGameoverReason()` to `ChessGame.getGameOverReason()`.
  **This is a breaking change** if you are using the `getGameoverReason` method.
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
- Added read lock on `getPieceCount` methods on `ChessGame` class.
- `ChessGame` no longer lets an exception thrown by a `ChessGameListener` callback
  (`onMoveMade`, `onMoveUnmade`, `onMoveRemade`, `onPositionJumped`, `onGameOver`,
  `onHistoryChanged`) propagate out of `makeMove()`/`unmakeMove()`/etc. Previously, a
  misbehaving listener could both prevent subsequent listeners from being notified and
  cause the caller to receive an exception even though the move/state change had already
  been applied successfully.
- Listener notifications for moves (`makeMove*`, `makeDropMove`) now always fire after
  `writeLock` is released, matching the existing behavior of
  `unmakeMove`/`remakeMove`/`jumpToNode`/etc. Previously the two families were
  inconsistent, and the same call (e.g. `unmakeMove()`) could notify with or without
  the lock held depending on whether it was invoked directly or via `goBackward()`.
- `getGameResult()`/`getGameOverReason()` no longer re-fire `onGameOver` on every call once
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
- **`ChessGame.getChecker()`**: fixed an infinite loop that occurred whenever the
  side to move was in check. The bit-clearing step incorrectly isolated the
  already-processed checker bit (`checkersMask &= 1L << square`) instead of
  clearing it, so the loop condition (`checkersMask != 0L`) could never become
  false. Now clears the bit correctly (`checkersMask &= ~(1L << square)`).
- **`ChessGame.getHeaders()`**: no longer returns a direct reference to the
  internal `headers` map. Callers mutating the returned map outside of the
  class's read/write lock could corrupt game state read or written
  concurrently by another thread, breaking the thread-safety guarantee
  documented on the class. Now returns a defensive copy
  (`new LinkedHashMap<>(headers)`), consistent with `getMoveHistory()`.
- **`ChessGame.getPGN(int)` / `getPurePGN(int)`**: fixed a lock-reentrancy bug
  where these methods called the public `getGameResult()` while already
  holding `writeLock`. Since `getGameResult()` notifies listeners
  (`onGameOver`) internally and assumes it is not nested inside another
  `writeLock`-holding frame, its `writeLock.unlock()` only decremented the
  reentrant hold count rather than truly releasing the lock — so
  `notifyGameOver()` could fire while `writeLock` was still effectively held,
  violating the class's documented invariant that listener callbacks never run
  while the lock is held, and risking deadlock for listeners that call back
  into other locked resources. Both methods now evaluate game state directly
  via `evaluateGameStateForNotification(...)` inside the lock and defer
  listener notification until after `writeLock` is released, matching the
  pattern already used by `deleteVariation`, `promoteVariationLocal`, and
  `jumpToNode`.
- **`ChessGame.getCurrentMoveInfo()`**: fixed a `NullPointerException` thrown when
  called at the start position (`currentNode == moveHistoryRoot`, whose
  `moveData` is `null`). Now throws `MoveNotFoundException` with a clear
  message instead, consistent with how other current-node-dependent methods
  (`setCurrentMoveClock`, `setTimeStamp`, etc.) guard against the root node.
- **`ChessGame.getRootNode()` / `getRootNode(int)`**: the temporary
  `Chessboard` built from `startPositionFEN` was missing `isChess960`
  (only `gameVariant` was being copied over). For Chess960 games this caused
  `PGNExporter.buildPGNTreeWithSan(...)` to build the SAN tree against a
  non-960 board, producing incorrect castling notation (and potentially
  incorrect move legality) in the generated `MoveNodeDTO` tree. Now sets both
  `tempBoard.gameVariant` and `tempBoard.isChess960` from the live game before
  building the tree.
- **`ChessGame.getMainlineData()`**: the temporary `Chessboard` built from
  `startPositionFEN` didn't have `gameVariant` or `isChess960` set at all
  (unlike `getRootNode()`), so it silently defaulted to a standard,
  non-Chess960 board. For non-standard variant games (Crazyhouse, Atomic,
  Three-check, Chess960, etc.) this meant `MoveGenerator.makeMove(...)` ran
  under the wrong rules while walking the mainline, producing incorrect FEN
  strings embedded in each `MoveDataDTO`. Now sets `tempBoard.gameVariant`
  and `tempBoard.isChess960` from the live game before walking the mainline,
  matching `getRootNode()`.
- **`ChessGame.forceEndGame()`** (used by `resign()`, `agreeDraw()`, `timeOver()`,
  `adjudication()`, `forceEndGameExternal()`): forced game-ending events were
  being recorded on `currentNode` instead of the mainline tip node. Since
  `getGameResult()`, `getGameOverReason()`, `getPGN()`, and `getPurePGN()` all
  re-evaluate state from `getLastMainlineNode(moveHistoryRoot)` rather than
  `currentNode` (this is also why undoing a move doesn't change a finished
  game's reported result), calling `resign()`/`agreeDraw()`/etc. while
  browsing history (i.e. `currentNode` isn't the mainline tip) meant the
  forced result was silently discarded the next time any of those methods was
  called. Now marks `getLastMainlineNode(this.moveHistoryRoot)` instead,
  consistent with how the rest of the class determines "the" game result.
- **`ChessGame.forceEndGame()`**: separately, the "already finished" guard
  checked the raw `this.gameoverReason` field, which could still read
  `NOTGAMEOVER` even though the mainline tip had already naturally reached a
  terminal state (checkmate, stalemate, etc.) if no evaluating method
  (`getGameResult()`, `isGameOver()`, ...) had been called since. This let
  `resign()` / `agreeDraw()` / `timeOver()` / `adjudication()` silently
  overwrite a real game result with a forced one. The guard now refreshes
  state from the mainline tip first via
  `evaluateGameStateForNotification(...)` before checking, and correctly
  notifies `onGameOver` for a naturally-discovered terminal state (if this is
  the first time it's observed) before rejecting the forced end with
  `IllegalStateException`.

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

[Unreleased]: https://github.com/pepero-lover/JCB/compare/v1.9.0...HEAD
[1.9.0]: https://github.com/pepero-lover/JCB/compare/v1.8.0...v1.9.0
[1.8.0]: https://github.com/pepero-lover/JCB/compare/v1.7.2...v1.8.0
[1.7.2]: https://github.com/pepero-lover/JCB/compare/v1.7.1...v1.7.2
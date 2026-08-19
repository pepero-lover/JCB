---
name: "\U0001F41B Bug report"
about: Create a report to help us improve
title: "[BUG] "
labels: bug
assignees: pepero-lover

---

### Describe the bug
A clear and concise description of what the bug is.

### To Reproduce
Steps to reproduce the behavior or a minimal reproducible code snippet:
1. FEN (if applicable): `e.g. r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4`
2. Move sequence or code:
   ```java
   ChessGame game = ChessGame.fromFEN(...);
   game.makeMove("e2e4");
   ```
3. See error

### Expected behavior
A clear and concise description of what you expected to happen (e.g. should be a legal move, no exception expected, etc.).

### Environment
- JCB Version: [e.g. v1.6.0]
- Java Version: [e.g. Java 21]
- OS: [e.g. Windows 11, macOS, Linux]

### Stack Trace / Error Log
If applicable, paste the error log or stack trace below:
```text
// Paste log here
```
### Additional context
Add any other context about the problem here (e.g., multi-threading environment, Syzygy tablebase probing involved, specific variants used).

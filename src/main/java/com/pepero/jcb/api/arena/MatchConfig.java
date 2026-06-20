package com.pepero.jcb.api.arena;

import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.GameVariants;
import com.pepero.jcb.hash.PolyglotBookReader;

import java.util.ArrayList;
import java.util.List;

public class MatchConfig {
    public record EngineLimit(int depthLimit, long timeControlMs, long incrementMs) {
        public boolean hasTimeLimit() {
            return timeControlMs > 0;
        }
        public boolean hasDepthLimit() {
            return depthLimit > 0;
        }
    }

    private final int rounds;
    private final GameVariants variant;
    private final List<String> startingFens;

    private final EngineLimit engine1Limit;
    private final EngineLimit engine2Limit;
    private final int multiPv;

    private final PolyglotBookReader openingBook;

    private MatchConfig(Builder builder) {
        this.rounds = builder.rounds;
        this.variant = builder.variant;
        this.startingFens = builder.startingFens;
        this.engine1Limit = builder.engine1Limit;
        this.engine2Limit = builder.engine2Limit;
        this.multiPv = builder.multiPv;
        this.openingBook = builder.openingBook;
    }

    public int getRounds() { return rounds; }
    public GameVariants getVariant() { return variant; }
    public List<String> getStartingFens() { return startingFens; }
    public EngineLimit getEngine1Limit() { return engine1Limit; }
    public EngineLimit getEngine2Limit() { return engine2Limit; }
    public int getMultiPv() { return multiPv; }
    public PolyglotBookReader getOpeningBook() { return openingBook; }
    public boolean hasOpeningBook() { return openingBook != null; }

    public static class Builder {
        private int rounds = 1;
        private GameVariants variant = GameVariants.STANDARD;
        private List<String> startingFens = new ArrayList<>(List.of(Chessboard.start_position));

        private EngineLimit engine1Limit = new EngineLimit(0, 0, 0);
        private EngineLimit engine2Limit = new EngineLimit(0, 0, 0);

        private int multiPv = 1;

        private PolyglotBookReader openingBook = null;

        public Builder rounds(int rounds) {
            this.rounds = rounds;
            return this;
        }

        public Builder variant(GameVariants variant) {
            this.variant = variant;
            return this;
        }

        public Builder openingBook(String bookFilePath) {
            this.openingBook = new PolyglotBookReader(bookFilePath);
            return this;
        }

        public Builder addStartingFen(String fen) {
            if (this.startingFens.size() == 1 && this.startingFens.contains(Chessboard.start_position)) {
                this.startingFens.clear();
            }
            this.startingFens.add(fen);
            return this;
        }

        public Builder depthLimit(int depth) {
            this.engine1Limit = new EngineLimit(depth, engine1Limit.timeControlMs(), engine1Limit.incrementMs());
            this.engine2Limit = new EngineLimit(depth, engine2Limit.timeControlMs(), engine2Limit.incrementMs());
            return this;
        }

        public Builder timeControl(long timeMs, long incMs) {
            this.engine1Limit = new EngineLimit(engine1Limit.depthLimit(), timeMs, incMs);
            this.engine2Limit = new EngineLimit(engine2Limit.depthLimit(), timeMs, incMs);
            return this;
        }


        public Builder depthLimitAsymmetric(int whiteDepth, int blackDepth) {
            this.engine1Limit = new EngineLimit(whiteDepth, engine1Limit.timeControlMs(), engine1Limit.incrementMs());
            this.engine2Limit = new EngineLimit(blackDepth, engine2Limit.timeControlMs(), engine2Limit.incrementMs());
            return this;
        }

        public Builder timeControlAsymmetric(long whiteTimeMs, long whiteIncMs, long blackTimeMs, long blackIncMs) {
            this.engine1Limit = new EngineLimit(engine1Limit.depthLimit(), whiteTimeMs, whiteIncMs);
            this.engine2Limit = new EngineLimit(engine2Limit.depthLimit(), blackTimeMs, blackIncMs);
            return this;
        }

        public Builder multiPv(int multiPv) {
            this.multiPv = multiPv;
            return this;
        }

        public MatchConfig build() {
            boolean whiteHasBoth = engine1Limit.hasDepthLimit() && engine1Limit.hasTimeLimit();
            boolean blackHasBoth = engine2Limit.hasDepthLimit() && engine2Limit.hasTimeLimit();

            if (whiteHasBoth || blackHasBoth) {
                throw new IllegalStateException("Engine config cannot set both depth limits and time controls.");
            }

            if (!engine1Limit.hasDepthLimit() && !engine1Limit.hasTimeLimit() &&
                    !engine2Limit.hasDepthLimit() && !engine2Limit.hasTimeLimit()) {
                throw new IllegalStateException("Engine config must be set one of the engine's search limits! (depth or time)");
            }

            return new MatchConfig(this);
        }
    }
}
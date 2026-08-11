package com.pepero.jcb.api.arena;

import com.pepero.jcb.api.book.PolyglotBookReader;
import com.pepero.jcb.core.GameVariants;

public class MatchConfig {
    private final int totalGames;
    private final int concurrency;

    private final EngineConfig engine1Config;
    private final EngineConfig engine2Config;

    private final GameVariants variants;

    private final int seed;

    private final PolyglotBookReader openingBook;
    private final boolean repeatOpening;

    private final AdjudicationRule resignRule;
    private final AdjudicationRule drawRule;

    private MatchConfig(Builder builder) {
        this.totalGames = builder.totalGames;
        this.concurrency = builder.concurrency;
        this.engine1Config = builder.engine1Config;
        this.engine2Config = builder.engine2Config;
        this.variants = builder.variants;
        this.openingBook = builder.openingBook;
        this.repeatOpening = builder.repeatOpening;
        this.resignRule = builder.resignRule;
        this.drawRule = builder.drawRule;
        this.seed = builder.seed;
    }

    public int getTotalGames() { return totalGames; }
    public int getConcurrency() { return concurrency; }
    public EngineConfig getEngine1Config() { return engine1Config; }
    public EngineConfig getEngine2Config() { return engine2Config; }
    public GameVariants getVariants() { return variants; }
    public PolyglotBookReader getOpeningBook() { return openingBook; }
    public boolean isRepeatOpening() { return repeatOpening; }
    public boolean hasOpeningBook() { return openingBook != null; }
    public AdjudicationRule getResignRule() { return resignRule; }
    public AdjudicationRule getDrawRule() { return drawRule; }
    public int getSeed() { return seed; }

    public static class Builder {
        private int totalGames;
        private int concurrency = 1;

        private EngineConfig engine1Config;
        private EngineConfig engine2Config;

        private GameVariants variants = GameVariants.STANDARD;

        private PolyglotBookReader openingBook = null;
        private boolean repeatOpening = true;

        private AdjudicationRule resignRule = null;
        private AdjudicationRule drawRule = null;

        private int seed = 1111;

        public Builder totalGames(int totalGames) {
            this.totalGames = totalGames;
            return this;
        }

        public Builder concurrency(int concurrency) {
            this.concurrency = concurrency;
            return this;
        }

        public Builder openingBook(String bookFilePath) {
            this.openingBook = new PolyglotBookReader(bookFilePath);
            return this;
        }

        public Builder repeatOpening(boolean repeatOpening) {
            this.repeatOpening = repeatOpening;
            return this;
        }

        public Builder engine1Config(EngineConfig config) {
            this.engine1Config = config;
            return this;
        }

        public Builder engine2Config(EngineConfig config) {
            this.engine2Config = config;
            return this;
        }

        public Builder variants(GameVariants variants) {
            this.variants = variants;
            return this;
        }

        public Builder resignRule(AdjudicationRule resignRule) {
            this.resignRule = resignRule;
            return this;
        }

        public Builder drawRule(AdjudicationRule drawRule) {
            this.drawRule = drawRule;
            return this;
        }

        public Builder seed(int seed) {
            this.seed = seed;
            return this;
        }

        public MatchConfig build() {
            if(engine1Config == null) throw new IllegalArgumentException("Engine 1 config not found!");
            if(engine2Config == null) throw new IllegalArgumentException("Engine 2 config not found!");
            if(totalGames <= 0) throw new IllegalArgumentException("Total Games should be exist and positive number!");
            if(concurrency <= 0) throw new IllegalArgumentException("Concurrency should be positive number!");

            return new MatchConfig(this);
        }
    }
}
package com.pepero.jcb.api.arena;

import com.pepero.jcb.api.book.PolyglotBookReader;

public class MatchConfig {
    private final int totalGames;
    private final int concurrency;

    private final EngineConfig engine1Config;
    private final EngineConfig engine2Config;

    private final PolyglotBookReader openingBook;
    private final boolean repeatOpening;
    private final boolean randomBookMove;

    private final AdjudicationRule resignRule;
    private final AdjudicationRule drawRule;

    private MatchConfig(Builder builder) {
        this.totalGames = builder.totalGames;
        this.concurrency = builder.concurrency;
        this.engine1Config = builder.engine1Config;
        this.engine2Config = builder.engine2Config;
        this.openingBook = builder.openingBook;
        this.randomBookMove = builder.randomBookMove;
        this.repeatOpening = builder.repeatOpening;
        this.resignRule = builder.resignRule;
        this.drawRule = builder.drawRule;
    }

    public int getTotalGames() { return totalGames; }
    public int getConcurrency() { return concurrency; }
    public EngineConfig getEngine1Config() { return engine1Config; }
    public EngineConfig getEngine2Config() { return engine2Config; }
    public PolyglotBookReader getOpeningBook() { return openingBook; }
    public boolean isRepeatOpening() { return repeatOpening; }
    public boolean hasOpeningBook() { return openingBook != null; }
    public boolean isRandomBookMove() { return randomBookMove; }
    public AdjudicationRule getResignRule() { return resignRule; }
    public AdjudicationRule getDrawRule() { return drawRule; }

    public static class Builder {
        private int totalGames;
        private int concurrency = 1;

        private EngineConfig engine1Config;
        private EngineConfig engine2Config;

        private boolean randomBookMove = false;

        private PolyglotBookReader openingBook = null;
        private boolean repeatOpening = true;

        private AdjudicationRule resignRule = null;
        private AdjudicationRule drawRule = null;

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

        public Builder randomBookMove(boolean randomBookMove) {
            this.randomBookMove = randomBookMove;
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

        public MatchConfig build() {
            if(engine1Config == null) throw new IllegalArgumentException("Engine 1 config not found!");
            if(engine2Config == null) throw new IllegalArgumentException("Engine 2 config not found!");

            return new MatchConfig(this);
        }
    }
}
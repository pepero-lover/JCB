package com.pepero.jcb.api.arena;

public record EngineLimit(int depthLimit, long timeControlMs, long incrementMs) {
        public boolean hasTimeLimit() {
            return timeControlMs > 0;
        }
        public boolean hasDepthLimit() {
            return depthLimit > 0;
        }

    public EngineLimit(long timeControlMs, long incrementMs) {
        this(-1, timeControlMs, incrementMs);
    }

    public EngineLimit(int depthLimit) {
        this(depthLimit, -1, -1);
    }
}
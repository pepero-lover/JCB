package com.pepero.jcb.api.arena;

public record EngineLimit(int depthLimit, long timeControlMs, long incrementMs) {

    public static EngineLimit depth(int depthLimit) {
        return new EngineLimit(depthLimit, -1, -1);
    }

    public static EngineLimit time(long timeControlMs, long incrementMs) {
        return new EngineLimit(-1, timeControlMs, incrementMs);
    }

    public EngineLimit withDepth(int depthLimit) {
        return new EngineLimit(depthLimit, timeControlMs, incrementMs);
    }

    public EngineLimit withTime(long timeControlMs, long incrementMs) {
        return new EngineLimit(depthLimit, timeControlMs, incrementMs);
    }

    public boolean hasTimeLimit() { return timeControlMs > 0; }
    public boolean hasDepthLimit() { return depthLimit > 0; }
}
package com.pepero.jcb.api.arena;

public record EngineLimit(int depthLimit, long timeControlMs, long incrementMs) {
        public boolean hasTimeLimit() {
            return timeControlMs > 0;
        }
        public boolean hasDepthLimit() {
            return depthLimit > 0;
        }
    }
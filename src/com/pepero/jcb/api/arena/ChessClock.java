package com.pepero.jcb.api.arena;

public class ChessClock {
    private long whiteTimeMs;
    private long blackTimeMs;
    
    private final long whiteIncMs;
    private final long blackIncMs;

    public ChessClock(long whiteStartMs, long whiteIncMs, long blackStartMs, long blackIncMs) {
        this.whiteTimeMs = whiteStartMs;
        this.whiteIncMs = whiteIncMs;
        this.blackTimeMs = blackStartMs;
        this.blackIncMs = blackIncMs;
    }

    public long getWhiteTimeMs() { return whiteTimeMs; }
    public long getBlackTimeMs() { return blackTimeMs; }

    public void spendTime(boolean isWhite, long elapsedMs) {
        if (isWhite) {
            whiteTimeMs -= elapsedMs;
            whiteTimeMs += whiteIncMs;
        } else {
            blackTimeMs -= elapsedMs;
            blackTimeMs += blackIncMs;
        }
    }

    public boolean isTimeUp(boolean isWhite) {
        return isWhite ? whiteTimeMs <= 0 : blackTimeMs <= 0;
    }
}
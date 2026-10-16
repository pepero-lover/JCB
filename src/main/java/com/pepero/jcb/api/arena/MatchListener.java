package com.pepero.jcb.api.arena;

public interface MatchListener {
    void onMovePlayed(MatchMoveEvent event);
    void onMatchFinished(MatchFinishedEvent event);
}
package com.pepero.jcb.api.uci;

import java.util.List;

/**
 * Engine analysis listener for {@link UCIEngineWrapper}
 */
public interface EngineAnalysisListener {
        default void onAnalysisBundled(List<EngineLine> bundledLines) {}
        default void onBestMoveFound(String bestMove) {}
        default void onEngineLog(String direction, String log) {}
        default void onEngineInfo(int depth, EngineCp score, String pv) {}
        default void onEngineCrashed(Throwable cause) {}
    }
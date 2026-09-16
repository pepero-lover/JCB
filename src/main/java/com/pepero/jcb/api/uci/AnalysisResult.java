package com.pepero.jcb.api.uci;

import com.pepero.jcb.api.ChessGame;

import java.util.List;

/**
 * Result of {@link UCIEngineWrapper#startAnalysisSync(ChessGame, int, long, long, long, long, int, long)}
 * the engine's actual bestmove and the full set of pv lines available at that moment.
 *
 * @param bestMove UCI bestmove string, always present
 * @param lines    all pv lines sorted by pvNumber. Might be empty if
 *                 bestmove arrived before any info line was parsed
 */
public record AnalysisResult(String bestMove, List<EngineLine> lines) {
}
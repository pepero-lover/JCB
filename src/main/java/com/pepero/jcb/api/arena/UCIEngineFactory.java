package com.pepero.jcb.api.arena;

import com.pepero.jcb.api.uci.UCIEngineWrapper;

public interface UCIEngineFactory {
    UCIEngineWrapper spawn(EngineConfig config);
}
package com.pepero.jcb.api.arena;

import com.pepero.jcb.api.uci.UCIEngineWrapper;

interface UCIEngineFactory {
    UCIEngineWrapper spawn(EngineConfig config);
}
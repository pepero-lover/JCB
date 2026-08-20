package com.pepero.jcb.api.arena;

import com.pepero.jcb.api.uci.UCIEngineWrapper;
import java.util.concurrent.atomic.AtomicBoolean;

public class CancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile UCIEngineWrapper activeEngine;

    public boolean isCancelled() {
        return cancelled.get();
    }

    // get active engine (get pondering engine)
    void setActiveEngine(UCIEngineWrapper engine) {
        this.activeEngine = engine;
    }

    // cancel the active engine
    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            UCIEngineWrapper engine = activeEngine;
            if (engine != null) {
                engine.stopAnalysis();
            }
        }
    }
}
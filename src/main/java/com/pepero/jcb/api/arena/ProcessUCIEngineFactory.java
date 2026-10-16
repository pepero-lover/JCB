package com.pepero.jcb.api.arena;

import com.pepero.jcb.api.exception.engine.UCIEngineException;
import com.pepero.jcb.api.uci.UCIEngineWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class ProcessUCIEngineFactory implements UCIEngineFactory {

    @Override
    public UCIEngineWrapper spawn(EngineConfig config) {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(config.command().toString());
        if (config.args() != null) {
            commandLine.addAll(config.args());
        }

        ProcessBuilder pb = new ProcessBuilder(commandLine);
        pb.directory(config.workingDirectory().toFile());

        UCIEngineWrapper result = new UCIEngineWrapper(pb, 100, null);

        if (config.uciOptions() != null) {
            for (Map.Entry<String, String> entry : config.uciOptions().entrySet()) {
                if(!result.hasOption(entry.getKey())) throw new UCIEngineException("Option \"" +
                        entry.getKey() + "\" not found!");
                result.setOptionSync(entry.getKey(), entry.getValue());
            }
        }

        result.newGame();

        return result;
    }
}
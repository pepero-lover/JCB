package com.pepero.jcb.api.arena;

import com.pepero.jcb.api.uci.UCIEngineWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProcessUCIEngineFactory implements UCIEngineFactory {

    @Override
    public UCIEngineWrapper spawn(EngineConfig config) {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(config.command());
        if (config.args() != null) {
            commandLine.addAll(config.args());
        }

        ProcessBuilder pb = new ProcessBuilder(commandLine);
        pb.directory(new File(config.workingDirectory()));

        UCIEngineWrapper result = new UCIEngineWrapper(pb, 100, null);

        if (config.uciOptions() != null) {
            for (Map.Entry<String, String> entry : config.uciOptions().entrySet()) {
                result.setOptionSync(entry.getKey(), entry.getValue());
            }
        }

        result.newGame();

        return result;
    }
}
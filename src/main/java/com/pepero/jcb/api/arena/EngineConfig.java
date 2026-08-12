package com.pepero.jcb.api.arena;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record EngineConfig(
        String name, // engine display name
        String command, // engine file dir
        String workingDirectory, // engine working dir
        List<String> args, // engine args
        Protocol protocol, // engine protocol (UCI)
        Map<String, String> uciOptions, // "Hash"=128, "Threads"=4 etc.
        EngineLimit limit // engine limits (time or depth)
) {
    public enum Protocol { UCI }
}
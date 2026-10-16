package com.pepero.jcb.api.arena;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record EngineConfig(
        String name, // engine display name
        Path command, // engine file path
        Path workingDirectory, // engine working dir (default: the engine file's folder)
        List<String> args, // engine args
        Protocol protocol, // engine protocol (UCI)
        Map<String, String> uciOptions, // "Hash"=128, "Threads"=4 etc.
        EngineLimit limit // engine limits (time or depth)
) {
    public enum Protocol { UCI }

    public EngineConfig {
        Objects.requireNonNull(command, "Engine command can not be null!");

        command = command.toAbsolutePath().normalize();
        workingDirectory = workingDirectory != null
                ? workingDirectory.toAbsolutePath().normalize()
                : command.getParent();

        args = args == null ? List.of() : List.copyOf(args);
        uciOptions = uciOptions == null ? Map.of() : Map.copyOf(uciOptions);
        protocol = protocol == null ? Protocol.UCI : protocol;
    }
}
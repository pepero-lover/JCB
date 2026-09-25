package com.pepero.jcb.api;

import com.pepero.jcb.api.pgn.PGNDatabaseIterator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PGNPublicBench {

    private static final int MAX_NODES = 1_000_000;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("usage: PGNPublicBench <file.pgn> [maxGames=10000] [warmup=3] [passes=7]");
            return;
        }
        Path file = Path.of(args[0]);
        int maxGames = args.length > 1 ? Integer.parseInt(args[1]) : 10_000;
        int warmup = args.length > 2 ? Integer.parseInt(args[2]) : 3;
        int passes = args.length > 3 ? Integer.parseInt(args[3]) : 7;

        // ---- dataset (I/O and unparsable games stay out of every timed region) ----
        List<String> good = new ArrayList<>();
        long chars = 0, nodes = 0;
        int dropped = 0;
        for (String game : readGames(file, maxGames)) {
            try {
                nodes += PGNGameAccessor.extractMainline(game, MAX_NODES, Integer.MAX_VALUE)
                        .mainlineMoveData().size();
                good.add(game);
                chars += game.length();
            } catch (RuntimeException e) {
                dropped++;
            }
        }
        String[] pgns = good.toArray(String[]::new);
        int games = pgns.length;

        System.out.printf("java %s | %d cpu | maxHeap %d MB%n",
                System.getProperty("java.version"), Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().maxMemory() >> 20);
        System.out.printf("%s: %d games (%d dropped), %.1f MB, %d nodes (%.1f/game, mainline only)%n%n",
                file.getFileName(), games, dropped, chars / 1e6, nodes, (double) nodes / games);

        // ---- benchmarks ----
        List<PGNBenchRunner.Result> results = new ArrayList<>();

        results.add(PGNBenchRunner.run("fromPGN", () -> pgns,
                p -> ChessGame.fromPGN(p, MAX_NODES).getZobristHash(), warmup, passes));

        results.add(PGNBenchRunner.run("getPGN",
                () -> Arrays.stream(pgns).map(p -> ChessGame.fromPGN(p, MAX_NODES)).toArray(ChessGame[]::new),
                g -> g.getPGN(MAX_NODES).length(), warmup, passes));

        // ---- report ----
        System.out.printf("%-10s %10s %10s %10s %8s %10s %9s %10s%n",
                "stage", "median ms", "min ms", "games/s", "MB/s", "us/game", "ns/node", "KB/game");
        for (PGNBenchRunner.Result r : results) {
            double sec = r.medianMs() / 1000.0;
            System.out.printf("%-10s %10.1f %10.1f %10.0f %8.1f %10.1f %9.0f %10.1f%n",
                    r.name(), r.medianMs(), r.minMs(),
                    games / sec, chars / 1e6 / sec,
                    r.medianMs() * 1000.0 / games, r.medianMs() * 1e6 / nodes,
                    r.medianAllocBytes() / 1024.0 / games);
        }

        // ---- behavior fingerprint (untimed) ----
        long fp = 17;
        for (String p : pgns) fp = fp * 31 + ChessGame.fromPGN(p, MAX_NODES).getPGN(MAX_NODES).hashCode();
        System.out.printf("%nfingerprint (load->export over all games): %016x   [sink %d]%n", fp, PGNBenchRunner.sink);
    }

    /** Streams up to maxGames raw PGN strings through the library's own {@link PGNDatabaseIterator}. */
    static List<String> readGames(Path file, int maxGames) {
        List<String> games = new ArrayList<>();
        try (PGNDatabaseIterator it = new PGNDatabaseIterator(file)) {
            while (games.size() < maxGames && it.hasNext()) games.add(it.next().rawPGN());
        }
        return games;
    }
}
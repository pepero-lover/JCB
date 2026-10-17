package com.pepero.jcb.api.convert;

import com.pepero.jcb.api.parse.ConvertStringMoveUtils;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.MoveGenerator;
import com.pepero.jcb.core.encode.EncodeMove;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Run main in the IDE; defaults to LAN -> SAN with LAN input.
 * Arguments: mode, repeats per sample, samples, input file, input format (lan/san).
 * Modes: lan-to-san, encoded-to-san, lan-to-data, san-to-data.
 */
public class ConvertStringMoveUtilsBenchmark {
    private enum Mode { LAN_TO_SAN, ENCODED_TO_SAN, LAN_TO_DATA, SAN_TO_DATA }
    private record Game(String[] lans, String[] sans, int[] moves) {}
    private static volatile long sink;

    public static void main(String[] args) throws IOException {
        Mode mode = args.length > 0 ? Mode.valueOf(args[0].toUpperCase(Locale.ROOT).replace('-', '_')) : Mode.LAN_TO_SAN;
        int repeats = args.length > 1 ? Integer.parseInt(args[1]) : 10;
        int samples = args.length > 2 ? Integer.parseInt(args[2]) : 7;
        Path input = Path.of(args.length > 3 ? args[3] : "random_games_benchmark.txt");
        String format = args.length > 4 ? args[4] : "lan";
        if (repeats <= 0 || samples <= 0) throw new IllegalArgumentException("Repeats and samples must be positive");
        if (!format.equalsIgnoreCase("lan") && !format.equalsIgnoreCase("san")) {
            throw new IllegalArgumentException("Input format must be lan or san");
        }
        List<Game> games = prepare(input, format.equalsIgnoreCase("lan"), mode);
        long movesPerPass = games.stream().mapToLong(game -> game.moves.length).sum();
        if (movesPerPass == 0) throw new IllegalArgumentException("No moves found in " + input);
        System.out.printf("Mode: %s, input: %s, games: %d, moves/pass: %d%n", mode, format, games.size(), movesPerPass);
        System.out.println("Preparation excluded. Timing includes board creation and replay. Lower ns/move is better.");
        System.out.println("Warming up JIT...");
        for (int i = 0; i < 5; i++) run(games, mode, repeats);
        long conversions = Math.multiplyExact(movesPerPass, repeats);
        double[] nsPerMove = new double[samples];
        for (int i = 0; i < samples; i++) {
            long start = System.nanoTime();
            run(games, mode, repeats);
            long elapsed = System.nanoTime() - start;
            nsPerMove[i] = (double) elapsed / conversions;
            System.out.printf(Locale.ROOT, "#%d: %.1f ns/move, %.3f M conversions/sec%n",
                    i + 1, nsPerMove[i], 1000.0 / nsPerMove[i]);
        }
        Arrays.sort(nsPerMove);
        double median = samples % 2 == 0
                ? (nsPerMove[samples / 2 - 1] + nsPerMove[samples / 2]) / 2 : nsPerMove[samples / 2];
        System.out.printf(Locale.ROOT, "Median: %.1f ns/move, %.3f M conversions/sec%n", median, 1000.0 / median);
        System.out.println("Checksum: " + sink);
    }

    private static List<Game> prepare(Path input, boolean lanInput, Mode mode) throws IOException {
        List<Game> games = new ArrayList<>();
        for (String line : Files.readAllLines(input)) {
            if (line.isBlank()) continue;
            String[] tokens = line.trim().split("\\s+");
            String[] lans = new String[tokens.length];
            String[] sans = mode == Mode.SAN_TO_DATA ? new String[tokens.length] : null;
            int[] moves = new int[tokens.length];
            Chessboard board = new Chessboard(Chessboard.start_position);
            for (int i = 0; i < tokens.length; i++) {
                int move = lanInput ? ConvertStringMoveUtils.lanToMoveData(board, tokens[i])
                        : ConvertStringMoveUtils.sanToMoveData(board, tokens[i]);
                moves[i] = move;
                lans[i] = lanInput ? tokens[i] : EncodeMove.moveToString(move);
                if (sans != null) sans[i] = lanInput ? ConvertStringMoveUtils.toSanString(board, move) : tokens[i];
                MoveGenerator.makeMove(board, move);
            }
            games.add(new Game(lans, sans, moves));
        }
        return games;
    }

    private static void run(List<Game> games, Mode mode, int repeats) {
        long checksum = 0;
        for (int r = 0; r < repeats; r++) {
            for (Game game : games) {
                Chessboard board = new Chessboard(Chessboard.start_position);
                for (int i = 0; i < game.moves.length; i++) {
                    int replayMove = game.moves[i];
                    switch (mode) {
                        case LAN_TO_SAN -> checksum += ConvertStringMoveUtils.toSanString(board, game.lans[i]).hashCode();
                        case ENCODED_TO_SAN -> checksum += ConvertStringMoveUtils.toSanString(board, replayMove).hashCode();
                        case LAN_TO_DATA -> {
                            replayMove = ConvertStringMoveUtils.lanToMoveData(board, game.lans[i]);
                            checksum += replayMove;
                        }
                        case SAN_TO_DATA -> {
                            replayMove = ConvertStringMoveUtils.sanToMoveData(board, game.sans[i]);
                            checksum += replayMove;
                        }
                    }
                    MoveGenerator.makeMove(board, replayMove);
                }
            }
        }
        sink = checksum;
    }
}

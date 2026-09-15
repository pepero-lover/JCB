package com.pepero.jcb.example;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.ChessGameListener;
import com.pepero.jcb.api.dto.MoveInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PuzzleTrainerExample {

    private record Puzzle(String id, String fen, List<String> moves, int rating, List<String> themes) {}

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java PuzzleTrainerExample <lichess-puzzle-csv-path> [count]");
            return;
        }

        int limit = args.length > 1 ? Integer.parseInt(args[1]) : 20;
        List<Puzzle> puzzles = loadPuzzles(Path.of(args[0]), limit);

        if (puzzles.isEmpty()) {
            System.out.println("No puzzles loaded from " + args[0]);
            return;
        }

        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        int solved = 0;

        for (Puzzle puzzle : puzzles) {
            System.out.println();
            System.out.println("=== Puzzle " + puzzle.id() + " (rating " + puzzle.rating() + ") ===");
            if (!puzzle.themes().isEmpty()) {
                System.out.println("Themes: " + String.join(", ", puzzle.themes()));
            }

            PuzzleOutcome outcome = solve(puzzle, stdin);

            switch (outcome) {
                case SOLVED -> {
                    solved++;
                    System.out.println("Solved!");
                }
                case SKIPPED -> System.out.println("Skipped.");
                case QUIT -> {
                    printSummary(solved, puzzles.size());
                    return;
                }
            }
        }

        printSummary(solved, puzzles.size());
    }

    private static void printSummary(int solved, int total) {
        System.out.println();
        System.out.println(solved + " / " + total + " solved.");
    }

    private enum PuzzleOutcome { SOLVED, SKIPPED, QUIT }

    private static List<Puzzle> loadPuzzles(Path csvPath, int limit) throws IOException {
        List<Puzzle> result = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            reader.readLine(); // header row

            String line;
            while (result.size() < limit && (line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                String[] cols = line.split(",", -1);
                if (cols.length < 4) continue;

                String id = cols[0];
                String fen = cols[1];
                List<String> moves = new ArrayList<>(Arrays.asList(cols[2].split(" ")));

                int rating;
                try {
                    rating = Integer.parseInt(cols[3].trim());
                } catch (NumberFormatException e) {
                    rating = 0;
                }

                List<String> themes = (cols.length > 7 && !cols[7].isBlank())
                        ? Arrays.asList(cols[7].split(" "))
                        : List.of();

                result.add(new Puzzle(id, fen, moves, rating, themes));
            }
        }

        return result;
    }

    private static PuzzleOutcome solve(Puzzle puzzle, BufferedReader stdin) throws IOException {
        ChessGame game = ChessGame.fromFEN(puzzle.fen);

        boolean[] autoPlaying = {false};
        boolean[] wasWrong = {false};
        int[] expectedIndex = {0};

        ChessGameListener listener = new ChessGameListener() {
            @Override
            public void onMoveMade(ChessGame source, MoveInfo moveInfo) {
                if (autoPlaying[0]) {
                    // this was our own auto-played setup/reply move, not a solver guess
                    autoPlaying[0] = false;
                    return;
                }

                String played = moveInfo.toLanString();
                String expected = puzzle.moves().get(expectedIndex[0]);

                if (!played.equalsIgnoreCase(expected)) {
                    wasWrong[0] = true;
                    source.unmakeMove();
                    return;
                }

                expectedIndex[0]++;
                if (expectedIndex[0] >= puzzle.moves().size()) {
                    return; // solution fully played, solve()'s loop will exit
                }

                // correct so far and more moves remain: auto-play the opponent's reply
                autoPlaying[0] = true;
                source.makeMoveLan(puzzle.moves().get(expectedIndex[0]));
                expectedIndex[0]++;
            }
        };

        game.addChessGameListener(listener);

        try {
            // Moves[0]: the opponent's setup move, not shown to the solver as a choice
            autoPlaying[0] = true;
            game.makeMoveLan(puzzle.moves().getFirst());
            expectedIndex[0] = 1;

            while (expectedIndex[0] < puzzle.moves().size()) {
                game.printBoard();
                System.out.println();
                System.out.print("Your move (LAN, e.g. e2e4 / e7e8q), 'skip' or 'quit': ");

                String input = stdin.readLine();
                if (input == null || input.equalsIgnoreCase("quit")) {
                    return PuzzleOutcome.QUIT;
                }
                if (input.equalsIgnoreCase("skip")) {
                    return PuzzleOutcome.SKIPPED;
                }

                wasWrong[0] = false;
                boolean applied = game.tryMakeMoveSan(input.trim());

                if (!applied) {
                    System.out.println("Illegal move, try again.");
                } else if (wasWrong[0]) {
                    System.out.println("Wrong move, try again.");
                }
            }

            return PuzzleOutcome.SOLVED;
        } finally {
            game.removeChessGameListener(listener);
        }
    }
}
package com.pepero.jcb.api.book;

import java.util.ArrayList;
import java.util.List;

public class PGNSplitter {

    private enum State { BEFORE_MOVES, IN_MOVES }

    public static List<String> splitGames(String pgnText) {
        List<String> games = new ArrayList<>();
        String[] lines = pgnText.split("\\R");

        StringBuilder cur = new StringBuilder();
        State state = State.BEFORE_MOVES;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                cur.append(line).append("\n");
                continue;
            }

            boolean isTagLine = trimmed.startsWith("[") && trimmed.endsWith("]");

            if (isTagLine && state == State.IN_MOVES) {
                games.add(cur.toString());
                cur = new StringBuilder();
                state = State.BEFORE_MOVES;
            }

            if (!isTagLine) {
                state = State.IN_MOVES;
            }

            cur.append(line).append("\n");
        }

        if (!cur.isEmpty()) {
            games.add(cur.toString());
        }

        return games;
    }
}
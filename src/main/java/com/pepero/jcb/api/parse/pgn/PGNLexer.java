package com.pepero.jcb.api.parse.pgn;

import com.pepero.jcb.api.dto.PGNToken;

public class PGNLexer {
    private final String pgn;
    private int pointer = 0;

    public PGNLexer(String pgn) {
        this.pgn = pgn;
    }

    public PGNToken nextToken() {
        skipWhitespace(); // when empty char is given on pointer

        if (pointer >= pgn.length()) return new PGNToken(TokenType.EOF, "");

        char token = pgn.charAt(pointer);

        // when variation
        if (token == '(') {
            pointer++;
            return new PGNToken(TokenType.VARIATION_START, "(");
        }
        if (token == ')') {
            pointer++;
            return new PGNToken(TokenType.VARIATION_END, ")");
        }

        // when comment
        if (token == '{') {
            int start = ++pointer;
            while (pointer < pgn.length() && pgn.charAt(pointer) != '}') {
                pointer++;
            }
            String comment = pgn.substring(start, pointer);
            if(pointer < pgn.length()) pointer++; // skip '}'
            return new PGNToken(TokenType.COMMENT, comment);
        }
        if (token == '}') {
            pointer++;
            return nextToken();
        }

        // when nag
        if (token == '$') {
            int start = pointer++;
            while (pointer < pgn.length() && Character.isDigit(pgn.charAt(pointer))) {
                pointer++;
            }
            return new PGNToken(TokenType.NAG, pgn.substring(start, pointer));
        }

        // comment with one line
        if (token == ';') {
            int start = ++pointer;
            while (pointer < pgn.length() && pgn.charAt(pointer) != '\n' && pgn.charAt(pointer) != '\r') pointer++;
            String comment = pgn.substring(start, pointer);
            return new PGNToken(TokenType.COMMENT, comment);
        }

        // when move string, result, move number, etc.
        int start = pointer;
        while (pointer < pgn.length() && !isTerminator(pgn.charAt(pointer))) {
            pointer++;
        }

        if (start == pointer) {
            pointer++;
            return nextToken();
        }

        String text = pgn.substring(start, pointer);

        return classifyToken(text);
    }

    private void skipWhitespace() {
        while (pointer < pgn.length() && Character.isWhitespace(pgn.charAt(pointer))) {
            pointer++;
        }
    }

    private boolean isTerminator(char c) {
        return Character.isWhitespace(c) || c == '(' || c == ')' ||
                c == '{' || c == '}' || c == '$' || c == ';' || c == '.';
    }

    private PGNToken classifyToken(String text) {
        // number like '1.' , '1...' ..
        if (text.matches("^\\d+\\.+$") || text.matches("^\\d+$") || text.equals(".")) {
            return new PGNToken(TokenType.NUMBER_INDICATOR, text);
        }

        // result
        if (text.equals("1-0") || text.equals("0-1") || text.equals("1/2-1/2") || text.equals("*")) {
            return new PGNToken(TokenType.RESULT, text);
        }

        // nag
        if (text.matches("^[!?]+$")) {
            return new PGNToken(TokenType.NAG, text);
        }

        return new PGNToken(TokenType.MOVE, text);
    }
}

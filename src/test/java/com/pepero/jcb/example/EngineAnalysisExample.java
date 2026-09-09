package com.pepero.jcb.example;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.exception.FENConvertException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class EngineAnalysisExample {
    public static void main(String[] args) throws IOException {
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));

        try {
            ChessGame chessGame = ChessGame.fromFEN(input.readLine());
        } catch (FENConvertException e) {
            System.err.println("Invalid FEN! (Cause : " + e.getErrorType() + ")");
            System.exit(0);
        }

    }
}

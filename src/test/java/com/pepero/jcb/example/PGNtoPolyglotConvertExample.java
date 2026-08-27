package com.pepero.jcb.example;

import com.pepero.jcb.api.book.PolyglotBookBuilder;

import java.io.IOException;

public class PGNtoPolyglotConvertExample {
    public static void main(String[] args) throws IOException {
        String inputPgn = "games.pgn";
        String outputBin = "opening.bin";
        int maxPly = 30; // 15 moves

        PolyglotBookBuilder.build(inputPgn, outputBin, maxPly);

        System.out.println("Opening book built successfully: " + outputBin);
    }
}

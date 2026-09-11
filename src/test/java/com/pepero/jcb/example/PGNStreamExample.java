package com.pepero.jcb.example;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.pgn.PGNDatabaseIterator;
import com.pepero.jcb.api.pgn.PGNGameStub;

import java.nio.file.Path;

public class PGNStreamExample {
    public static void main(String[] args) {
        // Stream games out of multi-gigabyte PGN files with minimal memory usage
        try (PGNDatabaseIterator iterator = new PGNDatabaseIterator(Path.of("large_database.pgn"))) {
            while (iterator.hasNext()) {
                PGNGameStub stub = iterator.next();

                // Access headers without parsing full movetext
                System.out.println("White: " + stub.headers().get("White"));
                System.out.println("Black: " + stub.headers().get("Black"));

                // Lazily parse movetext into a ChessGame instance when needed
                ChessGame game = stub.toChessGame();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
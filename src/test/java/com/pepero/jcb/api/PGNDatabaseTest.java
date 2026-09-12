package com.pepero.jcb.api;

import com.pepero.jcb.api.pgn.PGNDatabaseIterator;
import com.pepero.jcb.api.pgn.PGNGameStub;

import java.nio.file.Path;

public class PGNDatabaseTest {
    public static void main(String[] args) {
        try(PGNDatabaseIterator pgnDatabaseIterator = new PGNDatabaseIterator(Path.of("D:\\PGN\\lichess_rated_2016-07.pgn"))) {
            int games = 0;

            long startTime = System.currentTimeMillis();

            long time = System.currentTimeMillis();

            while (pgnDatabaseIterator.hasNext()) {
                games++;
                PGNGameStub gameStub = pgnDatabaseIterator.next();
                gameStub.toChessGame();
                if(games % 10000 == 0) {
                    long stopTime = System.currentTimeMillis();
                    System.out.println(games + " games searched...");
                    System.out.println(((stopTime - time) / 1000.) + " seconds");
                }
            }

            long stopTime = System.currentTimeMillis();

            System.out.println(games + " games searched.");
            System.out.println("Time elapsed : " + ((stopTime - startTime) / 1000.) + " seconds");
        }
    }
}

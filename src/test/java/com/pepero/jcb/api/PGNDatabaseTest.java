package com.pepero.jcb.api;

import com.pepero.jcb.api.pgn.PGNDatabaseIterator;
import com.pepero.jcb.api.pgn.PGNGameStub;

import java.nio.file.Path;

public class PGNDatabaseTest {
    public static void main(String[] args) {
        try(PGNDatabaseIterator pgnDatabaseIterator = new PGNDatabaseIterator(Path.of("pgn/GM_Games.pgn"))) {
            while (pgnDatabaseIterator.hasNext()) {
                PGNGameStub gameStub = pgnDatabaseIterator.next();
                System.out.println(gameStub.rawPGN());
                System.out.println();
                System.out.println();
                System.out.println();
                System.out.println("PGN END");
                System.out.println();
            }
        }
    }
}

package com.pepero.jcb.api;

import com.pepero.jcb.api.pgn.PGNDatabaseIterator;
import com.pepero.jcb.api.pgn.PGNGameStub;

import java.nio.file.Path;

public class PGNDatabaseTest {
    public static void main(String[] args) {
        try(PGNDatabaseIterator pgnDatabaseIterator = new PGNDatabaseIterator(Path.of("pgn/GM_Games.pgn"))) {
            while (pgnDatabaseIterator.hasNext()) {
                PGNGameStub gameStub = pgnDatabaseIterator.next();
                System.out.println(ChessGame.fromPGN(gameStub.rawPGN()).getPGN());
                System.out.println();
                System.out.println("Reconverted PGN END");
            }
        }
    }
}

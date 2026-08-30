package com.pepero.jcb.api;

import com.pepero.jcb.api.book.BookMoveSelector;
import com.pepero.jcb.api.book.PolyglotBookReader;

import java.io.IOException;

public class PolyglotTest {
    public static void main(String[] args) throws IOException {
        //PolyglotBookBuilder.build("engine/UHO.pgn", "engine/UHO.bin", 20);
        PolyglotBookReader reader = new PolyglotBookReader("engine/UHO.bin");

        ChessGame game = ChessGame.startPosition();
        reader.findMoves(game.getPolyglotHash());
        for(int i = 0; i < 20; i++) {
            String move = BookMoveSelector.pickBestMove(
                    reader.findMoves(game.getPolyglotHash())
            );
            if(move == null) break;
            game.makeMoveLan(move);
        }

        System.out.println(game.getPGN());
    }
}
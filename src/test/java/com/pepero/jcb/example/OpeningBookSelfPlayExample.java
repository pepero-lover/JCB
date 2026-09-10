package com.pepero.jcb.example;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.book.BookEntry;
import com.pepero.jcb.api.book.BookMoveSelector;
import com.pepero.jcb.api.book.PolyglotBookReader;
import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.api.uci.EngineLine;
import com.pepero.jcb.api.uci.UCIEngineWrapper;

import java.util.HashMap;
import java.util.List;

public class OpeningBookSelfPlayExample {
    public static void main(String[] args) throws InterruptedException {
        PolyglotBookReader bookReader = new PolyglotBookReader("engine/opening.bin");

        ChessGame chessGame = ChessGame.startPosition();

        UCIEngineWrapper engineWrapper = new UCIEngineWrapper(new ProcessBuilder(
                "engine/stockfish-19.exe"
        ), 100, null);

        boolean inBook = true;

        while (true) {
            chessGame.printBoard();

            if (chessGame.getGameOverReason() != GameOverReason.NOTGAMEOVER) {
                System.out.println(chessGame.getGameResult() + " - Game over by " + chessGame.getGameOverReason());
                break;
            }

            String moveLan = null;

            if (inBook) {
                long hash = chessGame.getPolyglotHash();
                List<BookEntry> entries = bookReader.findMoves(hash);

                if (!entries.isEmpty()) {
                    moveLan = BookMoveSelector.pickWeightedRandom(entries);
                    System.out.println("(book) " + moveLan);
                } else {
                    inBook = false;
                    System.out.println("(out of book, switching to engine)");
                }
            }

            if (moveLan == null) {
                engineWrapper.startAnalysis(chessGame, 255, 1);
                Thread.sleep(3000);
                engineWrapper.stopAnalysis();

                HashMap<Integer, EngineLine> pvLines = engineWrapper.getCurrentEngineLines();
                EngineLine best = pvLines.get(1);
                moveLan = best.pv().trim().split("\\s+")[0];
                System.out.println("(engine) " + moveLan);
            }

            if (!chessGame.tryMakeMoveLan(moveLan)) {
                System.out.println("Move rejected by ChessGame! (" + moveLan + ") stopping.");
                break;
            }
        }

        System.out.println(chessGame.getPGN());

        System.exit(0);
    }
}
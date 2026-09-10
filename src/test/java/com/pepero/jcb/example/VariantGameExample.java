package com.pepero.jcb.example;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.api.enums.PieceType;
import com.pepero.jcb.api.enums.Square;
import com.pepero.jcb.api.exception.IllegalMoveException;
import com.pepero.jcb.core.GameVariant;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class VariantGameExample {
    public static void main(String[] args) throws IOException {
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("Available variants:");
        GameVariant[] variants = GameVariant.values();
        for (int i = 0; i < variants.length; i++) {
            System.out.println("  " + i + ": " + variants[i]);
        }
        System.out.print("Pick a variant number: ");
        GameVariant variant = variants[Integer.parseInt(input.readLine().trim())];

        ChessGame chessGame = ChessGame.startPosition(variant);

        System.out.println("Commands: move <san>, drop <P>@<square> (e.g. N@f3), history, quit");

        while (true) {
            chessGame.printBoard();

            if (chessGame.getGameOverReason() != GameOverReason.NOTGAMEOVER) {
                System.out.println(chessGame.getGameResult() + " - Game over by " + chessGame.getGameOverReason());
                break;
            }

            System.out.print((chessGame.getTurn() ? "White" : "Black") + " > ");
            String line = input.readLine();
            if (line == null) break;
            line = line.trim();

            if (line.equalsIgnoreCase("quit")) break;

            if (line.equalsIgnoreCase("history")) {
                chessGame.printHistory(true);
                continue;
            }

            if (line.startsWith("drop ")) {
                String dropArg = line.substring(5).trim(); // e.g. "N@f3"
                String[] dropParts = dropArg.split("@");
                if (dropParts.length != 2) {
                    System.out.println("Format: drop <P>@<square>  (e.g. drop N@f3)");
                    continue;
                }

                PieceType pieceType = PieceType.fromChar(dropParts[0].trim().charAt(0));
                Square target = Square.fromString(dropParts[1].trim());

                if (!chessGame.canDropPiece(pieceType, target)) {
                    System.out.println("Illegal drop!");
                    continue;
                }

                try {
                    chessGame.makeDropMove(pieceType, target);
                } catch (IllegalMoveException e) {
                    System.out.println("Illegal drop: " + e.getMessage());
                }
                continue;
            }

            if (!chessGame.tryMakeMoveSan(line)) {
                System.out.println("Illegal or unparseable move: " + line);
            }
        }

        System.exit(0);
    }
}
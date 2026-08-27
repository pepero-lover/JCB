package com.pepero.jcb.example;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.api.exception.ConvertMoveException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class CLIChess {
    public static void main(String[] args) throws IOException {
        // Initialize chess game
        ChessGame chessGame = ChessGame.startPosition();

        BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            chessGame.printBoard();

            GameOverReason reason = chessGame.isGameOver();
            if (reason != GameOverReason.NOTGAMEOVER) {
                System.out.println("\n*** Game over! Reason: " + reason + " ***");
                System.out.println("Result: " + chessGame.getGameResult());
                System.out.println();
            }

            System.out.print(">>");
            String input = userInput.readLine();
            if (input == null || input.trim().isEmpty()) continue;

            String[] arg = input.trim().split("\\s+");
            String command = arg[0].toLowerCase();

            if(command.equals("u") || command.equals("undo")) { // if undo,
                if(chessGame.canUndo()) {
                    chessGame.unmakeMove();

                    continue;
                }

                System.err.println("Could not undo move!");
                System.out.println();

                continue;
            }

            if (command.equals("r") || command.equals("redo")) {
                if (arg.length == 1) { // mainline redo
                    if (chessGame.canRedo()) {
                        chessGame.remakeMove();
                    } else {
                        System.err.println("Could not redo mainline move!");
                    }
                } else { // variation redo
                    try {
                        int variationIndex = Integer.parseInt(arg[1]);
                        if (chessGame.canRedo(variationIndex)) {
                            chessGame.remakeMove(variationIndex);
                        } else {
                            System.err.println("Could not redo that variation index move!");
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("variation index should be integer!");
                    }
                }
                continue;
            }

            // print move history
            if(input.equals("movehistory") || input.equals("history") || input.equals("h")) {
                // move history with san
                System.out.println("\n[ Move history ]");
                chessGame.printHistory();
                System.out.println();
                continue;
            }

            if(command.equals("move")) {
                if (arg.length < 2) {
                    System.err.println("Please enter SAN move format!");
                    continue;
                }
                String san = arg[1];
                try {
                    // san move to MoveInfo class
                    MoveInfo moveInfo = chessGame.sanToMoveData(san);
                    chessGame.makeMove(moveInfo);
                } catch (ConvertMoveException e) {
                    System.err.println("Could not parse the SAN move!");
                }
                continue;
            }

            if(command.equals("moves")) {
                if (arg.length < 2) {
                    System.err.println("Please enter SAN move format!");
                    continue;
                }
                try {
                    for(int i = 1; i < arg.length; i++) {
                        String san = arg[i];

                        // get San data to MoveInfo
                        MoveInfo moveInfo = chessGame.sanToMoveData(san);
                        chessGame.makeMove(moveInfo);
                    }
                } catch (ConvertMoveException e) {
                    System.err.println("Could not parse the SAN move!");
                }
                continue;
            }

            // exit
            if(input.equals("exit")) {
                break;
            }

            System.err.println("Unknown command. (move, undo, redo, movehistory, exit)");
        }
    }
}

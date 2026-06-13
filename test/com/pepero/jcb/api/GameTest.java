package com.pepero.jcb.api;

import com.pepero.jcb.api.enums.GameMode;

import java.util.Scanner;

public class GameTest {
    public static void main(String[] args) {
        ChessGame chessGame = new ChessGame(GameMode.VARIATION);
        Scanner scanner = new Scanner(System.in);

        System.out.println(chessGame);

        while (true) {
            System.out.print("\n수를 입력하세요 >> ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("exit")) {
                break;
            }

            if (input.isEmpty()) continue;

            try {
                if (input.equalsIgnoreCase("u")) {
                    if (chessGame.canUndo()) {
                        chessGame.unmakeMove();
                    }
                } else if (input.equalsIgnoreCase("r")) {
                    if (chessGame.canRedo()) {
                        chessGame.remakeMove();
                    }
                } else {
                    chessGame.makeMove(input);
                }
            } catch (Exception e) {}

            System.out.println(chessGame);
        }

        scanner.close();
    }
}

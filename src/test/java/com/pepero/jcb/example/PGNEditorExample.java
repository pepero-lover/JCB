package com.pepero.jcb.example;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.dto.MoveDataDTO;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PGNEditorExample {
    public static void main(String[] args) throws IOException {
        Path path = Paths.get("pgn.txt");

        String inputPGN = Files.readString(path);

        ChessGame chessGame = ChessGame.fromPGN(inputPGN);

        BufferedReader bf = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            String input = bf.readLine();
            if (input == null) break; // EOF

            String[] arg = input.trim().split("\\s+");
            if (arg.length == 0 || arg[0].isEmpty()) continue;
            String firstToken = arg[0];

            try {
                if (firstToken.equals("tree")) {
                    chessGame.printHistory(true);
                    continue;
                }
                if (firstToken.equals("promote")) {
                    chessGame.promoteVariationLocal(Long.parseLong(arg[1]));
                }
                if (firstToken.equals("delete")) {
                    chessGame.deleteVariation(Long.parseLong(arg[1]));
                }
                if (firstToken.equals("jump")) {
                    chessGame.jumpToNode(Long.parseLong(arg[1]));
                }
                if (firstToken.equals("next")) {
                    chessGame.goForward();
                }
                if (firstToken.equals("prev")) {
                    chessGame.goBackward();
                }
                if (firstToken.equals("board")) {
                    chessGame.printBoard();
                    System.out.println();
                    for (MoveDataDTO mainlineNode : chessGame.getMainlineData()) {
                        System.out.print(mainlineNode.san() + " ");
                    }
                    System.out.println();
                }
                if(firstToken.equals("move")) {
                    chessGame.makeMoveSan(arg[1]);
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                System.out.println("id를 입력해주세요. 예: jump 12345");
            } catch (NumberFormatException e) {
                System.out.println("id는 숫자여야 합니다.");
            } catch (RuntimeException e) {
                System.out.println("에러: " + e.getMessage());
            }
        }
    }
}

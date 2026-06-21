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
        // 시작 포지션으로 ChessGame 겍체를 생성합니다.
        ChessGame chessGame = new ChessGame();

        BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));

        // 무한루프로 계속 사용자의 인풋을 받습니다.
        while (true) {
            chessGame.printBoard();

            GameOverReason reason = chessGame.isGameOver();
            if (reason != GameOverReason.NOTGAMEOVER) {
                System.out.println("\n*** 게임 종료! 사유: " + reason + " ***");
                System.out.println("결과: " + chessGame.getGameResult());
                System.out.println();
            }

            System.out.print(">>");
            String input = userInput.readLine();
            if (input == null || input.trim().isEmpty()) continue;

            String[] arg = input.trim().split("\\s+");
            String command = arg[0].toLowerCase();

            if(command.equals("u") || command.equals("undo")) { // 만약 언도를 할 경우
                if(chessGame.canUndo()) {
                    chessGame.unmakeMove();

                    continue;
                }

                System.err.println("수를 되돌릴 수 없습니다!");
                System.out.println();

                continue;
            }

            if (command.equals("r") || command.equals("redo")) {
                if (arg.length == 1) { // 메인 라인 되돌리기
                    if (chessGame.canRedo()) {
                        chessGame.remakeMove();
                    } else {
                        System.err.println("수를 앞으로 되돌릴 수 없습니다!");
                    }
                } else { // 바리에이션 되돌리기
                    try {
                        int variationIndex = Integer.parseInt(arg[1]);
                        if (chessGame.canRedo(variationIndex)) {
                            chessGame.remakeMove(variationIndex);
                        } else {
                            System.err.println("해당 바리에이션 번호로 되돌릴 수 없습니다!");
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("바리에이션 인덱스는 숫자여야 합니다!");
                    }
                }
                continue;
            }

            // 무브 히스토리 출력
            if(input.equals("movehistory") || input.equals("history") || input.equals("h")) {
                // san 을 포함한 무브 히스토리
                System.out.println("\n[ 기보 히스토리 ]");
                chessGame.printHistory();
                System.out.println();
                continue;
            }

            if(command.equals("move")) {
                if (arg.length < 2) {
                    System.err.println("SAN 기보를 입력해주세요! (예: move e4)");
                    continue;
                }
                String san = arg[1];
                try {
                    // San 무브를 무브 데이터로 가져옵니다.
                    MoveInfo encoded_data = chessGame.toLanMoveData(san);
                    chessGame.makeMove(encoded_data);
                } catch (ConvertMoveException e) {
                    System.err.println("SAN 무브를 해석할 수 없습니다!");
                }
                continue;
            }

            if(command.equals("moves")) {
                if (arg.length < 2) {
                    System.err.println("SAN 기보를 입력해주세요! (예: moves e4 e5)");
                    continue;
                }
                try {
                    for(int i = 1; i < arg.length; i++) {
                        String san = arg[i];

                        // San 무브를 무브 데이터로 가져옵니다.
                        MoveInfo encodedData = chessGame.toLanMoveData(san);
                        chessGame.makeMove(encodedData);
                    }
                } catch (ConvertMoveException e) {
                    System.err.println("SAN 무브를 해석할 수 없습니다!");
                }
                continue;
            }

            // 종료
            if(input.equals("exit")) {
                break;
            }

            System.err.println("알 수 없는 명령어입니다. (move, undo, redo, movehistory, exit)");
        }
    }
}

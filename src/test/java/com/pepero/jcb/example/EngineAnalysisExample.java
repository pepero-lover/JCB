package com.pepero.jcb.example;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.api.exception.FENConvertException;
import com.pepero.jcb.api.exception.IllegalMoveException;
import com.pepero.jcb.api.parse.ConvertStringMoveUtils;
import com.pepero.jcb.api.uci.EngineLine;
import com.pepero.jcb.api.uci.UCIEngineWrapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class EngineAnalysisExample {
    public static void main(String[] args) throws IOException, InterruptedException {
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));

        ChessGame chessGame;

        System.out.print("Enter position FEN (if fails, starts with default start position) : ");

        try {
            chessGame = ChessGame.fromFEN(input.readLine());
        } catch (FENConvertException e) {
            System.err.println("Invalid FEN! (Cause : " + e.getErrorType() + ")");
            System.err.println("Starts with start FEN.");
            chessGame = ChessGame.startPosition();
        }

        UCIEngineWrapper engineWrapper = new UCIEngineWrapper(new ProcessBuilder(
                "engine/stockfish-19.exe"
        ), 100, null);

        while (true) {
            chessGame.printBoard();

            if(chessGame.getGameOverReason() != GameOverReason.NOTGAMEOVER) {
                System.out.println(chessGame.getGameResult().toString().toLowerCase() + "!  Game overed by " +
                        chessGame.getGameOverReason().toString().toLowerCase());
                break;
            }

            engineWrapper.startAnalysis(chessGame, 255, 5);
            Thread.sleep(5000);
            engineWrapper.stopAnalysis();

            HashMap<Integer, EngineLine> pvLines = engineWrapper.getCurrentEngineLines();
            List<EngineLine> lines = new ArrayList<>(pvLines.values());
            lines.sort((a, b) -> Integer.compare(a.pvNumber(), b.pvNumber()));

            for (EngineLine line : lines) {
                String coloredSan = ConvertStringMoveUtils.toUnicodePieces(
                        chessGame.getTurn(), line.sanPv());
                System.out.printf("[%d] (%s) %s%n", line.pvNumber(), line.score(), coloredSan);
            }

            System.out.print("Select line number (or 'q' to quit): ");
            String lineChoice = input.readLine();
            if ("q".equalsIgnoreCase(lineChoice)) break;

            EngineLine chosen = pvLines.get(Integer.parseInt(lineChoice.trim()));
            if (chosen == null) {
                System.out.println("Invalid line number.");
                continue;
            }

            System.out.print("How many plies to play (max " +
                    chosen.pv().trim().split("\\s+").length + "): ");
            int plyCount = Integer.parseInt(input.readLine().trim());

            List<MoveInfo> moveInfos = chosen.pvMoveList().subList(0, plyCount);

            try {
                chessGame.makeMoveAll(moveInfos);
            } catch (IllegalMoveException e) {
                System.err.println("Illegal move in sequence: " + e.getMessage());
            }
        }

        System.exit(0);
    }
}
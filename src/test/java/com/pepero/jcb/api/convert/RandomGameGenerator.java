package com.pepero.jcb.api.convert;

import com.pepero.jcb.api.parse.ConvertStringMoveUtils;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.MoveGenerator;
import com.pepero.jcb.core.constant.MoveCache;
import com.pepero.jcb.core.encode.EncodeMove;
import com.pepero.jcb.api.ChessGame;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * Generates random-play games and saves them as SAN sequences,
 * for later use as a realistic benchmark corpus.
 */
public class RandomGameGenerator {
    public static void main(String[] args) throws IOException {
        int gameCount = 2000;
        int maxPly = 80;
        Random random = new Random(1111);

        boolean lanMode = true;

        StringBuilder allGames = new StringBuilder();

        for (int g = 0; g < gameCount; g++) {
            ChessGame game = ChessGame.startPosition();
            Chessboard board = game.getBoardSnapshot();

            for (int ply = 0; ply < maxPly; ply++) {
                int[] moveList = new int[MoveCache.MAX_MOVE_SIZE];
                int moveCount = MoveGenerator.generateMoves(board, moveList);
                if (moveCount == 0) break;

                int pick = random.nextInt(moveCount);
                int moveData = moveList[pick];

                String result;
                if(lanMode) {
                    result = EncodeMove.moveToString(moveData);
                } else {
                    result = ConvertStringMoveUtils.toSanString(board, moveData);
                }

                MoveGenerator.makeMove(board, moveData);
                allGames.append(result).append(" ");
            }
            allGames.append("\n");
        }

        Files.writeString(Path.of("random_games_benchmark.txt"), allGames.toString());
        System.out.println("Generated " + gameCount + " random games.");
    }
}
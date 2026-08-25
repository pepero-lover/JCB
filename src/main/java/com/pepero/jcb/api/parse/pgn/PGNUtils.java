package com.pepero.jcb.api.parse.pgn;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.dto.MoveNodeDTO;
import com.pepero.jcb.api.enums.GameResult;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class PGNUtils {
    /**
     * Export PGNGame to pgn string
     *
     * @param chessGame chess pgn
     * @param pgn PGN Game data
     * @param isPure contain commentary, clk, nag, etc.
     * @return Exported pgn string
     */
    public static String export(ChessGame chessGame, PGNGame pgn, boolean isPure) {
        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, String> entry : pgn.headers().entrySet()) {
            sb.append("[").append(entry.getKey()).append(" \"")
                    .append(entry.getValue()).append("\"]\n");
        }
        sb.append("\n");

        String[] spilt_startFEN = chessGame.getStartPositionFEN().split(" ");

        if (pgn.rootNode() != null && pgn.rootNode().children() != null && !pgn.rootNode().children().isEmpty()) {
            buildMoveText(pgn.rootNode().children(), sb, spilt_startFEN[1].equals("w"),
                    Integer.parseInt(spilt_startFEN[5]) / 2 + 1, true, isPure);
        }

        sb.append(" ").append(getGameResultString(pgn.matchResult()));

        return sb.toString().replaceAll(" +", " ").trim();
    }

    public static String getGameResultString(GameResult gameResult) {
        if (gameResult == null) return "*";
        return switch (gameResult) {
            case WHITE_WON -> "1-0";
            case BLACK_WON -> "0-1";
            case DRAW -> "1/2-1/2";
            case UNKNOWN, ABORTED -> "*";
        };
    }

    /**
     * Build pgn
     *
     * @param siblings move node children
     * @param sb string pgn
     * @param isWhite is white turn
     * @param moveNumber move number like 1. 1...
     * @param forceMoveNumber if move is variation (like 1. e4 ("1." d4))
     * @param isPure contain commentary, clk, etc.
     */
    private static void buildMoveText(List<MoveNodeDTO> siblings, StringBuilder sb, boolean isWhite,
                                      int moveNumber, boolean forceMoveNumber, boolean isPure) {
        if (siblings == null || siblings.isEmpty()) return;

        MoveNodeDTO mainMove = siblings.getFirst();

        if (isWhite) {
            sb.append(moveNumber).append(". ");
        } else if (forceMoveNumber) {
            sb.append(moveNumber).append("... ");
        }
        sb.append(mainMove.san()).append(" ");

        boolean interrupted = false;

        if (mainMove.annotation().nag() != null && !isPure) {
            sb.append(mainMove.annotation().nag()).append(" ");
        }

        boolean hasComment = mainMove.annotation().comment() != null && !mainMove.annotation().comment().isEmpty();
        boolean hasClk = mainMove.annotation().clk() != null && !mainMove.annotation().clk().isEmpty();
        boolean hasEval = mainMove.annotation().eval() != null && !mainMove.annotation().eval().isEmpty();
        boolean hasTimestamp = mainMove.annotation().timeStamp() != null &&
                !mainMove.annotation().timeStamp().isEmpty();

        if ((hasComment || hasClk || hasEval || hasTimestamp) && !isPure) {
            StringJoiner innerContent = new StringJoiner(" ");

            if (hasClk) {
                innerContent.add("[%clk " + mainMove.annotation().clk() + "]");
            }
            if (hasTimestamp) {
                innerContent.add("[%timestamp " + mainMove.annotation().timeStamp() + "]");
            }
            if (hasEval) {
                innerContent.add("[%eval " + mainMove.annotation().eval() + "]");
            }
            if (hasComment) {
                innerContent.add(mainMove.annotation().comment());
            }

            sb.append("{").append(innerContent).append("} ");
            interrupted = true;
        }

        for (int i = 1; i < siblings.size(); i++) {
            sb.append("( ");
            buildMoveText(List.of(siblings.get(i)), sb, isWhite, moveNumber, true, isPure);
            sb.append(") ");
            interrupted = true;
        }

        if (mainMove.children() != null && !mainMove.children().isEmpty()) {
            boolean nextIsWhite = !isWhite;
            int nextMoveNumber = isWhite ? moveNumber : moveNumber + 1;
            boolean nextForceNumber = !nextIsWhite && interrupted;

            buildMoveText(mainMove.children(), sb, nextIsWhite, nextMoveNumber, nextForceNumber, isPure);
        }
    }
}
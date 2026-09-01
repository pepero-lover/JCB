package com.pepero.jcb.api;

import com.pepero.jcb.api.dto.MoveAnnotationDTO;
import com.pepero.jcb.api.dto.MoveNodeDTO;
import com.pepero.jcb.api.enums.GameResult;
import com.pepero.jcb.api.exception.NodesOverflowException;
import com.pepero.jcb.api.parse.ConvertStringMoveUtils;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.ChessboardUtils;
import com.pepero.jcb.core.GameVariant;
import com.pepero.jcb.core.MoveGenerator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Export {@link ChessGame} class to PGN string. <br>
 * This class doesn't have toPGN() method, if you want to parse the PGN string on {@link ChessGame} class, go to
 * {@link ChessGame#getPGN()}.
 */
class PGNExporter {
    static PGNGame createPGNGame(
            Map<String, String> originalHeaders,
            String startFen,
            GameVariant variant,
            boolean isChess960,
            GameResult result,
            MoveNode rootNode,
            int maxNodes) {
        LinkedHashMap<String, String> pgnHeaders = new LinkedHashMap<>(originalHeaders);

        if (isChess960) pgnHeaders.put("Variant", "Chess960");

        if (variant != GameVariant.STANDARD) {
            switch (variant) {
                case CRAZY_HOUSE -> pgnHeaders.put("Variant", "Crazyhouse");
                case THREE_CHECK -> pgnHeaders.put("Variant", "Three-check");
                case KING_OF_THE_HILL -> pgnHeaders.put("Variant", "King of the Hill");
                case HORDE -> pgnHeaders.put("Variant", "Horde");
                case GIVEAWAY, SUICIDE -> pgnHeaders.put("Variant", "Antichess");
                case ATOMIC -> pgnHeaders.put("Variant", "Atomic");
                case RACING_KINGS -> pgnHeaders.put("Variant", "Racing Kings");
            }
            if (variant == GameVariant.SUICIDE) {
                pgnHeaders.put("RuleVariants", "FICS");
            }
        }

        if (!startFen.equals(ChessboardUtils.getDefaultStartPosition(variant))) {
            pgnHeaders.put("SetUp", "1");
            pgnHeaders.put("FEN", startFen);
        } else {
            pgnHeaders.remove("SetUp");
            pgnHeaders.remove("FEN");
        }

        pgnHeaders.put("Result", getGameResultString(result));

        Chessboard tempBoard = new Chessboard(startFen);
        tempBoard.gameVariant = variant;
        tempBoard.isChess960 = isChess960;

        MoveNodeDTO rootDTO = buildPGNTreeWithSan(rootNode, tempBoard, maxNodes, new int[1]);

        return new PGNGame(pgnHeaders, rootDTO, result);
    }

    /**
     * Add san move on pgn tree
     *
     * @param node root node
     * @param tempBoard board
     * @param maxNodesCount max nodes count
     * @param currentNodes current nodes (default : new int[1])
     * @return root node
     *
     * @throws NodesOverflowException if move count is more than maxNodesCount
     */
    static MoveNodeDTO buildPGNTreeWithSan(MoveNode node, Chessboard tempBoard, int maxNodesCount,
                                           int[] currentNodes) {
        currentNodes[0]++;
        if (maxNodesCount < currentNodes[0]) throw new NodesOverflowException(
                "This pgn's node (move) count is more than max nodes count! (Max node count : " + maxNodesCount + ")"
        );

        String calculatedSan = null;
        boolean moved = node.moveData != null;

        if (moved) {
            calculatedSan = ConvertStringMoveUtils.toSanString(tempBoard, node.moveData);
            MoveGenerator.makeMove(tempBoard, node.moveData.originEncodedData());
        }

        List<MoveNodeDTO> childrenDTOs = new java.util.ArrayList<>(node.children.size());
        for (MoveNode child : node.children) {
            childrenDTOs.add(buildPGNTreeWithSan(child, tempBoard, maxNodesCount, currentNodes));
        }

        if (moved) {
            MoveGenerator.unmakeMove(tempBoard, node.moveData.originEncodedData());
        }

        MoveAnnotation nodeAnnotation = node.getAnnotation();
        MoveAnnotationDTO annotationDTO = new MoveAnnotationDTO(nodeAnnotation.comment,
                nodeAnnotation.nag, nodeAnnotation.clk, nodeAnnotation.timeStamp,
                nodeAnnotation.eval, nodeAnnotation.csl, nodeAnnotation.cal);

        return new MoveNodeDTO(
                node.id,
                node.ply,
                node.fullMovePly,
                node.moveData,
                childrenDTOs,
                calculatedSan,
                annotationDTO);
    }


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

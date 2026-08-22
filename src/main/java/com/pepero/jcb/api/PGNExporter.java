package com.pepero.jcb.api;

import com.pepero.jcb.api.dto.MoveAnnotationDTO;
import com.pepero.jcb.api.dto.MoveNodeDTO;
import com.pepero.jcb.api.dto.PGNGame;
import com.pepero.jcb.api.enums.GameResult;
import com.pepero.jcb.api.exception.NodesOverflowException;
import com.pepero.jcb.api.parse.ConvertStringMoveUtils;
import com.pepero.jcb.api.parse.pgn.MoveAnnotation;
import com.pepero.jcb.api.parse.pgn.PGNUtils;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.GameVariant;
import com.pepero.jcb.core.MoveGenerator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.pepero.jcb.api.PGNParser.getDefaultStartPosition;

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

        if (!startFen.equals(getDefaultStartPosition(variant))) {
            pgnHeaders.put("SetUp", "1");
            pgnHeaders.put("FEN", startFen);
        } else {
            pgnHeaders.remove("SetUp");
            pgnHeaders.remove("FEN");
        }

        pgnHeaders.put("Result", PGNUtils.getGameResultString(result));

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

        return new MoveNodeDTO(node.id, node.moveData, childrenDTOs, calculatedSan, annotationDTO);
    }
}

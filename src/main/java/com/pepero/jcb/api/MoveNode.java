package com.pepero.jcb.api;

import com.pepero.jcb.api.dto.MoveAnnotationDTO;
import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.dto.MoveNodeDTO;
import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.api.enums.GameResult;
import com.pepero.jcb.api.parse.pgn.MoveAnnotation;

import java.util.ArrayList;
import java.util.List;

class MoveNode {
    final long id;
    final MoveNode parent;
    final List<MoveNode> children = new ArrayList<>();
    final MoveInfo moveData;
    String san;

    MoveAnnotation annotation = null;

    // for external
    GameResult terminalResult = null;
    GameOverReason terminalReason = null;

    // cache game state
    boolean isStateEvaluated = false;
    GameResult calculatedResult = GameResult.UNKNOWN;
    GameOverReason calculatedReason = GameOverReason.NOTGAMEOVER;

    public MoveNode(long id) {
        this.id = id;
        this.moveData = null;
        this.parent = null;
    }

    public MoveNode(MoveInfo moveData, MoveNode parent, long id) {
        this.id = id;
        this.moveData = moveData;
        this.parent = parent;
    }

    public MoveAnnotation getAnnotation() {
        if (this.annotation == null) {
            this.annotation = new MoveAnnotation();
        }
        return this.annotation;
    }

    @Override
    public String toString() {
        String dataStr = (moveData == null) ? "ROOT" : moveData.toString();
        return dataStr + " -> " + children;
    }

    /**
     * Convert MoveNode to MoveNodeDTO
     *
     * @return converted MoveNodeDTO
     */
    public MoveNodeDTO convertToDTO() {
        List<MoveNodeDTO> childDTOs = this.children.stream()
                .map(MoveNode::convertToDTO)
                .toList();

        MoveAnnotationDTO annotationDTO = null;
        if (this.getAnnotation() != null) {
            MoveAnnotation anno = this.getAnnotation();
            annotationDTO = new MoveAnnotationDTO(
                    anno.comment, anno.nag, anno.clk, anno.timeStamp,
                    anno.eval, anno.csl, anno.cal
            );
        }

        return new MoveNodeDTO(
                this.id,
                this.moveData,
                childDTOs,
                this.san,
                annotationDTO
        );
    }

    /**
     * Get last main line node
     * <p>
     * Example : <br>
     * e4 e5 Nf3 Nc6 (Nf6 Nxe5) 'Bc4' <br>
     * and the result is Bc4
     *
     * @return last main line node
     */
    public MoveNode getLastMainlineNode() {
        return getLastMainlineNode(this);
    }

    /**
     * Get last main line node
     * <p>
     * Example : <br>
     * e4 e5 Nf3 Nc6 (Nf6 Nxe5) 'Bc4' <br>
     * and the result is Bc4
     *
     * @param startNode start root node
     *
     * @return last main line node
     */
    private MoveNode getLastMainlineNode(MoveNode startNode) {
        MoveNode lastNode = startNode;

        while (!lastNode.children.isEmpty()) {
            lastNode = lastNode.children.getFirst();
        }

        return lastNode;
    }
}
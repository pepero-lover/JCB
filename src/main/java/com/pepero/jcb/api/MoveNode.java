package com.pepero.jcb.api;

import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.api.enums.GameResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Move node class for storing move data on history tree.
 */
class MoveNode {
    final long id;
    final MoveNode parent;
    final List<MoveNode> children = new ArrayList<>();
    final MoveInfo moveData;

    final int ply;
    final int fullMovePly;

    MoveAnnotation annotation = null;

    // for external
    GameResult terminalResult = null;
    GameOverReason terminalReason = null;

    // cache game state
    boolean isStateEvaluated = false;
    GameResult calculatedResult = GameResult.UNKNOWN;
    GameOverReason calculatedReason = GameOverReason.NOTGAMEOVER;

    // cache san, fen
    String cachedSan;
    String cachedFen;

    /**
     * Constructor for root node <br>
     * the default start 'ply' is 0
     *
     * @param id id
     * @param fullMovePly full move ply data
     */
    public MoveNode(long id, int fullMovePly) {
        this.id = id;
        this.moveData = null;
        this.parent = null;
        this.ply = 0;
        this.fullMovePly = fullMovePly;
    }

    /**
     * Constructor for any node except root node
     *
     * @param moveData move data
     * @param parent parent data
     * @param id id
     * @param ply ply data
     * @param fullMovePly full move ply data
     */
    public MoveNode(MoveInfo moveData, MoveNode parent, long id, int ply, int fullMovePly) {
        this.id = id;
        this.moveData = moveData;
        this.parent = parent;

        this.ply = ply;
        this.fullMovePly = fullMovePly;
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

    /**
     * Get the distance between this node and root node.
     */
    public int depthOf() {
        return ply;
    }
}
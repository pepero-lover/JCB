package com.pepero.jcb.api.exception;

public class NodesOverflowException extends RuntimeException {
    private final long maxNodesCount;

    public NodesOverflowException(long maxNodesCount) {
        super("This node (move) count is more than max nodes count! (Max node count : " + maxNodesCount + ")");
        this.maxNodesCount = maxNodesCount;
    }

    public long getMaxNodesCount() {
        return maxNodesCount;
    }
}

package com.pepero.jcb.api;

import java.util.function.Function;

public class FeatureTest {
    public static void main(String[] args) {
        ChessGame game = ChessGame.startPosition();
        game.makeMoveSanAll("e4 e5 Nf3 Nc6 Bc4 Bc5 c3 Nf6 d3 d6");
        game.printBoard();

        record Snapshot(String fen, long hash, int moveCount) {
            @Override
            public String toString() {
                return "Snapshot{" +
                        "fen='" + fen + '\'' +
                        ", hash=" + hash +
                        ", moveCount=" + moveCount +
                        '}';
            }
        }

        Snapshot snapshot = game.read((Function<ChessGameReadView, Snapshot>) v ->
                new Snapshot(v.getFEN(), v.getZobristHash(), v.getLegalMoves().size()));
    }
}

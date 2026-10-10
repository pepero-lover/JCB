package com.pepero.jcb.api;

import com.pepero.jcb.core.ChessboardUtils;
import com.pepero.jcb.core.GameVariant;

public class FeatureTest {
    public static void main(String[] args) {
        ChessGame game = ChessGame.startPosition(GameVariant.CRAZY_HOUSE);
        ChessboardUtils.printChessBoard(game.getFEN());
    }
}

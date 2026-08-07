package com.pepero.jcb.api.tactic;

import com.pepero.jcb.api.analyze.TacticAnalyzer;
import com.pepero.jcb.api.analyze.TacticFinding;
import com.pepero.jcb.api.enums.Square;
import com.pepero.jcb.core.Chessboard;

import java.util.List;

public class TacticTest {
    public static void main(String[] args) {
        Chessboard chessboard = new Chessboard("6k1/8/3Q4/1R6/2b5/5K2/8/3R4 w - - 0 1 ");
        List<Square> tactics = TacticAnalyzer.findHangingPieces(chessboard, false);
        System.out.println(tactics);
    }
}

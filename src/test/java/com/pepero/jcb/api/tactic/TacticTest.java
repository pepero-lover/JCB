package com.pepero.jcb.api.tactic;

import com.pepero.jcb.api.analyze.TacticAnalyzer;
import com.pepero.jcb.api.analyze.TacticFinding;
import com.pepero.jcb.core.Chessboard;

import java.util.List;

public class TacticTest {
    public static void main(String[] args) {
        Chessboard chessboard = new Chessboard("2k1R3/8/8/4q3/8/8/4R3/5K2 b - - 0 1");
        List<TacticFinding> tactics = TacticAnalyzer.analyze(chessboard, true);
        System.out.println(tactics);
    }
}

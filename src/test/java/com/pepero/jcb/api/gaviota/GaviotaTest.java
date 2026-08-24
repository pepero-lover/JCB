package com.pepero.jcb.api.gaviota;

import com.pepero.jcb.core.Chessboard;

import java.nio.file.Path;

public class GaviotaTest {
    public static void main(String[] args) {
        GaviotaTablebase tb = new GaviotaTablebase(Path.of("gaviota/"));
        Chessboard chessboard = new Chessboard("K7/N7/k7/8/3p4/8/N7/8 w - - 0 1");
        System.out.println(tb.probeDtm(chessboard));
    }
}

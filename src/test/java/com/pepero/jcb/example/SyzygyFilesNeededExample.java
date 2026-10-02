package com.pepero.jcb.example;

import com.pepero.jcb.api.exception.tablebase.TablebaseMissingFileException;
import com.pepero.jcb.api.syzygy.SyzygyTablebase;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.ChessboardUtils;

import java.nio.file.Path;

public class SyzygyFilesNeededExample {
    public static void main(String[] args) {
        // get syzygy folder and load
        SyzygyTablebase tb = new SyzygyTablebase(Path.of("syzygy/"), 7);

        // 7 piece position example
        Chessboard board = new Chessboard("3k4/3n1q2/8/8/8/8/2NBR3/4K3 w - - 0 1");

        ChessboardUtils.printChessBoard(board);


        // print required material sets to probe this position
        System.out.println(SyzygyTablebase.requiredMaterials(
                board
        ));

        // print missing tables
        System.out.println(tb.findMissingTables(
                board
        ));

        System.out.println(SyzygyTablebase.requiredMaterials(16).size());

        System.out.println("--------------------------");
        System.out.println("Showing exception message");
        System.out.println("--------------------------");

        try {
            tb.getWdlData(board);
        } catch (TablebaseMissingFileException e) {
            System.err.println(e.getMessage());
        }
    }
}

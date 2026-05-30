package com.pepero.jcb;

import com.pepero.jcb.convertstr.ConvertStringMoveUtils;
import com.pepero.jcb.core.ChessBoardUtils;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.Initializer;
import com.pepero.jcb.core.MoveGenerator;

import java.io.IOException;
import java.util.Scanner;

public class Main {
    // This project actually copied the ChessProgramming's code
    //
    // To see the original video, check out this.
    // https://www.youtube.com/watch?v=QUNP-UjujBM&list=PLmN0neTso3Jxh8ZIylk74JpwfiWNI76Cs

    // FEN debug positions
    public static String empty_board = "8/8/8/8/8/8/8/8 w - - ";
    public static String start_position = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 ";
    public static String tricky_position = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1 ";
    public static String killer_position = "rnbqkb1r/pp1p1pPp/8/2p1pP2/1P1P4/3P3P/P1P1P3/RNBQKBNR w KQkq e6 0 1";
    public static String cmk_position = "r2q1rk1/ppp2ppp/2n1bn2/2b1p3/3pP3/3P1NPP/PPP1NPB1/R1BQ1RK1 b - - 0 9 ";

    public static void main(String[] args) throws IOException {
        // init all
        Initializer.init();

        // for debugging
        Scanner scanner = new Scanner(System.in);

        // parse custom FEN string
        Chessboard chessboard = new Chessboard(start_position);
    }
}

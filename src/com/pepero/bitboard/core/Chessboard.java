package com.pepero.bitboard.core;

import com.pepero.bitboard.constant.BoardSquares;

import java.util.Arrays;

import static com.pepero.bitboard.constant.BoardSquares.*;

public class Chessboard {

    // start pos
    public static String start_position = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 ";

    // define bitboards
    public long[] bitboards = new long[12];

    /*
    define occupancy bitboards
    the reason why the array's size is 3 is that it has white state, black state,
     and merged white and black (all) state
    */
    public long[] occupancies = new long[3];

    // side to move
    // 0 equals to white
    // 1 equals to black
    public int side;

    // enpassant
    // init to no square
    public int enpassant = BoardSquares.no_sq;

    /*
       bin  dec

      0001    1   white king can castle to the king side
      0010    2   white king can castle to the queen side
      0100    4   black king can castle to the king side
      1000    8   black king can castle to the queen side

      examples

      1111        both sides a castle both directions
      1001        black king is able to castle to queen side
                  white king is able to castle to king side
     */

    // castling rights
    public int castle;


    /**********************************\
     ==================================

     History Stack (for takeBack)

     ==================================
     \**********************************/

    // maximum search depth (Perft & Search)
    public static final int MAX_DEPTH = 128;

    // preserve board state arrays (stack)
    private long[][] bitboards_copy = new long[MAX_DEPTH][12];
    private long[][] occupancies_copy = new long[MAX_DEPTH][3];
    private int[] side_copy = new int[MAX_DEPTH];
    private int[] enpassant_copy = new int[MAX_DEPTH];
    private int[] castle_copy = new int[MAX_DEPTH];

    // ply counter for history stack
    private int copy_ply = 0;


    /**********************************\
     ==================================

     Class Constructors

     ==================================
     \**********************************/

    public Chessboard() {
        resetBoard();
    }

    public Chessboard(String fen) {
        ChessBoardUtils.parseFen(this, fen);
    }

    public Chessboard(Chessboard chessboard) {
        System.arraycopy(chessboard.bitboards, 0, this.bitboards, 0, bitboards.length);
        System.arraycopy(chessboard.occupancies, 0, this.occupancies, 0, occupancies.length);

        this.side = chessboard.side;
        this.enpassant = chessboard.enpassant;
        this.castle = chessboard.castle;
    }


    /**********************************\
     ==================================

     Board Methods

     ==================================
     \**********************************/

    public void resetBoard() {
        // reset board position and state variables
        Arrays.fill(this.bitboards, 0L);

        // reset occupancies (bitboards)
        Arrays.fill(this.occupancies, 0L);

        // reset game state variables
        this.side = 0;
        this.enpassant = no_sq;
        this.castle = 0;

        // reset history ply pointer
        this.copy_ply = 0;
    }

    public void setStartPos() {
        // reset the original board
        resetBoard();

        // set start position with fen
        ChessBoardUtils.parseFen(this, start_position);
    }

    /**
     * Preserve board state (Push into History Stack)
     */
    public void copyBoard() {
        // copy current state into the history stack at current ply
        System.arraycopy(this.bitboards, 0, bitboards_copy[copy_ply], 0, 12);
        System.arraycopy(this.occupancies, 0, occupancies_copy[copy_ply], 0, 3);

        side_copy[copy_ply] = this.side;
        enpassant_copy[copy_ply] = this.enpassant;
        castle_copy[copy_ply] = this.castle;

        // increment ply (go deeper)
        copy_ply++;
    }

    /**
     * Restore board state (Pop from History Stack)
     */
    public void takeBack() {
        // decrement ply to get the previous state (go back)
        copy_ply--;

        // copy the previous state from the history stack back to the board
        System.arraycopy(bitboards_copy[copy_ply], 0, this.bitboards, 0, 12);
        System.arraycopy(occupancies_copy[copy_ply], 0, this.occupancies, 0, 3);

        this.side = side_copy[copy_ply];
        this.enpassant = enpassant_copy[copy_ply];
        this.castle = castle_copy[copy_ply];
    }

    /**
     * returns bitboard that has only one piece type
     *
     * @param index put P or N, ... so on that are on EncodedPieces
     * @return if index is P, returns only white pawns (P) if index is  n, returns only black knights (n) and so on
     */
    public long getBitboardPiece(int index) {
        return bitboards[index];
    }
}

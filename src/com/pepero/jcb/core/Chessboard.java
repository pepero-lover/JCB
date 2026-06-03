package com.pepero.jcb.core;

import com.pepero.jcb.constant.BoardSquares;
import com.pepero.jcb.hash.Zobrist;

import java.util.Arrays;

import static com.pepero.jcb.constant.BoardSquares.*;

public class Chessboard {

    // start pos
    public static final String start_position = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 ";

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

    // history hashes
    public long[] historyHashes = new long[1024];

    // ply
    public int ply = 0;

    // half ply
    public int half_ply = 0;


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

    // "almost" unique position identifier aka hash key or position key
    public long hash_key;

    // maximum search depth (Perft & Search)
    public static final int MAX_DEPTH = 128;

    // preserve board state arrays (stack)
    private long[][] bitboards_copy = new long[MAX_DEPTH][12];
    private long[][] occupancies_copy = new long[MAX_DEPTH][3];
    private int[] side_copy = new int[MAX_DEPTH];
    private int[] enpassant_copy = new int[MAX_DEPTH];
    private int[] castle_copy = new int[MAX_DEPTH];

    private long[] hash_key_copy = new long[MAX_DEPTH];

    private int[] ply_copy = new int[MAX_DEPTH];
    private int[] half_ply_copy = new int[MAX_DEPTH];

    // ply counter for history stack
    private int copy_index = 0;

    public Chessboard() {
        resetBoard();
    }

    public Chessboard(String fen) {
        ChessBoardUtils.parseFen(this, fen);
    }

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
        this.copy_index = 0;

        // init hash key
        this.hash_key = Zobrist.generateHashKey(this);

        // reset ply
        this.ply = 0;

        // reset half ply
        this.half_ply = 0;
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
        System.arraycopy(this.bitboards, 0, bitboards_copy[copy_index], 0, 12);
        System.arraycopy(this.occupancies, 0, occupancies_copy[copy_index], 0, 3);

        side_copy[copy_index] = this.side;
        enpassant_copy[copy_index] = this.enpassant;
        castle_copy[copy_index] = this.castle;

        hash_key_copy[copy_index] = this.hash_key;

        ply_copy[copy_index] = this.ply;
        half_ply_copy[copy_index] = this.half_ply;

        // increment index
        copy_index++;
    }

    /**
     * Restore board state (Pop from History Stack)
     */
    public void takeBack() {
        // decrement index to get the previous state
        copy_index--;

        // copy the previous state from the history stack back to the board
        System.arraycopy(bitboards_copy[copy_index], 0, this.bitboards, 0, 12);
        System.arraycopy(occupancies_copy[copy_index], 0, this.occupancies, 0, 3);

        this.side = side_copy[copy_index];
        this.enpassant = enpassant_copy[copy_index];
        this.castle = castle_copy[copy_index];

        this.hash_key = hash_key_copy[copy_index];

        this.ply = ply_copy[copy_index];
        this.half_ply = half_ply_copy[copy_index];
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

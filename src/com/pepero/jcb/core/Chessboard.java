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


    // For chess 960

    // king side rook square on chess 960
    public int king_side_rook_file = -1;
    // queen side rook on chess 960
    public int queen_side_rook_file = -1;

    public GameVariants gameVariants;

    public static final int MAX_DEPTH = 1024;

    public int[] enpassant_history = new int[MAX_DEPTH];
    public int[] castle_history = new int[MAX_DEPTH];
    public int[] half_ply_history = new int[MAX_DEPTH];
    public long[] hash_key_history = new long[MAX_DEPTH];

    public int[] captured_piece_history = new int[MAX_DEPTH];

    public Chessboard() {
        this(GameVariants.STANDARD);
    }

    public Chessboard(GameVariants gameVariants) {
        resetBoard(gameVariants);
    }

    public Chessboard(String fen) {
        this(fen, GameVariants.STANDARD);
    }

    public Chessboard(String fen, GameVariants gameVariants) {
        ChessboardUtils.parseFen(this, fen);

        this.gameVariants = gameVariants;
    }

    public Chessboard(Chessboard source) {
        this.copyFrom(source);
    }

    public void resetBoard() {
        // reset board position and state variables
        Arrays.fill(this.bitboards, 0L);

        // reset occupancies (bitboards)
        Arrays.fill(this.occupancies, 0L);

        Arrays.fill(this.enpassant_history, 0);
        Arrays.fill(this.castle_history, 0);
        Arrays.fill(this.half_ply_history, 0);
        Arrays.fill(this.hash_key_history, 0);
        Arrays.fill(this.captured_piece_history, 0);

        // reset game state variables
        this.side = 0;
        this.enpassant = no_sq;
        this.castle = 0;

        // game variants
        this.gameVariants = GameVariants.STANDARD;

        // chess 960
        this.king_side_rook_file = -1;
        this.queen_side_rook_file = -1;

        // init hash key
        this.hash_key = Zobrist.generateHashKey(this);

        // reset ply
        this.ply = 0;

        // reset half ply
        this.half_ply = 0;
    }

    public void resetBoard(GameVariants gameVariants) {
        resetBoard();
        this.gameVariants = gameVariants;
    }

    public void setStartPos() {
        // reset the original board
        resetBoard();

        // set start position with fen
        ChessboardUtils.parseFen(this, start_position);
    }

    public void copyFrom(Chessboard source) {
        System.arraycopy(source.bitboards, 0, this.bitboards, 0, 12);
        System.arraycopy(source.occupancies, 0, this.occupancies, 0, 3);

        this.side = source.side;
        this.enpassant = source.enpassant;
        this.castle = source.castle;

        this.hash_key = source.hash_key;

        this.ply = source.ply;
        this.half_ply = source.half_ply;

        this.gameVariants = source.gameVariants;

        this.king_side_rook_file = source.king_side_rook_file;
        this.queen_side_rook_file = source.queen_side_rook_file;

        System.arraycopy(source.historyHashes, 0, this.historyHashes, 0, 1024);
        System.arraycopy(source.enpassant_history, 0, this.enpassant_history, 0, MAX_DEPTH);
        System.arraycopy(source.castle_history, 0, this.castle_history, 0, MAX_DEPTH);
        System.arraycopy(source.half_ply_history, 0, this.half_ply_history, 0, MAX_DEPTH);
        System.arraycopy(source.hash_key_history, 0, this.hash_key_history, 0, MAX_DEPTH);
        System.arraycopy(source.captured_piece_history, 0, this.captured_piece_history, 0, MAX_DEPTH);
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

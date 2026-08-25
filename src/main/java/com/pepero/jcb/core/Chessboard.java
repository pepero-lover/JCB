package com.pepero.jcb.core;

import com.pepero.jcb.core.constant.BoardSquares;
import com.pepero.jcb.core.hash.Zobrist;

import java.util.Arrays;

import static com.pepero.jcb.core.constant.SideToMove.*;
import static com.pepero.jcb.core.constant.BoardSquares.*;

public class Chessboard {
    static {
        Initializer.init();
    }

    // start pos
    public static final String start_position = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    // horde start pos
    public static final String horde_start_position = "rnbqkbnr/pppppppp/8/1PP2PP1/PPPPPPPP/PPPPPPPP/PPPPPPPP/PPPPPPPP w kq - 0 1";

    // racing kings start pos
    public static final String racing_kings_start_position = "8/8/8/8/8/8/krbnNBRK/qrbnNBRQ w - - 0 1";

    // antichess variants start pos
    // equals to start_position, but removed castling rights.
    public static final String antichess_start_position = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1";

    // define bitboards
    public long[] bitboards = new long[12];

    /*
    define occupancy bitboards
    the reason why the array's size is 3 is that it has white state, black state,
     and merged white and black (all) state
    */
    public long[] occupancies = new long[3];

    /*
    define mailbox to check piece on square
     */
    public int[] mailbox = new int[64];

    // side to move
    // 0 equals to white
    // 1 equals to black
    public int side;

    // enpassant
    // init to no square
    public int enpassant = BoardSquares.no_sq;

    // ply
    public int ply = 0;


    // for storing full move ply
    public int full_move = 0;

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

    // For crazy house

    // captured piece pocket
    public int[] pocket = new int[12];

    public long promoted_pieces = 0L;


    // For 3 check

    // checked count
    public int[] check_count = new int[2];

    public GameVariant gameVariant;

    public boolean isChess960 = false;

    public int MAX_DEPTH = 256;

    public int[] enpassant_history = new int[MAX_DEPTH];
    public int[] castle_history = new int[MAX_DEPTH];
    public int[] half_ply_history = new int[MAX_DEPTH];
    public long[] hash_key_history = new long[MAX_DEPTH];

    public int[] captured_piece_history = new int[MAX_DEPTH];

    // 3 check
    public int[][] check_count_history = new int[2][MAX_DEPTH];

    // atomic
    public static final int MAX_EXPLOSION_PER_PLY = 8;
    public int[][] explosion_piece_history = new int[MAX_DEPTH][MAX_EXPLOSION_PER_PLY];
    public int[][] explosion_square_history = new int[MAX_DEPTH][MAX_EXPLOSION_PER_PLY];
    public int[] explosion_count_history = new int[MAX_DEPTH];

    // crazy house
    public boolean[] promoted_captured_history = new boolean[MAX_DEPTH];

    public Chessboard() {
        this(GameVariant.STANDARD);
    }

    public Chessboard(GameVariant gameVariant) {
        resetBoard(gameVariant);
    }

    public Chessboard(String fen) {
        this(fen, GameVariant.STANDARD);
    }

    public Chessboard(String fen, GameVariant gameVariant) {
        this(fen, false, gameVariant);
    }

    public Chessboard(String fen, boolean isChess960) {
        this(fen, isChess960, GameVariant.STANDARD);
    }

    public Chessboard(String fen, boolean isChess960, GameVariant gameVariant) {
        ChessboardUtils.parseFen(this, fen);

        this.isChess960 = isChess960;
        this.gameVariant = gameVariant;
    }

    public Chessboard(Chessboard source) {
        this.copyFrom(source);
    }

    public void resetBoard() {
        // reset board position and state variables
        Arrays.fill(this.bitboards, 0L);

        // reset occupancies (bitboards)
        Arrays.fill(this.occupancies, 0L);

        // reset mailbox
        Arrays.fill(this.mailbox, -1);

        Arrays.fill(this.enpassant_history, 0);
        Arrays.fill(this.castle_history, 0);
        Arrays.fill(this.half_ply_history, 0);
        Arrays.fill(this.hash_key_history, 0);
        Arrays.fill(this.captured_piece_history, 0);
        Arrays.fill(this.check_count_history[white], 0);
        Arrays.fill(this.check_count_history[black], 0);

        for (int i = 0; i < this.explosion_piece_history.length; i++) {
            Arrays.fill(this.explosion_piece_history[i], 0);
            Arrays.fill(this.explosion_square_history[i], 0);
        }
        Arrays.fill(this.explosion_count_history, 0);

        // reset game state variables
        this.side = 0;
        this.enpassant = no_sq;
        this.castle = 0;

        // game variant
        this.gameVariant = GameVariant.STANDARD;

        // chess 960
        this.king_side_rook_file = -1;
        this.queen_side_rook_file = -1;

        // init hash key
        this.hash_key = Zobrist.generateHashKey(this);

        // reset ply
        this.ply = 0;

        // reset full move
        this.full_move = 0;

        // reset half ply
        this.half_ply = 0;

        // reset pocket and promoted pieces
        this.promoted_pieces = 0L;

        Arrays.fill(this.pocket, 0);

        // reset 3 check counter
        Arrays.fill(this.check_count, 0);
    }

    public void resetBoard(GameVariant gameVariant) {
        resetBoard();
        this.gameVariant = gameVariant;
    }

    public void setStartPos() {
        // reset the original board
        resetBoard();

        // set start position with fen
        ChessboardUtils.parseFen(this, start_position);
    }

    public void copyFrom(Chessboard source) {
        if (source.MAX_DEPTH > this.MAX_DEPTH) this.ensureCapacityTo(source.MAX_DEPTH);

        System.arraycopy(source.bitboards, 0, this.bitboards, 0, 12);
        System.arraycopy(source.occupancies, 0, this.occupancies, 0, 3);
        System.arraycopy(source.mailbox, 0, this.mailbox, 0, 64);

        this.side = source.side;
        this.enpassant = source.enpassant;
        this.castle = source.castle;

        this.hash_key = source.hash_key;

        this.ply = source.ply;
        this.full_move = source.full_move;
        this.half_ply = source.half_ply;

        this.gameVariant = source.gameVariant;
        this.isChess960 = source.isChess960;

        this.king_side_rook_file = source.king_side_rook_file;
        this.queen_side_rook_file = source.queen_side_rook_file;

        System.arraycopy(source.enpassant_history, 0, this.enpassant_history, 0, MAX_DEPTH);
        System.arraycopy(source.castle_history, 0, this.castle_history, 0, MAX_DEPTH);
        System.arraycopy(source.half_ply_history, 0, this.half_ply_history, 0, MAX_DEPTH);
        System.arraycopy(source.hash_key_history, 0, this.hash_key_history, 0, MAX_DEPTH);
        System.arraycopy(source.captured_piece_history, 0, this.captured_piece_history, 0, MAX_DEPTH);

        // crazy house
        System.arraycopy(source.pocket, 0, this.pocket, 0, 12);
        this.promoted_pieces = source.promoted_pieces;
        System.arraycopy(source.promoted_captured_history, 0, this.promoted_captured_history, 0, MAX_DEPTH);

        // 3 check
        System.arraycopy(source.check_count, 0, this.check_count, 0, 2);

        System.arraycopy(source.check_count_history[white], 0, this.check_count_history[white], 0, MAX_DEPTH);
        System.arraycopy(source.check_count_history[black], 0, this.check_count_history[black], 0, MAX_DEPTH);

        // atomic
        for (int i = 0; i < MAX_DEPTH; i++) {
            System.arraycopy(source.explosion_piece_history[i], 0, this.explosion_piece_history[i], 0, MAX_EXPLOSION_PER_PLY);
            System.arraycopy(source.explosion_square_history[i], 0, this.explosion_square_history[i], 0, MAX_EXPLOSION_PER_PLY);
        }
        System.arraycopy(source.explosion_count_history, 0, this.explosion_count_history, 0, MAX_DEPTH);
    }

    /**
     * Ensure capacity to 'capacity' param
     *
     * @param capacity capacity
     */
    private void ensureCapacityTo(int capacity) {
        int oldCapacity = MAX_DEPTH;
        MAX_DEPTH = capacity;

        enpassant_history = Arrays.copyOf(enpassant_history, capacity);
        captured_piece_history = Arrays.copyOf(captured_piece_history, capacity);
        castle_history = Arrays.copyOf(castle_history, capacity);
        half_ply_history = Arrays.copyOf(half_ply_history, capacity);
        hash_key_history = Arrays.copyOf(hash_key_history, capacity);
        promoted_captured_history = Arrays.copyOf(promoted_captured_history, capacity);

        check_count_history[white] = Arrays.copyOf(check_count_history[white], capacity);
        check_count_history[black] = Arrays.copyOf(check_count_history[black], capacity);

        explosion_piece_history = Arrays.copyOf(explosion_piece_history, capacity);
        explosion_square_history = Arrays.copyOf(explosion_square_history, capacity);
        explosion_count_history = Arrays.copyOf(explosion_count_history, capacity);
        for (int i = oldCapacity; i < capacity; i++) {
            explosion_piece_history[i] = new int[MAX_EXPLOSION_PER_PLY];
            explosion_square_history[i] = new int[MAX_EXPLOSION_PER_PLY];
        }
    }

    /**
     * Resize history if ply is grater than history size
     */
    public void ensureCapacity() {
        if (ply >= MAX_DEPTH) {
            ensureCapacityTo(MAX_DEPTH * 2);
        }
    }
}

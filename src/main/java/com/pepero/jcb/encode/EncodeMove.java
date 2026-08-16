package com.pepero.jcb.encode;

import com.pepero.jcb.constant.BoardSquares;
import com.pepero.jcb.core.ChessboardUtils;

import java.util.HashMap;
import java.util.Map;

import static com.pepero.jcb.constant.BoardSquares.*;
import static com.pepero.jcb.constant.EncodedPieces.*;
import static com.pepero.jcb.core.ChessboardUtils.ascii_pieces;

public class EncodeMove {
    /*

         Binary move bits



    1 = use on this flag or something
    0 = not use on this flag or something

    binary                         hex

    0000 0000 0000 0000 0011 1111  0x3f      source square      the 6 bits are square code (com/pepero/bitboard/constant/BoardSquares.java)
    0000 0000 0000 1111 1100 0000  0xfc0     target square      this too,
    0000 0000 1111 0000 0000 0000  0xf000    piece              4 bits because the amount of piece is 12. 4 bits can handle 16 numbers so this is enough to storage
    0000 1111 0000 0000 0000 0000  0xf0000   promoted piece     this too,
    0001 0000 0000 0000 0000 0000  0x100000  capture flag       true / false ( 1 bit )
    0010 0000 0000 0000 0000 0000  0x200000  double push flag   true / false ( 1 bit )
    0100 0000 0000 0000 0000 0000  0x400000  enpassant flag     true / false ( 1 bit )
    1000 0000 0000 0000 0000 0000  0x800000  castling flag      true / false ( 1 bit )

    and additionally, crazy house :
    0000 0000 0000 0000 0000 0000 1111  0x3f       dropping piece type
    0000 0000 0000 0000 1111 1100 0000  0xfc0      target square (drop location)
    0001 0000 0000 0000 0000 0000 0000  0x1000000  dropping flag
     */

    /**
     * Encode crazy house dropping move
     *
     * @param pieceToDrop piece to drop type
     * @param target target square (drop location)
     * @return encoded move (int)
     */
    public static int encodeDropMove(int pieceToDrop, int target) {
        return (target << 6) | (pieceToDrop << 12) | (1 << 24);
    }

    /**
     * Encode move to 32 bit int data
     *
     * @param source piece start position
     * @param target piece destination
     * @param piece piece type
     * @param promoted pawn promote type
     * @param capture whether captured a piece
     * @param douPawn whether moved double pawn push move
     * @param enpassant whether played enpassant
     * @param castling whether played castling move
     * @return encoded move (int)
     */
    public static int encodeMove(int source, int target, int piece, int promoted,
                                 boolean capture, boolean douPawn, boolean enpassant, boolean castling){
        return
                source |
                (target << 6) |
                (piece << 12) |
                (promoted << 16) |
                (capture ? 1 : 0) << 20 |
                ((douPawn ? 1 : 0) << 21) |
                (enpassant ? 1 : 0) << 22|
                (castling ? 1 : 0) << 23;
    }

    /**
     * Extract encoded move and get source square
     * @param move encoded move (that can be generated on encodeMove() method)
     * @return source square
     */
    public static int getMoveSource(int move){
        return (move & 0x3f);

        // more information is on 6 lines to 22 lines
    }

    /**
     * Extract encoded move and get target square
     * @param move encoded move (that can be generated on encodeMove() method)
     * @return target square
     */
    public static int getMoveTarget(int move){
        return ((move & 0xfc0) >>> 6);
        // the reason why the result shifted 6 bits is the empty space like 0000 0000 0000 1111 1100 0000
        // and there is 00 0000 ( 6 bits ) of empty square, so if we want to get this only,
        // we need to remove these empty space (empty space is source square)
        // so we shifted 6 bits

        // more information is on 6 lines to 22 lines
    }

    /**
     * Extract encoded move and get piece type
     * @param move encoded move (that can be generated on encodeMove() method)
     * @return piece type
     */
    public static int getMovePiece(int move){
        return ((move & 0xf000) >>> 12);
    }

    /**
     * Extract encoded move and get promoted piece type
     * @param move encoded move (that can be generated on encodeMove() method)
     * @return promoted piece type
     */
    public static int getMovePromoted(int move){
        return ((move & 0xf0000) >>> 16);
    }

    /**
     * Extract encoded move and get capture flag
     * @param move encoded move (that can be generated on encodeMove() method)
     * @return capture flag
     */
    public static boolean getMoveCapture(int move){
        return ((move & 0x100000) >>> 20) == 1;
    }

    /**
     * Extract encoded move and get double pawn push flag
     * @param move encoded move (that can be generated on encodeMove() method)
     * @return  double push flag
     */
    public static boolean getMoveDouble(int move){
        return ((move & 0x200000) >>> 21) == 1;
    }

    /**
     * Extract encoded move and get enpassant flag
     * @param move encoded move (that can be generated on encodeMove() method)
     * @return enpassant flag
     */
    public static boolean getMoveEnpassant(int move){
        return ((move & 0x400000) >>> 22) == 1;
    }

    /**
     * Extract encoded move and get castling flag
     * @param move encoded move (that can be generated on encodeMove() method)
     * @return castling flag
     */
    public static boolean getMoveCastling(int move){
        return ((move & 0x800000) >>> 23) == 1;
    }

    /**
     * Extract encoded move and get drop flag
     * @param move encoded move (that can be generated on encodeMove() method)
     * @return move drop flag
     */
    public static boolean getMoveDrop(int move){
        return ((move & 0x1000000) >>> 24) == 1;
    }

    /**
     * Get encoded move string
     *
     * @param move encoded move (that can be generated on encodeMove() method)
     * @return encoded move string
     */
    public static String moveToString(int move) {
        if (EncodeMove.getMoveDrop(move)) {
            char pieceChar = ChessboardUtils.ascii_pieces[EncodeMove.getMovePiece(move)];
            String target = BoardSquares.square_to_coordinates[EncodeMove.getMoveTarget(move)];
            return Character.toUpperCase(pieceChar) + "@" + target;
        } else {
            int source = EncodeMove.getMoveSource(move);
            int target = EncodeMove.getMoveTarget(move);

            return BoardSquares.square_to_coordinates[source] +
                    BoardSquares.square_to_coordinates[target] +
                    (EncodeMove.getMovePromoted(move) != 0 ?
                            EncodeMove.promoted_pieces.get(EncodeMove.getMovePromoted(move)) : "");
        }
    }

    /**
     * Print move data (for UCI purposes)
     * @param move encoded move
     */
    public static void printMove(int move){
        System.out.println(moveToString(move));
    }

    // promoted pieces
    public static Map<Integer, Character> promoted_pieces = new HashMap<>();

    /**
     * Init promoted_pieces char map
     */
    public static void initPromotedPiecesChar(){
        promoted_pieces.put(Q, 'q');
        promoted_pieces.put(R, 'r');
        promoted_pieces.put(B, 'b');
        promoted_pieces.put(N, 'n');
        promoted_pieces.put(q, 'q');
        promoted_pieces.put(r, 'r');
        promoted_pieces.put(b, 'b');
        promoted_pieces.put(n, 'n');
    }
}

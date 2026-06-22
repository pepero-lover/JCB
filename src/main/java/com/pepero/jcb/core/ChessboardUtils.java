package com.pepero.jcb.core;

import com.pepero.jcb.api.enums.Square;
import com.pepero.jcb.bitboard.Attacks;
import com.pepero.jcb.bitboard.BitBoardUtils;
import com.pepero.jcb.constant.BoardSquares;
import com.pepero.jcb.constant.CastlingRights;
import com.pepero.jcb.constant.SideToMove;
import com.pepero.jcb.encode.EncodeMove;
import com.pepero.jcb.hash.Zobrist;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.pepero.jcb.constant.BoardSquares.*;
import static com.pepero.jcb.constant.EncodedPieces.*;
import static com.pepero.jcb.constant.SideToMove.*;

public class ChessboardUtils {
    public static final char[] ascii_pieces = {
            'P','N','B','R','Q','K',
            'p','n','b','r','q','k'
    };

    public static final char[] promotion_pieces = {
            'p','n','b','r','q','k'
    };

    // convert char pieces to encoded constants
    public static final Map<Character, Integer> char_to_encoded_piece = new HashMap<>();

    // init piece char
    public static void initCharPieces(){
        initCharToEncodedPiece();
        initPieceToCharPieces();
    }

    // init char map
    private static void initCharToEncodedPiece(){
        char_to_encoded_piece.put('P', P);
        char_to_encoded_piece.put('N', N);
        char_to_encoded_piece.put('B', B);
        char_to_encoded_piece.put('R', R);
        char_to_encoded_piece.put('Q', Q);
        char_to_encoded_piece.put('K', K);

        char_to_encoded_piece.put('p', p);
        char_to_encoded_piece.put('n', n);
        char_to_encoded_piece.put('b', b);
        char_to_encoded_piece.put('r', r);
        char_to_encoded_piece.put('q', q);
        char_to_encoded_piece.put('k', k);
    }

    public static final Map<Integer, Character> encoded_piece_to_char = new HashMap<>();

    // init char map
    private static void initPieceToCharPieces(){
        encoded_piece_to_char.put(P, 'P');
        encoded_piece_to_char.put(N, 'N');
        encoded_piece_to_char.put(B, 'B');
        encoded_piece_to_char.put(R, 'R');
        encoded_piece_to_char.put(Q, 'Q');
        encoded_piece_to_char.put(K, 'K');

        encoded_piece_to_char.put(p, 'p');
        encoded_piece_to_char.put(n, 'n');
        encoded_piece_to_char.put(b, 'b');
        encoded_piece_to_char.put(r, 'r');
        encoded_piece_to_char.put(q, 'q');
        encoded_piece_to_char.put(k, 'k');
    }

    /**
     * print this chessboard
     */
    public static void printChessBoard(Chessboard chessboard) {
        StringBuilder sb = new StringBuilder(256);
        char[] board = new char[64];

        // initialize board with dots
        Arrays.fill(board, '.');

        // loop over all piece types
        for (int piece = P; piece <= k; piece++) {
            long bitboardPiece = chessboard.getBitboardPiece(piece);
            char pieceChar = ascii_pieces[piece];

            // a bit scanning: find all set bits for this piece type
            while (bitboardPiece != 0L) {
                int square = BitBoardUtils.getLS1BIndex(bitboardPiece);
                board[square] = pieceChar;
                bitboardPiece = BitBoardUtils.popBit(bitboardPiece,square);
            }
        }

        sb.append('\n');

        // loop over board ranks
        for (int rank = 0; rank < 8; rank++) {
            // append ranks
            sb.append("  ").append(8 - rank).append("  ");

            // loop over board files
            for (int file = 0; file < 8; file++) {
                int square = rank * 8 + file;
                // prints char piece from our mapped board
                sb.append(" ").append(board[square]);
            }
            // print new line every rank
            sb.append('\n');
        }

        // print board files
        sb.append("\n      a b c d e f g h \n\n");

        // print side to move
        sb.append("      Side:     ").append(chessboard.side == SideToMove.white ? "white" : "black").append("\n");

        // print enpassant square
        sb.append("      Enpassant:   ").append((chessboard.enpassant != no_sq) ?
                BoardSquares.square_to_coordinates[chessboard.enpassant] : "no").append("\n");

        // print castling rights
        sb.append("      Castling:  ")
                .append(((chessboard.castle & CastlingRights.WK) != 0) ? 'K' : '-')
                .append(((chessboard.castle & CastlingRights.WQ) != 0) ? 'Q' : '-')
                .append(((chessboard.castle & CastlingRights.BK) != 0) ? 'k' : '-')
                .append(((chessboard.castle & CastlingRights.BQ) != 0) ? 'q' : '-')
                .append("\n");

        sb.append("      Hash key:  ")
                .append(Long.toHexString(chessboard.hash_key))
                .append("\n");

        System.out.print(sb);
    }

    public static void parseFen(Chessboard chessboard, String fen) {
        // reset chessboard
        chessboard.resetBoard();

        // divide fen
        String[] fenDivided = fen.split(" ");

        // init rank and file
        int rank = 0;
        int file = 0;

        String fenBoard = fenDivided[0];

        // loop over FEN string
        for (int i = 0; i < fenBoard.length(); i++) {
            // get one fen char
            char fenChar = fenBoard.charAt(i);

            // match space char (end of board config)
            if (fenChar == ' ') {
                break;
            }

            // match rank separator
            if (fenChar == '/') {
                // increment rank
                rank++;

                // reset file
                file = 0;
            }
            // match empty square numbers within FEN string
            else if (fenChar >= '1' && fenChar <= '8') {
                // adjust file counter
                file += (fenChar - '0');
            }
            // match char pieces within FEN string
            else if ((fenChar >= 'a' && fenChar <= 'z') || (fenChar >= 'A' && fenChar <= 'Z')) {
                // init current square
                int square = rank * 8 + file;

                // init piece type
                Integer piece = ChessboardUtils.char_to_encoded_piece.get(fenChar);

                if (piece != null) {
                    // set piece on the corresponding bitboard
                    chessboard.bitboards[piece] = BitBoardUtils.setBit(chessboard.bitboards[piece], square);
                }

                // increment file counter
                file++;
            }
        }

        long rooks = chessboard.bitboards[r];

        int queen_rook_square = BitBoardUtils.getLS1BIndex(rooks);

        if(queen_rook_square != -1) {
            chessboard.queen_side_rook_file = queen_rook_square;

            rooks = BitBoardUtils.popBit(rooks, queen_rook_square);
            int king_rook_square = BitBoardUtils.getLS1BIndex(rooks);

            if (king_rook_square != -1) {
                chessboard.king_side_rook_file = king_rook_square % 8;
            } else {
                chessboard.king_side_rook_file = -1;
            }
        } else {
            chessboard.king_side_rook_file = -1;
            chessboard.queen_side_rook_file = -1;
        }


        // parse side to move
        String sideFen = fenDivided[1];
        chessboard.side = 'w' == sideFen.charAt(0) ? white : black;

        // parse castling rights
        String castlingFen = fenDivided[2];

        for(char c : castlingFen.toCharArray()){
            switch (c){
                case 'K' : chessboard.castle |= CastlingRights.WK; break;
                case 'Q' : chessboard.castle |= CastlingRights.WQ; break;
                case 'k' : chessboard.castle |= CastlingRights.BK; break;
                case 'q' : chessboard.castle |= CastlingRights.BQ; break;
                case '-' :
                default: break;
            }
        }

        // parse enpassant square
        String enpassantFen = fenDivided[3];

        if(!enpassantFen.equals("-")){
            // parse enpassant file & rank
            int fileInt = enpassantFen.charAt(0) - 'a';
            int rankInt = 8 - (enpassantFen.charAt(1) - '0');

            // init enpassant square
            chessboard.enpassant = rankInt * 8 + fileInt;
        }

        else // no enpassant square
            chessboard.enpassant = no_sq;

        // loop over white pieces bitboards
        for (int piece = P; piece <= K; piece++){
            // populate white occupancy bitboard
            chessboard.occupancies[white] |= chessboard.bitboards[piece];
        }

        // loop over black pieces bitboards
        for (int piece = p; piece <= k; piece++){
            // populate black occupancy bitboard
            chessboard.occupancies[black] |= chessboard.bitboards[piece];
        }

        // init half ply
        chessboard.half_ply = Integer.parseInt(fenDivided[4]);

        // init ply
        chessboard.ply = (Integer.parseInt(fenDivided[5]) - 1) * 2 + chessboard.side == white ? 0 : 1;

        // init all occupancies
        chessboard.occupancies[both] |= chessboard.occupancies[white];
        chessboard.occupancies[both] |= chessboard.occupancies[black];

        // init hash key
        chessboard.hash_key = Zobrist.generateHashKey(chessboard);
    }

    public static void parseFen(Chessboard chessboard, String fen, GameVariants gameVariants) {

    }

    public static String getFen(Chessboard chessboard){
        StringBuilder fen = new StringBuilder();

        for (int rank = 0; rank < 8; rank++){
            int empty_square = 0;

            for (int file = 0; file < 8; file++){
                int square = rank * 8 + file;

                int type = getPieceTypeOnSquare(chessboard, square);
                if(type == -1) {
                    empty_square++;
                    continue;
                }

                if(empty_square > 0) {
                    fen.append(empty_square);
                    empty_square = 0;
                }

                fen.append(encoded_piece_to_char.get(type));
            }

            if(empty_square > 0) {
                fen.append(empty_square);
            }

            if (rank < 7){
                fen.append("/");
            }
        }

        // side to move
        fen.append(chessboard.side == white ? " w " : " b ");

        // castling rights
        StringBuilder castle = new StringBuilder();
        if ((chessboard.castle & CastlingRights.WK) != 0) castle.append("K");
        if ((chessboard.castle & CastlingRights.WQ) != 0) castle.append("Q");
        if ((chessboard.castle & CastlingRights.BK) != 0) castle.append("k");
        if ((chessboard.castle & CastlingRights.BQ) != 0) castle.append("q");

        fen.append(castle.isEmpty() ? "-" : castle.toString()).append(" ");

        // enpassant
        if (chessboard.enpassant == BoardSquares.no_sq) {
            fen.append("- ");
        } else {
            fen.append(BoardSquares.square_to_coordinates[chessboard.enpassant]).append(" ");
        }

        fen.append(chessboard.half_ply).append(" ");

        fen.append((chessboard.ply / 2) + 1);

        return fen.toString();
    }

    /**
     * Get piece type on square
     * if there is notting, returns -1
     *
     * @param chessboard chessboard
     * @param square square
     * @return Get piece type on square
     * if there is notting, returns -1
     */
    public static int getPieceTypeOnSquare(Chessboard chessboard, int square){
        for (int piece = P; piece <= k; piece++){
            if(BitBoardUtils.getBit(chessboard.bitboards[piece], square)) return piece;
        }

        return -1;
    }

    /**
     * Get whether king is under attack (depends on side to move)
     * @param chessboard chessboard
     * @return whether king is under attack
     */
    public static boolean isCheck(Chessboard chessboard) {
        int kingPos = BitBoardUtils.getLS1BIndex(
                chessboard.side == white ?
                        chessboard.bitboards[K] : chessboard.bitboards[k]);

        return MoveGenerator.isSquareAttacked(chessboard, kingPos, chessboard.side == white ? black : white);
    }

    /**
     * Get checkers square <br>
     * if return is <b>00101010001010</b>, <br>
     * the first checker square is '<b>001010</b>', and second checker is '<b>100010</b>' <br>
     * and attacking piece count is '<b>11</b>' and count is 2.
     *
     * @param chessboard chessboard
     * @return checkers square
     */
    public static int getChecker(Chessboard chessboard) {
        int kingSquare = BitBoardUtils.getLS1BIndex(
                chessboard.side == white ? chessboard.bitboards[K] : chessboard.bitboards[k]);

        int firstAttacker = -1;
        int secondAttacker = -1;
        int oppSide = chessboard.side == white ? black : white;

        // get all checker
        long checkersMask = 0L;

        // pawn
        if (oppSide == white) {
            checkersMask |= Attacks.pawn_attacks[black][kingSquare] & chessboard.bitboards[P];
        } else {
            checkersMask |= Attacks.pawn_attacks[white][kingSquare] & chessboard.bitboards[p];
        }

        // knight
        checkersMask |= Attacks.knight_attacks[kingSquare] &
                (oppSide == white ? chessboard.bitboards[N] : chessboard.bitboards[n]);

        // bishop
        checkersMask |= Attacks.getBishopAttacks(kingSquare, chessboard.occupancies[both]) &
                (oppSide == white ? (chessboard.bitboards[B] | chessboard.bitboards[Q]) :
                        (chessboard.bitboards[b] | chessboard.bitboards[q]));

        // rook
        checkersMask |= Attacks.getRookAttacks(kingSquare, chessboard.occupancies[both]) &
                (oppSide == white ? (chessboard.bitboards[R] | chessboard.bitboards[Q]) :
                        (chessboard.bitboards[r] | chessboard.bitboards[q]));

        // queen is already contained

        if (checkersMask != 0) {
            firstAttacker = BitBoardUtils.getLS1BIndex(checkersMask);
            checkersMask = BitBoardUtils.popBit(checkersMask, firstAttacker);

            if (checkersMask != 0) {
                secondAttacker = BitBoardUtils.getLS1BIndex(checkersMask);
            }
        }

        return (firstAttacker != -1 ? firstAttacker : 0) |
                (secondAttacker != -1 ? secondAttacker << 6 : 0) |
                (firstAttacker != -1 ? 1 << 12 : 0) |
                (secondAttacker != -1 ? 1 << 13 : 0);
    }

    /**
     * Return whether this position has legal move(s)
     *
     * @param chessboard chess board
     * @return whether this position has legal move(s)
     */
    public static boolean hasLegalMoves(Chessboard chessboard) {
        int[] move_list = new int[255];
        int move_count = MoveGenerator.generateMoves(chessboard, move_list);

        for (int i = 0; i < move_count; i++) {
            if (MoveGenerator.makeMove(chessboard, move_list[i])) {
                MoveGenerator.unmakeMove(chessboard, move_list[i]);
                return true;
            }
        }

        return false;
    }

    /**
     * Return whether this move is legal move or not
     *
     * @param chessboard chessboard
     * @param encoded_move encoded move
     * @return whether this move is legal move or not
     */
    public static boolean isLegalMove(Chessboard chessboard, int encoded_move) {
        int[] move_list = new int[255];
        int move_count = MoveGenerator.generateMoves(chessboard, move_list);

        for (int i = 0; i < move_count; i++) {
            if(EncodeMove.getMoveSource(move_list[i]) == EncodeMove.getMoveSource(encoded_move) &&
                    EncodeMove.getMoveTarget(move_list[i]) == EncodeMove.getMoveTarget(encoded_move) &&
                    EncodeMove.getMovePromoted(move_list[i]) == EncodeMove.getMovePromoted(encoded_move)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get whether this position is checkmate
     *
     * @param chessboard chessboard
     * @return whether this position is checkmate
     */
    public static boolean isCheckmate(Chessboard chessboard) {
        return isCheck(chessboard) && !hasLegalMoves(chessboard);
    }

    /**
     * Get whether this position is stalemate
     *
     * @param chessboard chessboard
     * @return whether this position is stalemate
     */
    public static boolean isStaleMate(Chessboard chessboard) {
        return !isCheck(chessboard) && !hasLegalMoves(chessboard);
    }

    /**
     * Get this position's repetition count
     *
     * @param chessboard chessboard
     * @return this position's repetition count
     */
    public static int getRepetitionCount(Chessboard chessboard) {
        int count = 1;

        int limit = Math.max(0, chessboard.ply - chessboard.half_ply);

        for (int i = chessboard.ply - 2; i >= limit; i -= 2) {
            if (chessboard.historyHashes[i] == chessboard.hash_key){
                count++;

                if (count >= 3){
                    return 3;
                }
            }
        }

        return count;
    }
}

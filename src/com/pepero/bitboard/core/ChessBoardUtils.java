package com.pepero.bitboard.core;

import com.pepero.bitboard.bitboard.BitBoardUtils;
import com.pepero.bitboard.constant.BoardSquares;
import com.pepero.bitboard.constant.CastlingRights;
import com.pepero.bitboard.constant.SideToMove;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static com.pepero.bitboard.constant.BoardSquares.*;
import static com.pepero.bitboard.constant.EncodedPieces.*;
import static com.pepero.bitboard.constant.SideToMove.*;

public class ChessBoardUtils {
    public static final char[] ascii_pieces = {
            'P','N','B','R','Q','K',
            'p','n','b','r','q','k'
    };

    // convert char pieces to encoded constants
    public static final Map<Character, Integer> char_pieces = new HashMap<>();

    // init char map
    public static void initCharPieces(){
        char_pieces.put('P', P);
        char_pieces.put('N', N);
        char_pieces.put('B', B);
        char_pieces.put('R', R);
        char_pieces.put('Q', Q);
        char_pieces.put('K', K);

        char_pieces.put('p', p);
        char_pieces.put('n', n);
        char_pieces.put('b', b);
        char_pieces.put('r', r);
        char_pieces.put('q', q);
        char_pieces.put('k', k);
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
                Integer piece = ChessBoardUtils.char_pieces.get(fenChar);

                if (piece != null) {
                    // set piece on the corresponding bitboard
                    chessboard.bitboards[piece] = BitBoardUtils.setBit(chessboard.bitboards[piece], square);
                }

                // increment file counter
                file++;
            }
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

        // init all occupancies
        chessboard.occupancies[both] |= chessboard.occupancies[white];
        chessboard.occupancies[both] |= chessboard.occupancies[black];
    }
}

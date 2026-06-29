package com.pepero.jcb.core;

import com.pepero.jcb.bitboard.Attacks;
import com.pepero.jcb.bitboard.BitBoardUtils;
import com.pepero.jcb.constant.BoardSquares;
import com.pepero.jcb.constant.CastlingRights;
import com.pepero.jcb.constant.SideToMove;
import com.pepero.jcb.encode.EncodeMove;
import com.pepero.jcb.hash.Zobrist;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static com.pepero.jcb.constant.BoardSquares.*;
import static com.pepero.jcb.constant.EncodedPieces.*;
import static com.pepero.jcb.constant.MoveCache.CHESSBOARD_UTIL_CACHE;
import static com.pepero.jcb.constant.MoveCache.MAX_MOVE_SIZE;
import static com.pepero.jcb.constant.SideToMove.*;
import static com.pepero.jcb.core.MoveGenerator.isSquareAttacked;

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
        System.out.println(toStringChessboard(chessboard));
    }

    public static void parseFen(Chessboard chessboard, String fen) {
        // reset chessboard
        chessboard.resetBoard(chessboard.gameVariants);

        // divide fen
        String[] fenDivided = fen.trim().split("\\s+");

        String boardPart = fenDivided[0];
        Arrays.fill(chessboard.pocket, 0);

        // pocket parsing
        int pocketStart = boardPart.indexOf('[');
        if (pocketStart != -1) {
            int pocketEnd = boardPart.indexOf(']');
            if (pocketEnd > pocketStart) {
                String pocketStr = boardPart.substring(pocketStart + 1, pocketEnd);
                for (int i = 0; i < pocketStr.length(); i++) {
                    char c = pocketStr.charAt(i);
                    Integer piece = char_to_encoded_piece.get(c);
                    if (piece != null) {
                        chessboard.pocket[piece]++;
                    }
                }
            }
            // remove pocket on board part
            boardPart = boardPart.substring(0, pocketStart);
        }

        // init rank and file
        int rank = 0;
        int file = 0;
        int last_square = -1; // for '~' parsing

        // loop over FEN string
        for (int i = 0; i < boardPart.length(); i++) {
            // get one fen char
            char fenChar = boardPart.charAt(i);

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
            // if last square was promoted piece
            else if(fenChar == '~') {
                if (last_square != -1 && chessboard.gameVariants == GameVariants.CRAZY_HOUSE) {
                    chessboard.promoted_pieces = BitBoardUtils.setBit(chessboard.promoted_pieces, last_square);
                }
            }
            // match char pieces within FEN string
            else if ((fenChar >= 'a' && fenChar <= 'z') || (fenChar >= 'A' && fenChar <= 'Z')) {
                Integer piece = char_to_encoded_piece.get(fenChar);
                if (piece != null && file < 8) {
                    last_square = rank * 8 + file;

                    chessboard.bitboards[piece] = BitBoardUtils.setBit(chessboard.bitboards[piece], last_square);

                    file++;
                }
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
        chessboard.side = (fenDivided.length > 1 && fenDivided[1].equals("b")) ? black : white;

        // parse castling rights
        if (fenDivided.length > 2 && !fenDivided[2].equals("-")) {
            for(char c : fenDivided[2].toCharArray()){
                switch (c){
                    case 'K' : chessboard.castle |= CastlingRights.WK; break;
                    case 'Q' : chessboard.castle |= CastlingRights.WQ; break;
                    case 'k' : chessboard.castle |= CastlingRights.BK; break;
                    case 'q' : chessboard.castle |= CastlingRights.BQ; break;
                }
            }
        }

        // parse enpassant square
        chessboard.enpassant = no_sq;

        if (fenDivided.length > 3 && !fenDivided[3].equals("-") && fenDivided[3].length() >= 2) {
            int fileInt = fenDivided[3].charAt(0) - 'a';
            int rankInt = 8 - (fenDivided[3].charAt(1) - '0');
            if (fileInt >= 0 && fileInt <= 7 && rankInt >= 0 && rankInt <= 7) {
                chessboard.enpassant = rankInt * 8 + fileInt;
            }
        }

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
        chessboard.half_ply = (fenDivided.length > 4) ? Integer.parseInt(fenDivided[4]) : 0;
        int full_move = (fenDivided.length > 5) ? Integer.parseInt(fenDivided[5]) : 1;

        // init ply
        chessboard.ply = (full_move - 1) * 2 + (chessboard.side == white ? 0 : 1);

        // init all occupancies
        chessboard.occupancies[both] |= chessboard.occupancies[white];
        chessboard.occupancies[both] |= chessboard.occupancies[black];

        // init hash key
        chessboard.hash_key = Zobrist.generateHashKey(chessboard);
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

                if (chessboard.gameVariants == GameVariants.CRAZY_HOUSE &&
                        BitBoardUtils.getBit(chessboard.promoted_pieces, square)) {
                    fen.append("~");
                }
            }

            if(empty_square > 0) {
                fen.append(empty_square);
            }

            if (rank < 7){
                fen.append("/");
            }
        }

        if (chessboard.gameVariants == GameVariants.CRAZY_HOUSE) {
            String pocketStr =
                    "Q".repeat(Math.max(0, chessboard.pocket[Q])) +
                    "R".repeat(Math.max(0, chessboard.pocket[R])) +
                    "B".repeat(Math.max(0, chessboard.pocket[B])) +
                    "N".repeat(Math.max(0, chessboard.pocket[N])) +
                    "P".repeat(Math.max(0, chessboard.pocket[P])) +
                    "q".repeat(Math.max(0, chessboard.pocket[q])) +
                    "r".repeat(Math.max(0, chessboard.pocket[r])) +
                    "b".repeat(Math.max(0, chessboard.pocket[b])) +
                    "n".repeat(Math.max(0, chessboard.pocket[n])) +
                    "p".repeat(Math.max(0, chessboard.pocket[p]));

            if (!pocketStr.isEmpty()) {
                fen.append("[").append(pocketStr).append("]");
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

        return isSquareAttacked(chessboard, kingPos, chessboard.side == white ? black : white);
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
        int[] move_list = CHESSBOARD_UTIL_CACHE.get();
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
     * Return whether this move is a legal move or not
     *
     * @param chessboard chessboard
     * @param encoded_move encoded move
     * @return whether this move is a legal move or not
     */
    public static boolean isLegalMove(Chessboard chessboard, int encoded_move) {
        int[] move_list = CHESSBOARD_UTIL_CACHE.get();
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
            if (chessboard.hash_key_history[i] == chessboard.hash_key){
                count++;

                if (count >= 3){
                    return 3;
                }
            }
        }

        return count;
    }

    /**
     * Get whether 'pinned_piece' is pinned or not
     *
     * @param chessboard chessboard
     * @param square king square or something
     * @param pinned_piece the piece to get whether this piece is pinned or not
     *
     * @return whether 'pinned_piece' is pinned or not
     */
    public static boolean isPinned(Chessboard chessboard, int square, int pinned_piece) {
        int piece_type = getPieceTypeOnSquare(chessboard, pinned_piece);

        if(piece_type == -1) return false;
        if(piece_type == K || piece_type == k) return false;

        boolean is_white = piece_type <= K;

        long occupancy = chessboard.occupancies[both];

        long first_bishop_attack = Attacks.getBishopAttacks(square, occupancy);
        long first_rook_attack = Attacks.getRookAttacks(square, occupancy);

        boolean in_bishop_line = BitBoardUtils.getBit(first_bishop_attack, pinned_piece);
        boolean in_rook_line = BitBoardUtils.getBit(first_rook_attack, pinned_piece);

        if (!in_bishop_line && !in_rook_line) {
            return false;
        }

        long enemy_bishop = is_white ?
                (chessboard.bitboards[b] | chessboard.bitboards[q]) :
                (chessboard.bitboards[B] | chessboard.bitboards[Q]);

        long enemy_rook = is_white ?
                (chessboard.bitboards[r] | chessboard.bitboards[q]) :
                (chessboard.bitboards[R] | chessboard.bitboards[Q]);

        long occ_without_piece = occupancy ^ (1L << pinned_piece);

        if (in_bishop_line) {
            long second_bishop_attack = Attacks.getBishopAttacks(square, occ_without_piece);
            long bishop_shadow = second_bishop_attack & ~first_bishop_attack;

            if ((bishop_shadow & enemy_bishop) != 0L) {
                return true;
            }
        }

        if (in_rook_line) {
            long second_rook_attack = Attacks.getRookAttacks(square, occ_without_piece);
            long rook_shadow = second_rook_attack & ~first_rook_attack;

            if ((rook_shadow & enemy_rook) != 0L) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get whether 'pinned_piece' is pinned or not
     *
     * @param chessboard chessboard
     * @param pinned_piece the piece to get whether this piece is pinned or not
     *
     * @return whether 'pinned_piece' is pinned or not
     */
    public static boolean isPinnedToKing(Chessboard chessboard, int pinned_piece) {
        int piece_type = getPieceTypeOnSquare(chessboard, pinned_piece);
        if (piece_type == -1) return false;
        boolean is_white = (piece_type >= P && piece_type <= K);

        return isPinned(chessboard,
                BitBoardUtils.getLS1BIndex(is_white ? chessboard.bitboards[K] : chessboard.bitboards[k]),
                pinned_piece);
    }

    /**
     * Get pieces attacking square
     *
     * @param chessboard chessboard
     * @param square square
     * @param white_attacking is white/black attacking
     */
    public static long getAttackersTo(Chessboard chessboard, int square, boolean white_attacking) {
        long attackers = 0L;
        long occupancy = chessboard.occupancies[both];

        long pawns = white_attacking ? chessboard.bitboards[P] : chessboard.bitboards[p];
        attackers |= Attacks.pawn_attacks[(white_attacking) ? black : white][square] & pawns;

        long knights = white_attacking ? chessboard.bitboards[N] : chessboard.bitboards[n];
        attackers |= Attacks.knight_attacks[square] & knights;

        long king = white_attacking ? chessboard.bitboards[K] : chessboard.bitboards[k];
        attackers |= Attacks.king_attacks[square] & king;

        long bishop = white_attacking ? (chessboard.bitboards[B] | chessboard.bitboards[Q]) :
                (chessboard.bitboards[b] | chessboard.bitboards[q]);
        attackers |= Attacks.getBishopAttacks(square, occupancy) & bishop;

        long rook = white_attacking ? (chessboard.bitboards[R] | chessboard.bitboards[Q]) :
                (chessboard.bitboards[r] | chessboard.bitboards[q]);
        attackers |= Attacks.getRookAttacks(square, occupancy) & rook;

        return attackers;
    }

    /**
     * Get whether a piece on square is defended (it doesn't consider a pinned piece)
     *
     * @param chessboard chessboard
     * @param square square
     * @return whether a piece on square is defended (it doesn't consider a pinned piece)
     */
    public static boolean isDefended(Chessboard chessboard, int square) {
        int piece_type = getPieceTypeOnSquare(chessboard, square);
        if (piece_type == -1) return false;

        int is_white = (piece_type <= K) ? white : black;

        return isSquareAttacked(chessboard, square, is_white);
    }

    /**
     * Get whether 3 squares are perfectly aligned
     *
     * @param sq1 square 1
     * @param sq2 square 2
     * @param sq3 square 3
     * @return whether 3 squares are perfectly aligned
     */
    public static boolean isAligned(int sq1, int sq2, int sq3) {
        int r1 = sq1 / 8, f1 = sq1 % 8;
        int r2 = sq2 / 8, f2 = sq2 % 8;
        int r3 = sq3 / 8, f3 = sq3 % 8;

        return (r2 - r1) * (f3 - f1) == (r3 - r1) * (f2 - f1);
    }

    /**
     * Get whether a piece on square is defended (it does consider a pinned piece)
     *
     * @param chessboard chessboard
     * @param square square
     * @return whether a piece on square is defended (it does consider a pinned piece)
     */
    public static boolean isTacticallyDefended(Chessboard chessboard, int square) {
        int pieceType = getPieceTypeOnSquare(chessboard, square);
        if (pieceType == -1) return false;

        boolean is_white = (pieceType <= K);

        long defenders = getAttackersTo(chessboard, square, is_white);
        int kingSq = BitBoardUtils.getLS1BIndex(is_white ? chessboard.bitboards[K] : chessboard.bitboards[k]);

        while (defenders != 0L) {
            int defenderSq = BitBoardUtils.getLS1BIndex(defenders);

            if (!isPinnedToKing(chessboard, defenderSq)) {
                return true;
            }
            else if (isAligned(kingSq, defenderSq, square)) {
                return true;
            }

            defenders = BitBoardUtils.popBit(defenders, defenderSq);
        }

        return false;
    }

    public static String toStringChessboard(Chessboard chessboard) {
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
        sb.append("      Side:     ").append(chessboard.side == white ? "white" : "black").append("\n");

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

        if(chessboard.gameVariants == GameVariants.CRAZY_HOUSE) {
            sb
                    .append("      Pocket:  [")
                    .append("Q".repeat(Math.max(0, chessboard.pocket[Q])))
                    .append("R".repeat(Math.max(0, chessboard.pocket[R])))
                    .append("B".repeat(Math.max(0, chessboard.pocket[B])))
                    .append("N".repeat(Math.max(0, chessboard.pocket[N])))
                    .append("P".repeat(Math.max(0, chessboard.pocket[P])))
                    .append("q".repeat(Math.max(0, chessboard.pocket[q])))
                    .append("r".repeat(Math.max(0, chessboard.pocket[r])))
                    .append("b".repeat(Math.max(0, chessboard.pocket[b])))
                    .append("n".repeat(Math.max(0, chessboard.pocket[n])))
                    .append("p".repeat(Math.max(0, chessboard.pocket[p])))
                    .append("]\n");
        }

        return sb.toString();
    }
}

package com.pepero.jcb.core;

import com.pepero.jcb.api.exception.VariantNotMatchException;
import com.pepero.jcb.bitboard.Attacks;
import com.pepero.jcb.bitboard.BitBoardUtils;
import com.pepero.jcb.constant.BoardSquares;
import com.pepero.jcb.constant.CastlingRights;
import com.pepero.jcb.encode.EncodeMove;
import com.pepero.jcb.hash.Zobrist;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static com.pepero.jcb.constant.BoardSquares.*;
import static com.pepero.jcb.constant.EncodedPieces.*;
import static com.pepero.jcb.constant.MoveCache.CHESSBOARD_UTIL_CACHE;
import static com.pepero.jcb.constant.SideToMove.*;
import static com.pepero.jcb.core.MoveGenerator.isSquareAttacked;
import static com.pepero.jcb.core.MoveGenerator.isSquareAttackedWithOcc;

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
                    last_square = (7 - rank) * 8 + file;

                    chessboard.bitboards[piece] = BitBoardUtils.setBit(chessboard.bitboards[piece], last_square);
                    chessboard.mailbox[last_square] = piece;

                    file++;
                }
            }
        }

        // parse side to move
        chessboard.side = (fenDivided.length > 1 && fenDivided[1].equals("b")) ? black : white;

        // parse castling rights
        chessboard.king_side_rook_file = -1;
        chessboard.queen_side_rook_file = -1;

        if (fenDivided.length > 2 && !fenDivided[2].equals("-")) {
            for (char c : fenDivided[2].toCharArray()) {
                switch (c) {
                    case 'K':
                        chessboard.castle |= CastlingRights.WK;
                        chessboard.king_side_rook_file = 7; // h-file
                        break;
                    case 'Q':
                        chessboard.castle |= CastlingRights.WQ;
                        chessboard.queen_side_rook_file = 0; // a-file
                        break;
                    case 'k':
                        chessboard.castle |= CastlingRights.BK;
                        chessboard.king_side_rook_file = 7; // h-file
                        break;
                    case 'q':
                        chessboard.castle |= CastlingRights.BQ;
                        chessboard.queen_side_rook_file = 0; // a-file
                        break;
                    default:
                        // when Chess 960
                        if (chessboard.isChess960) {
                            if (c >= 'A' && c <= 'H') {
                                int white_king_sq = BitBoardUtils.getLS1BIndex(chessboard.bitboards[K]);
                                int king_file = white_king_sq % 8;

                                int f = c - 'A';
                                chessboard.castle |= (f > king_file) ? CastlingRights.WK : CastlingRights.WQ;
                                if (f > king_file) chessboard.king_side_rook_file = f;
                                else chessboard.queen_side_rook_file = f;
                            } else if (c >= 'a' && c <= 'h') {
                                int black_king_sq = BitBoardUtils.getLS1BIndex(chessboard.bitboards[k]);
                                int king_file = black_king_sq % 8;

                                int f = c - 'a';
                                chessboard.castle |= (f > king_file) ? CastlingRights.BK : CastlingRights.BQ;
                                if (f > king_file) chessboard.king_side_rook_file = f;
                                else chessboard.queen_side_rook_file = f;
                            }
                        }
                        break;
                }
            }
        }

        // parse enpassant square
        chessboard.enpassant = no_sq;

        if (fenDivided.length > 3 && !fenDivided[3].equals("-") && fenDivided[3].length() >= 2) {
            int fileInt = fenDivided[3].charAt(0) - 'a';
            int rankInt = fenDivided[3].charAt(1) - '1';
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

        int checksTokenIndex = -1;
        for (int i = 4; i < fenDivided.length; i++) {
            if (fenDivided[i].matches("\\+?\\d+\\+\\d+")) {
                checksTokenIndex = i;
                break;
            }
        }

        if (chessboard.gameVariants == GameVariants.THREE_CHECK) {
            int whiteChecksGiven = 0;
            int blackChecksGiven = 0;

            if (checksTokenIndex != -1) {
                String token = fenDivided[checksTokenIndex];
                if (token.startsWith("+")) {
                    token = token.substring(1);
                }

                String[] parts = token.split("\\+");
                int first = Integer.parseInt(parts[0]);
                int second = Integer.parseInt(parts[1]);

                if (checksTokenIndex == 4) {
                    // lichess (3+3)
                    whiteChecksGiven = 3 - first;
                    blackChecksGiven = 3 - second;
                } else {
                    // fairy-stockfish (+0+0)
                    whiteChecksGiven = first;
                    blackChecksGiven = second;
                }
            }

            chessboard.check_count[black] = whiteChecksGiven;
            chessboard.check_count[white] = blackChecksGiven;
        }

        // shift 1 index if there is 3 check info
        int halfPlyIndex = (checksTokenIndex == 4) ? 5 : 4;
        int fullMoveIndex = (checksTokenIndex == 4) ? 6 : 5;

        // init half ply
        chessboard.half_ply = (fenDivided.length > halfPlyIndex) ? Integer.parseInt(fenDivided[halfPlyIndex]) : 0;
        int full_move = (fenDivided.length > fullMoveIndex) ? Integer.parseInt(fenDivided[fullMoveIndex]) : 1;

        // init ply
        chessboard.ply = 0;

        // init full move
        chessboard.full_move = (full_move - 1) * 2 + (chessboard.side == white ? 0 : 1);

        // init all occupancies
        chessboard.occupancies[both] |= chessboard.occupancies[white];
        chessboard.occupancies[both] |= chessboard.occupancies[black];

        // init hash key
        chessboard.hash_key = Zobrist.generateHashKey(chessboard);
    }

    public static String getFen(Chessboard chessboard) {
        return getFen(chessboard, FENDialect.LICHESS);
    }

    public static String getFen(Chessboard chessboard, FENDialect dialect){
        StringBuilder fen = new StringBuilder();

        for (int rank = 0; rank < 8; rank++){
            int empty_square = 0;

            for (int file = 0; file < 8; file++){
                int square = (7 - rank) * 8 + file;

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

            fen.append("[").append(pocketStr).append("]");
        }

        // side to move
        fen.append(chessboard.side == white ? " w " : " b ");

        // castling rights
        StringBuilder castle = new StringBuilder();
        if(!chessboard.isChess960) {
            if ((chessboard.castle & CastlingRights.WK) != 0) castle.append("K");
            if ((chessboard.castle & CastlingRights.WQ) != 0) castle.append("Q");
            if ((chessboard.castle & CastlingRights.BK) != 0) castle.append("k");
            if ((chessboard.castle & CastlingRights.BQ) != 0) castle.append("q");
        } else {
            char wkr = (char) ('A' + chessboard.king_side_rook_file);
            char wqr = (char) ('A' + chessboard.queen_side_rook_file);
            char bkr = (char) ('a' + chessboard.king_side_rook_file);
            char bqr = (char) ('a' + chessboard.queen_side_rook_file);

            if ((chessboard.castle & CastlingRights.WK) != 0) castle.append(wkr);
            if ((chessboard.castle & CastlingRights.WQ) != 0) castle.append(wqr);
            if ((chessboard.castle & CastlingRights.BK) != 0) castle.append(bkr);
            if ((chessboard.castle & CastlingRights.BQ) != 0) castle.append(bqr);
        }

        fen.append(castle.isEmpty() ? "-" : castle.toString()).append(" ");

        // enpassant
        if (chessboard.enpassant == BoardSquares.no_sq) {
            fen.append("- ");
        } else {
            fen.append(BoardSquares.square_to_coordinates[chessboard.enpassant]).append(" ");
        }

        // lichess 3 check
        if (chessboard.gameVariants == GameVariants.THREE_CHECK && dialect == FENDialect.LICHESS) {
            int whiteRemaining = 3 - chessboard.check_count[white];
            int blackRemaining = 3 - chessboard.check_count[black];
            fen.append(whiteRemaining).append("+").append(blackRemaining).append(" ");
        }

        fen.append(chessboard.half_ply).append(" ");
        fen.append((chessboard.full_move / 2) + 1);

        // fairy-stockfish 3 check
        if (chessboard.gameVariants == GameVariants.THREE_CHECK && dialect == FENDialect.FAIRY_STOCKFISH) {
            int whiteGiven = chessboard.check_count[black];
            int blackGiven = chessboard.check_count[white];
            fen.append(" +").append(whiteGiven).append("+").append(blackGiven);
        }

        return fen.toString().trim();
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
        return chessboard.mailbox[square];
    }

    /**
     * Get whether king is under attack (depends on side to move)
     * @param chessboard chessboard
     * @return whether king is under attack
     */
    public static boolean isCheck(Chessboard chessboard) {
        if(chessboard.gameVariants == GameVariants.ANTICHESS) return false;
        if(chessboard.gameVariants == GameVariants.HORDE && chessboard.side == white) return false;

        int kingPos = BitBoardUtils.getLS1BIndex(
                chessboard.side == white ?
                        chessboard.bitboards[K] : chessboard.bitboards[k]);

        return isSquareAttacked(chessboard, kingPos, chessboard.side == white ? black : white);
    }

    /**
     * Get checkers square <br>
     * if return is <b>00101010001010</b>, <br>
     * the first checker square is '<b>001010</b>', and the second checker is '<b>100010</b>' <br>
     * and attacking piece count is '<b>10</b>' and count is 1.
     *
     * @param chessboard chessboard
     * @return checkers square
     */
    public static int getChecker(Chessboard chessboard) {
        int kingSquare = BitBoardUtils.getLS1BIndex(
                chessboard.side == white ? chessboard.bitboards[K] : chessboard.bitboards[k]);
        return getChecker(chessboard, kingSquare);
    }

    /**
     * Get checkers square <br>
     * if return is <b>00101010001010</b>, <br>
     * the first checker square is '<b>001010</b>', and the second checker is '<b>100010</b>' <br>
     * and attacking piece count is '<b>10</b>' and count is 1.
     *
     * @param chessboard chessboard
     * @param kingSquare king square
     * @return checkers square
     */
    public static int getChecker(Chessboard chessboard, int kingSquare) {
        int firstAttacker = -1;
        int secondAttacker = -1;
        int oppSide = chessboard.side == white ? black : white;

        // get all checker
        long checkersMask = 0L;

        // pawn
        if (oppSide == white)  {
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
        int move_count = MoveGenerator.generateMoves(chessboard, move_list, true);

        return move_count != 0;
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
     * Get whether this position is three checked <br>
     * if this position isn't three check variant, returns false.
     *
     * @param chessboard chess board
     * @return whether this position is three checked
     */
    public static boolean isThreeCheck(Chessboard chessboard) {
        if(chessboard.gameVariants != GameVariants.THREE_CHECK) return false;

        if(chessboard.check_count[white] >= 3) return true;
        if(chessboard.check_count[black] >= 3) return true;
        return false;
    }

    /**
     * Get whether this position's white/black king gone to the hill
     *
     * @param chessboard chessboard
     * @return whether this position's white/black king gone to the hill
     */
    public static boolean isKingGoneToHill(Chessboard chessboard) {
        if(chessboard.gameVariants != GameVariants.KING_OF_THE_HILL) return false;

        if ((chessboard.bitboards[K] & BoardSquares.CENTER_SQUARES) != 0) return true;
        if ((chessboard.bitboards[k] & BoardSquares.CENTER_SQUARES) != 0) return true;

        return false;
    }

    /**
     * Get whether this horde position's white pieces is all gone (black won)
     *
     * @return whether this horde position's white pieces is all gone
     */
    public static boolean isHordePiecesGone(Chessboard chessboard) {
        if(chessboard.gameVariants != GameVariants.HORDE) return false;

        return chessboard.occupancies[white] == 0L;
    }

    /**
     * Get whether this antichess position overed
     *
     * @return whether this antichess position overed
     */
    public static boolean isAntiChessOver(Chessboard chessboard) {
        if(chessboard.gameVariants != GameVariants.ANTICHESS) return false;

        return
                chessboard.occupancies[chessboard.side] == 0L ||
                        !ChessboardUtils.hasLegalMoves(chessboard);
    }

    /**
     * Get whether this atomic position overed
     *
     * @return whether this antichess position overed
     */
    public static boolean isAtomicOver(Chessboard chessboard) {
        if(chessboard.gameVariants != GameVariants.ATOMIC) return false;

        return chessboard.bitboards[K] == 0L || chessboard.bitboards[k] == 0L;
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

    public static final int WHITE_WON_VALUE = 0;
    public static final int BLACK_WON_VALUE = 1;
    public static final int DREW_VALUE = 2;
    public static final int ONGOING_VALUE = 3;

    /**
     * Get game result for king racing <br>
     * if white won, returns 0, <br>
     * if black won, returns 1, <br>
     * if drew, returns 2, <br>
     * and otherwise (not game over), returns 3
     *
     * @param chessboard chess board
     * @return game result
     */
    public static int getGameResultForRacingKings(Chessboard chessboard) {
        boolean whiteTurn = chessboard.side == white;

        int blackKingSq = BitBoardUtils.getLS1BIndex(chessboard.bitboards[k]);

        boolean wonRaceWhite = (chessboard.bitboards[K] & GOAL_LINE) != 0L;
        boolean wonRaceBlack = (chessboard.bitboards[k] & GOAL_LINE) != 0L;

        // if both have won race
        if(wonRaceWhite && wonRaceBlack) {
            // draw
            return DREW_VALUE;
        }

        // if black won the race, black won
        if(wonRaceBlack /* && !wonRaceWhite can be removed */) {
            return BLACK_WON_VALUE;
        }

        // if black turn, and white won the race, check black king can draw the race
        if(wonRaceWhite /* && !wonRaceBlack can be removed */) {
            if(!whiteTurn) {
                // get black king moves
                long kingAttacks = Attacks.king_attacks[blackKingSq] & ~chessboard.occupancies[black];
                boolean canDrawRace = false;
                while (kingAttacks != 0) {
                    int targetSq = BitBoardUtils.getLS1BIndex(kingAttacks);
                    if(!BitBoardUtils.getBit(GOAL_LINE, targetSq)) {
                        kingAttacks = BitBoardUtils.popBit(kingAttacks, targetSq);
                        continue;
                    }

                    // pop king pos on occupancy because

                    // let's assume this is the position
                    // - R - - - k - -
                    // - - - - - - - -

                    // and the expected is
                    // - R - - 1 k 1 -
                    // - - - - 1 1 1 -

                    // but if we don't pop the king square, the attack is blocked by king square so
                    // - R - - 1 k - -
                    // - - - - 1 1 1 -
                    // and this is not we wanted.
                    long tempOcc = BitBoardUtils.popBit(chessboard.occupancies[both], blackKingSq);
                    boolean isSafe = !isSquareAttackedWithOcc(chessboard, targetSq, white, tempOcc);

                    if (isSafe) {
                        canDrawRace = true;
                        break;
                    }

                    kingAttacks = BitBoardUtils.popBit(kingAttacks, targetSq);
                }

                // if black can't draw race
                if(!canDrawRace) {
                    // white won
                    return WHITE_WON_VALUE;
                }
            } else {
                // if white turn and white is already on finish line, white won
                return WHITE_WON_VALUE;
            }
        }

        return ONGOING_VALUE;
    }

    /**
     * Get this position's repetition count
     *
     * @param chessboard chessboard
     * @param maxCount max repetition count to check (for early function termination)
     * @return this position's repetition count
     */
    public static int getRepetitionCount(Chessboard chessboard, int maxCount) {
        int count = 1;

        int limit = Math.max(0, chessboard.ply - chessboard.half_ply);

        for (int i = chessboard.ply - 2; i >= limit; i -= 2) {
            if (chessboard.hash_key_history[i] == chessboard.hash_key){
                count++;
                if(maxCount <= count) return maxCount;
            }
        }

        return count;
    }

    /**
     * Get whether this position is repetition draw (starts at 'rootPly')
     * <p>
     * if the whole history repetition count is more than 2, return true, <br>
     * if after root ply history repetition count is more than 1, return true. <br>
     * if neither, return false.
     *
     * @param chessboard chessboard
     * @param rootPly start counting ply
     * @return whether this position is repetition draw
     */
    public static boolean isRepetitionDraw(Chessboard chessboard, int rootPly) {
        int count = 1;

        int limit = Math.max(0, chessboard.ply - chessboard.half_ply);

        for (int i = chessboard.ply - 2; i >= limit; i -= 2) {
            if (chessboard.hash_key_history[i] == chessboard.hash_key) {
                if (i >= rootPly) {
                    return true;
                }

                count++;

                if (count >= 3) {
                    return true;
                }
            }
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
            long bitboardPiece = chessboard.bitboards[piece];
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
                int square = (7 - rank) * 8 + file;
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

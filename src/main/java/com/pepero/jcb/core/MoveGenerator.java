package com.pepero.jcb.core;

import com.pepero.jcb.bitboard.Attacks;
import com.pepero.jcb.bitboard.BitBoardUtils;
import com.pepero.jcb.constant.CastlingRights;
import com.pepero.jcb.constant.EncodedPieces;
import com.pepero.jcb.constant.MoveCache;
import com.pepero.jcb.encode.EncodeMove;
import com.pepero.jcb.hash.Zobrist;

import java.util.Arrays;

import static com.pepero.jcb.constant.BoardSquares.*;
import static com.pepero.jcb.constant.EncodedPieces.*;
import static com.pepero.jcb.constant.SideToMove.*;

public class MoveGenerator {

    /*
                               castling   move     in      in
                                  right update     binary  decimal

     king & rooks didn't move:     1111 & 1111  =  1111    15

            white king  moved:     1111 & 1100  =  1100    12
      white king's rook moved:     1111 & 1110  =  1110    14
     white queen's rook moved:     1111 & 1101  =  1101    13

             black king moved:     1111 & 0011  =  1011    3
      black king's rook moved:     1111 & 1011  =  1011    11
     black queen's rook moved:     1111 & 0111  =  0111    7

    */

    // move types
    public static final int ILLEGAL_MOVE = -1;

    /**
     * Make a move on chess board
     * @param chessboard chess board
     * @param move encoded move
     * @return whether this move is successfully generated or not
     * ( if not generated, returns false. otherwise, returns true)
     */
    public static boolean makeMove(Chessboard chessboard, int move){
        // set chessboard history move data
        chessboard.enpassant_history[chessboard.ply] = chessboard.enpassant;
        chessboard.castle_history[chessboard.ply] = chessboard.castle;
        chessboard.half_ply_history[chessboard.ply] = chessboard.half_ply;
        chessboard.hash_key_history[chessboard.ply] = chessboard.hash_key;
        chessboard.captured_piece_history[chessboard.ply] = -1;

        // parse move
        int source_square = EncodeMove.getMoveSource(move);
        int target_square = EncodeMove.getMoveTarget(move);
        int piece = EncodeMove.getMovePiece(move);
        int promoted_piece = EncodeMove.getMovePromoted(move);
        boolean capture = EncodeMove.getMoveCapture(move);
        boolean double_push = EncodeMove.getMoveDouble(move);
        boolean enpass = EncodeMove.getMoveEnpassant(move);
        boolean castling = EncodeMove.getMoveCastling(move);

        // castling
        if(!castling) {
            // move piece
            chessboard.bitboards[piece] = BitBoardUtils.popBit(chessboard.bitboards[piece], source_square);
            chessboard.bitboards[piece] = BitBoardUtils.setBit(chessboard.bitboards[piece], target_square);

            // hash piece
            chessboard.hash_key ^= Zobrist.piece_keys[piece][source_square]; // remove piece from source square in hash key
            chessboard.hash_key ^= Zobrist.piece_keys[piece][target_square]; // set piece to the target square in hash key
        } else {
            int king_target, rook_target, rook_piece;
            if (chessboard.side == white) {
                rook_piece = R;
                if (target_square > source_square) {
                    king_target = g1;
                    rook_target = f1;
                }
                else {
                    king_target = c1;
                    rook_target = d1;
                }
            } else {
                rook_piece = r;
                if (target_square > source_square) {
                    king_target = g8;
                    rook_target = f8;
                }
                else {
                    king_target = c8;
                    rook_target = d8;
                }
            }

            // king
            chessboard.bitboards[piece] = BitBoardUtils.popBit(chessboard.bitboards[piece], source_square);
            chessboard.bitboards[piece] = BitBoardUtils.setBit(chessboard.bitboards[piece], king_target);
            chessboard.hash_key ^= Zobrist.piece_keys[piece][source_square];
            chessboard.hash_key ^= Zobrist.piece_keys[piece][king_target];

            // rook
            chessboard.bitboards[rook_piece] = BitBoardUtils.popBit(chessboard.bitboards[rook_piece], target_square);
            chessboard.bitboards[rook_piece] = BitBoardUtils.setBit(chessboard.bitboards[rook_piece], rook_target);
            chessboard.hash_key ^= Zobrist.piece_keys[rook_piece][target_square];
            chessboard.hash_key ^= Zobrist.piece_keys[rook_piece][rook_target];
        }

        // handling capture moves
        if (capture){
            // pick up bitboard piece index ranges depending on side
            int start_piece, end_piece;

            // white to move
            if(chessboard.side == white){
                start_piece = p;
                end_piece = k;
            }

            // black to move
            else {
                start_piece = P;
                end_piece = K;
            }

            // loop over bitboards opposite to the current side to move
            for(int bb_piece = start_piece; bb_piece <= end_piece; bb_piece++){
                // if there's a piece on the target square
                if(BitBoardUtils.getBit(chessboard.bitboards[bb_piece], target_square)){
                    // remove it from the corresponding bitboard
                    chessboard.bitboards[bb_piece]
                            = BitBoardUtils.popBit(chessboard.bitboards[bb_piece], target_square);

                    // remove the piece from hash key
                    chessboard.hash_key ^= Zobrist.piece_keys[bb_piece][target_square];

                    // if move is capture
                    chessboard.captured_piece_history[chessboard.ply] = bb_piece;

                    break;
                }
            }
        }

        // handle pawn promotions
        if (promoted_piece != 0){
            // erase the pawn from the target square
                /*
                chessboard.bitboards[(chessboard.side == white) ? P : p] =
                        BitBoardUtils.popBit(chessboard.bitboards[(chessboard.side == white) ? P : p], target_square);
                */

            // white to move
            if (chessboard.side == white){
                // erase the pawn from the target square
                chessboard.bitboards[P] = BitBoardUtils.popBit(chessboard.bitboards[P], target_square);

                // remove pawn from hash key
                chessboard.hash_key ^= Zobrist.piece_keys[P][target_square];
            }
            // black to move
            else {
                // erase the pawn from the target square
                chessboard.bitboards[p] = BitBoardUtils.popBit(chessboard.bitboards[p], target_square);

                // remove pawn from hash key
                chessboard.hash_key ^= Zobrist.piece_keys[p][target_square];
            }

            // set up promoted piece on chess board
            chessboard.bitboards[promoted_piece] =
                    BitBoardUtils.setBit(chessboard.bitboards[promoted_piece], target_square);

            // add promoted piece into the hash key
            chessboard.hash_key ^= Zobrist.piece_keys[promoted_piece][target_square];
        }

        // handle enpassant captures
        if (enpass){
            // white to move
            if (chessboard.side == white){
                // remove captured pawn
                chessboard.bitboards[p] = BitBoardUtils.popBit(chessboard.bitboards[p], target_square + 8);

                // remove pawn from hash key
                chessboard.hash_key ^= Zobrist.piece_keys[p][target_square + 8];
            }

            // black to move
            else {
                // remove captured pawn
                chessboard.bitboards[P] = BitBoardUtils.popBit(chessboard.bitboards[P], target_square - 8);

                // remove pawn from hash key
                chessboard.hash_key ^= Zobrist.piece_keys[P][target_square - 8];
            }
        }

        // hash enpassant if available (remove enpassant from hash key)
        if(chessboard.enpassant != no_sq){
            chessboard.hash_key ^= Zobrist.enpassant_keys[chessboard.enpassant];
        }

        // reset enpassant square
        chessboard.enpassant = no_sq;

        // handle double pawn push
        if (double_push){
            // set enpassant square depending on the side to move
            //chessboard.enpassant = (chessboard.side == white) ? target_square + 8 : target_square - 8;

            // white to move
            if (chessboard.side == white){
                // set enpassant square
                chessboard.enpassant = target_square + 8;

                // hash enpassant
                chessboard.hash_key ^= Zobrist.enpassant_keys[target_square + 8];
            }

            // black to move
            else {
                // set enpassant square
                chessboard.enpassant = target_square - 8;

                // hash enpassant
                chessboard.hash_key ^= Zobrist.enpassant_keys[target_square - 8];
            }
        }

        // remove castling rights if king moved
        chessboard.hash_key ^= Zobrist.castling_keys[chessboard.castle];

        if (piece == K) chessboard.castle &= ~(CastlingRights.WK | CastlingRights.WQ);
        if (piece == k) chessboard.castle &= ~(CastlingRights.BK | CastlingRights.BQ);

        int wk_rook_sq = (chessboard.king_side_rook_file != -1) ? chessboard.king_side_rook_file + 56 : h1;
        int wq_rook_sq = (chessboard.queen_side_rook_file != -1) ? chessboard.queen_side_rook_file + 56 : a1;
        int bk_rook_sq = (chessboard.king_side_rook_file != -1) ? chessboard.king_side_rook_file : h8;
        int bq_rook_sq = (chessboard.queen_side_rook_file != -1) ? chessboard.queen_side_rook_file : a8;

        if (source_square == wk_rook_sq || target_square == wk_rook_sq) chessboard.castle &= ~CastlingRights.WK;
        if (source_square == wq_rook_sq || target_square == wq_rook_sq) chessboard.castle &= ~CastlingRights.WQ;
        if (source_square == bk_rook_sq || target_square == bk_rook_sq) chessboard.castle &= ~CastlingRights.BK;
        if (source_square == bq_rook_sq || target_square == bq_rook_sq) chessboard.castle &= ~CastlingRights.BQ;

        chessboard.hash_key ^= Zobrist.castling_keys[chessboard.castle];

        // reset occupancies
        Arrays.fill(chessboard.occupancies,0L);

        // loop over white pieces bitboards
        for (int bb_piece = P; bb_piece <= K; bb_piece++)
            // update white occupancies
            chessboard.occupancies[white] |= chessboard.bitboards[bb_piece];

        // loop over black pieces bitboards
        for (int bb_piece = p; bb_piece <= k; bb_piece++)
            // update black occupancies
            chessboard.occupancies[black] |= chessboard.bitboards[bb_piece];

        // update both sides occupancies
        chessboard.occupancies[both] |= chessboard.occupancies[white];
        chessboard.occupancies[both] |= chessboard.occupancies[black];

        // change side
        chessboard.side ^= 1;

        // hash side
        chessboard.hash_key ^= Zobrist.side_key;

        if (
                EncodeMove.getMoveCapture(move) ||
                        EncodeMove.getMovePiece(move) == p ||
                        EncodeMove.getMovePiece(move) == P ) {
            chessboard.half_ply = 0;
        } else {
            chessboard.half_ply++;
        }

        chessboard.ply++;

        // ---------------------------------
        // debug hash key incremental update
        // ---------------------------------

        // build hash key for the updated position (after move is made) from scratch
        //long hash_from_scratch = Zobrist.generateHashKey(chessboard);

        // in case if the hash key built from scratch doesn't match
        // the one that was incrementally updated, we interrupt execution
            /*if(chessboard.hash_key != hash_from_scratch){
                System.out.println("\n\n Make move \n");
                System.out.println("move: " + EncodeMove.getMoveString(move));
                ChessBoardUtils.printChessBoard(chessboard);
                System.out.println("hash key should be: " + Long.toHexString(hash_from_scratch));
                new Scanner(System.in).nextLine();
            } */

        // make sure that king has not been exposed into a check
        if (isSquareAttacked(chessboard,
                (chessboard.side == white) ? BitBoardUtils.getLS1BIndex(chessboard.bitboards[k])
                        : BitBoardUtils.getLS1BIndex(chessboard.bitboards[K])
                , chessboard.side)){
            unmakeMove(chessboard, move);

            // return illegal move
            return false;
        }

        else {
            // return legal move
            return true;
        }
    }

    /**
     * Unmake Move on chessboard
     *
     * @param chessboard chessboard
     * @param move encoded move
     */
    public static void unmakeMove(Chessboard chessboard, int move) {
        // decrease ply
        chessboard.ply--;
        chessboard.side ^= 1;

        // get enpassant square, castle, half_ply, hash_key
        chessboard.enpassant = chessboard.enpassant_history[chessboard.ply];
        chessboard.castle = chessboard.castle_history[chessboard.ply];
        chessboard.half_ply = chessboard.half_ply_history[chessboard.ply];
        chessboard.hash_key = chessboard.hash_key_history[chessboard.ply];

        // get captured piece
        int captured_piece = chessboard.captured_piece_history[chessboard.ply];

        int source_square = EncodeMove.getMoveSource(move);
        int target_square = EncodeMove.getMoveTarget(move);
        int piece = EncodeMove.getMovePiece(move);
        int promoted_piece = EncodeMove.getMovePromoted(move);
        boolean capture = EncodeMove.getMoveCapture(move);
        boolean enpass = EncodeMove.getMoveEnpassant(move);
        boolean castling = EncodeMove.getMoveCastling(move);

        // unmake castling
        if (castling) {
            int k_target, r_target, rook_piece;
            if (chessboard.side == white) {
                rook_piece = R;
                if (target_square > source_square) { // king side castling
                    k_target = g1;
                    r_target = f1;
                }
                else { // queen side castling
                    k_target = c1;
                    r_target = d1;
                }
            } else {
                rook_piece = r;
                if (target_square > source_square) { // king side castling
                    k_target = g8; r_target = f8;
                }
                else { // queen side castling
                    k_target = c8;
                    r_target = d8;
                }
            }

            // unmake king
            chessboard.bitboards[piece] = BitBoardUtils.popBit(chessboard.bitboards[piece], k_target);
            chessboard.bitboards[piece] = BitBoardUtils.setBit(chessboard.bitboards[piece], source_square);

            // unmake rook
            chessboard.bitboards[rook_piece] = BitBoardUtils.popBit(chessboard.bitboards[rook_piece], r_target);
            chessboard.bitboards[rook_piece] = BitBoardUtils.setBit(chessboard.bitboards[rook_piece], target_square);
        } else {
            // unmake piece
            if (promoted_piece != 0) {
                chessboard.bitboards[promoted_piece] = BitBoardUtils.popBit(chessboard.bitboards[promoted_piece], target_square);
            } else {
                chessboard.bitboards[piece] = BitBoardUtils.popBit(chessboard.bitboards[piece], target_square);
            }
            chessboard.bitboards[piece] = BitBoardUtils.setBit(chessboard.bitboards[piece], source_square);
        }

        // if normal capture move
        if (capture && !enpass) {
            chessboard.bitboards[captured_piece] = BitBoardUtils.setBit(chessboard.bitboards[captured_piece], target_square);
        }

        // if enpassant
        if (enpass) {
            if (chessboard.side == white) {
                chessboard.bitboards[p] = BitBoardUtils.setBit(chessboard.bitboards[p], target_square + 8);
            } else {
                chessboard.bitboards[P] = BitBoardUtils.setBit(chessboard.bitboards[P], target_square - 8);
            }
        }

        // set occupancies
        Arrays.fill(chessboard.occupancies, 0L);
        for (int bb_piece = P; bb_piece <= K; bb_piece++) {
            chessboard.occupancies[white] |= chessboard.bitboards[bb_piece];
        }
        for (int bb_piece = p; bb_piece <= k; bb_piece++) {
            chessboard.occupancies[black] |= chessboard.bitboards[bb_piece];
        }
        chessboard.occupancies[both] |= chessboard.occupancies[white];
        chessboard.occupancies[both] |= chessboard.occupancies[black];
    }

    /**
     * This method checks whether the square is attacked or not
     *
     * @param chessboard the chessboard
     * @param square the square which will be checked this is attacked or not
     * @param side if the side is white, this method will check the white piece(s) is attacking
     *             if the side is black, this method will check the black piece(s) is attacking
     * @return return the square is attacked or not
     */
    public static boolean isSquareAttacked(Chessboard chessboard, int square, int side){
        // attacked by white pawns
        if(side == white && (Attacks.pawn_attacks[black][square] & chessboard.bitboards[P]) != 0) {
            return true;
        }

        // attacked by black pawns
        if(side == black && (Attacks.pawn_attacks[white][square] & chessboard.bitboards[p]) != 0) {
            return true;
        }

        // attacked by knights
        if ((Attacks.knight_attacks[square] & (side == white ?
                chessboard.bitboards[N] : chessboard.bitboards[n])) != 0) return true;

        // attacked by bishops
        if ((Attacks.getBishopAttacks(square, chessboard.occupancies[both])
                & (side == white ? chessboard.bitboards[B] : chessboard.bitboards[b])) != 0) return true;

        // attacked by rooks
        if ((Attacks.getRookAttacks(square, chessboard.occupancies[both])
                & (side == white ? chessboard.bitboards[R] : chessboard.bitboards[r])) != 0) return true;

        // attacked by queens
        if ((Attacks.getQueenAttacks(square, chessboard.occupancies[both])
                & (side == white ? chessboard.bitboards[Q] : chessboard.bitboards[q])) != 0) return true;

        // attacked by kings
        if ((Attacks.king_attacks[square] & (side == white ?
                chessboard.bitboards[K] : chessboard.bitboards[k])) != 0) return true;

        // by default return false
        return false;
    }

    /**
     * Prints attacked squares
     *
     * @param chessboard chess board
     * @param side if side is white, this method check all black pieces attacking squares
     *             if side is black, this method check all white pieces attacking squares
     */
    public static void printAttackedSquares(Chessboard chessboard, int side){
        StringBuilder sb = new StringBuilder();

        // loop over board ranks
        for(int rank = 0; rank < 8; rank++){
            // loop over board files
            for(int file = 0; file < 8; file++){
                // init square
                int square = rank * 8 + file;

                // print ranks
                if (file == 0)
                    sb.append("  ").append(8 - rank).append("   ");

                // check whether the current square is attacked or not
                sb.append(isSquareAttacked(chessboard, square, side) ? 1 : 0).append(" ");
            }

            // print new line every rank
            sb.append("\n");
        }

        // print board files
        sb.append("\n      a b c d e f g h \n\n");

        System.out.print(sb);
    }

    /**
     * Add move into move array and ++ the moveCount
     *
     * Usage : moveCount = addMove(int[] moveArray, int moveCount, int moveData);
     *
     * @param moveArray move array
     * @param moveCount move index
     * @param moveData encoded move data
     * @return move count + 1
     */
    public static int addMove(int[] moveArray, int moveCount, int moveData){
        moveArray[moveCount] = moveData;
        return ++moveCount;
    }

    /**
     * Generate Moves
     * @param chessboard chess board
     * @param moveArray the result array
     * @return move counts
     */
    public static int generateMoves(Chessboard chessboard, int[] moveArray){
        int moveCount = 0;

        // define source & target squares
        int source_square, target_square;

        // define current piece's bitboard copy, occupancy, attacks bitboards
        long bitboard, occupancy, attacks;

        // init occupancy (all pieces on board)
        occupancy = chessboard.occupancies[both];

        // loop over all the bitboards
        for (int piece = P; piece <= k; piece++){
            // init piece bitboard copy
            bitboard = chessboard.bitboards[piece];

            // generate white pawns & white king castling moves
            if (chessboard.side == white) {
                // pick up white pawn bitboards index
                if (piece == P) {
                    // loop over white pawns within white pawn bitboard
                    while (bitboard != 0) {
                        // init source square
                        source_square = BitBoardUtils.getLS1BIndex(bitboard);

                        // init target square
                        target_square = source_square - 8;

                        // generate quite pawn moves
                        if (!(target_square < a8) && !BitBoardUtils.getBit(occupancy, target_square)) {
                            // pawn promotion
                            if (source_square >= a7 && source_square <= h7) {
                                addPawnPromotionMoves(moveArray, moveCount, source_square, target_square,
                                        piece, false);
                                moveCount+=4;
                            } else {
                                // one square ahead pawn move
                                moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(source_square, target_square, piece, 0,
                                        false, false, false, false));

                                // two squares ahead pawn move
                                if ((source_square >= a2 && source_square <= h2) && !BitBoardUtils.getBit(occupancy, target_square - 8))
                                    moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(source_square, target_square - 8, piece, 0,
                                            false, true, false, false));
                            }
                        }

                        // bitwise AND pawn attacks with black occupancies
                        attacks = Attacks.pawn_attacks[white][source_square] & chessboard.occupancies[black];

                        // loop over valid captures
                        while (attacks != 0) {
                            // init target square
                            target_square = BitBoardUtils.getLS1BIndex(attacks);

                            // pawn promotion capture
                            if (source_square >= a7 && source_square <= h7) {
                                addPawnPromotionMoves(moveArray, moveCount, source_square, target_square,
                                        piece, true);
                                moveCount+=4;
                            } else {
                                // normal pawn capture
                                moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(source_square, target_square, piece, 0,
                                        true, false, false, false));
                            }

                            // pop ls1b of the pawn attacks
                            attacks = BitBoardUtils.popBit(attacks, target_square);
                        }

                        // generate enpassant captures
                        if(chessboard.enpassant != no_sq){
                            // look up pawn attacks and bitwise AND with enpassant square (bit)
                            long enpassant_attacks = Attacks.pawn_attacks[chessboard.side][source_square] & (1L << chessboard.enpassant);

                            // make sure enpassant capture available
                            if (enpassant_attacks != 0){
                                // init enpassant capture target square
                                int target_enpassant = BitBoardUtils.getLS1BIndex(enpassant_attacks);

                                moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(source_square, target_enpassant, piece, 0,
                                        true, false, true, false));
                            }
                        }

                        // pop ls1b from piece bitboard copy
                        bitboard = BitBoardUtils.popBit(bitboard, source_square);
                    }
                }

                // castling moves
                if (piece == K){
                    source_square = BitBoardUtils.getLS1BIndex(chessboard.bitboards[K]);

                    // king side castling is available
                    if ((chessboard.castle & CastlingRights.WK) != 0 && chessboard.king_side_rook_file != -1) {
                        int r_sq = chessboard.king_side_rook_file + 56;
                        boolean empty = true;

                        int min_king_rook_pos = Math.min(source_square, r_sq);
                        int max_king_rook_pos = Math.max(source_square, r_sq);

                        // make sure the square between king and king's rook are empty
                        for (int sq = min_king_rook_pos + 1; sq < max_king_rook_pos; sq++) {
                            if (BitBoardUtils.getBit(occupancy, sq)) {
                                empty = false;
                                break;
                            }
                        }

                        // make sure the final squares are empty (excluding king and rook themselves)
                        if (empty) {
                            if (f1 != source_square && f1 != r_sq && BitBoardUtils.getBit(occupancy, f1)) {
                                empty = false;
                            }

                            if (g1 != source_square && g1 != r_sq && BitBoardUtils.getBit(occupancy, g1)) {
                                empty = false;
                            }
                        }

                        // make sure king's path is not under attacks
                        if (empty) {
                            boolean safe = true;

                            int min_king_pos = Math.min(source_square, g1);
                            int max_king_pos = Math.max(source_square, g1);

                            for (int square = min_king_pos; square <= max_king_pos; square++) {
                                if (isSquareAttacked(chessboard, square, black)) {
                                    safe = false;
                                    break;
                                }
                            }
                            if (safe) {
                                moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(source_square, r_sq,
                                        piece, 0, false, false, false, true));
                            }
                        }
                    }

                    // queen side castling is available
                    if ((chessboard.castle & CastlingRights.WQ) != 0 && chessboard.queen_side_rook_file != -1) {
                        int r_sq = chessboard.queen_side_rook_file + 56;
                        boolean empty = true;

                        int min_king_rook_pos = Math.min(source_square, r_sq);
                        int max_king_rook_pos = Math.max(source_square, r_sq);

                        // make sure the square between king and queen's rook are empty
                        for (int sq = min_king_rook_pos + 1; sq < max_king_rook_pos; sq++) {
                            if (BitBoardUtils.getBit(occupancy, sq)) {
                                empty = false;
                                break;
                            }
                        }

                        // make sure the final squares are empty (excluding king and rook themselves)
                        if (empty) {
                            if (c1 != source_square && c1 != r_sq && BitBoardUtils.getBit(occupancy, c1)) {
                                empty = false;
                            }

                            if (d1 != source_square && d1 != r_sq && BitBoardUtils.getBit(occupancy, d1)) {
                                empty = false;
                            }
                        }

                        // make sure king's path is not under attacks
                        if (empty) {
                            boolean safe = true;

                            int min_king_pos = Math.min(source_square, c1);
                            int max_king_pos = Math.max(source_square, c1);

                            for (int sq = min_king_pos; sq <= max_king_pos; sq++) {
                                if (isSquareAttacked(chessboard, sq, black)) {
                                    safe = false;
                                    break;
                                }
                            }
                            if (safe) {
                                moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(source_square, r_sq,
                                        piece, 0, false, false, false, true));
                            }
                        }
                    }
                }
            }

            // generate black pawns & black king castling moves
            else {
                // pick up black pawn bitboards index
                if (piece == p) {
                    // loop over black pawns within black pawn bitboard
                    while (bitboard != 0) {
                        // init source square
                        source_square = BitBoardUtils.getLS1BIndex(bitboard);

                        // init target square
                        target_square = source_square + 8;

                        // generate quite pawn moves
                        if (!(target_square > h1) && !BitBoardUtils.getBit(occupancy, target_square)) {
                            // pawn promotion
                            if (source_square >= a2 && source_square <= h2) {
                                addPawnPromotionMoves(moveArray, moveCount, source_square, target_square,
                                        piece, false);
                                moveCount+=4;

                            } else {
                                // one square ahead pawn move
                                moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(source_square, target_square, piece, 0,
                                        false, false, false, false));

                                // two squares ahead pawn move
                                if ((source_square >= a7 && source_square <= h7) && !BitBoardUtils.getBit(occupancy, target_square + 8))
                                    moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(source_square, target_square + 8, piece, 0,
                                            false, true, false, false));
                            }
                        }

                        // bitwise AND pawn attacks with black occupancies
                        attacks = Attacks.pawn_attacks[black][source_square] & chessboard.occupancies[white];

                        // loop over valid captures
                        while (attacks != 0) {
                            // init target square
                            target_square = BitBoardUtils.getLS1BIndex(attacks);

                            // pawn promotion capture
                            if (source_square >= a2 && source_square <= h2) {
                                addPawnPromotionMoves(moveArray, moveCount, source_square, target_square,
                                        piece, true);
                                moveCount+=4;
                            } else {
                                // normal pawn capture
                                moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(source_square, target_square, piece, 0,
                                        true, false, false, false));
                            }

                            // pop ls1b of the pawn attacks
                            attacks = BitBoardUtils.popBit(attacks, target_square);
                        }

                        // generate enpassant captures
                        if(chessboard.enpassant != no_sq){
                            // look up pawn attacks and bitwise AND with enpassant square (bit)
                            long enpassant_attacks = Attacks.pawn_attacks[chessboard.side][source_square] & (1L << chessboard.enpassant);

                            // make sure enpassant capture available
                            if (enpassant_attacks != 0){
                                // init enpassant capture target square
                                int target_enpassant = BitBoardUtils.getLS1BIndex(enpassant_attacks);

                                moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(source_square, target_enpassant, piece, 0,
                                        true, false, true, false));
                            }
                        }

                        // pop ls1b from piece bitboard copy
                        bitboard = BitBoardUtils.popBit(bitboard, source_square);
                    }
                }

                // castling moves
                if (piece == k){
                    source_square = BitBoardUtils.getLS1BIndex(chessboard.bitboards[k]);

                    // king side castling is available
                    if ((chessboard.castle & CastlingRights.BK) != 0 && chessboard.king_side_rook_file != -1) {
                        int r_sq = chessboard.king_side_rook_file;
                        boolean empty = true;

                        int min_king_rook_pos = Math.min(source_square, r_sq);
                        int max_king_rook_pos = Math.max(source_square, r_sq);

                        // make sure the square between king and king's rook are empty
                        for (int sq = min_king_rook_pos + 1; sq < max_king_rook_pos; sq++) {
                            if (BitBoardUtils.getBit(occupancy, sq)) {
                                empty = false;
                                break;
                            }
                        }

                        // make sure the final squares are empty (excluding king and rook themselves)
                        if (empty) {
                            if (f8 != source_square && f8 != r_sq && BitBoardUtils.getBit(occupancy, f8)) {
                                empty = false;
                            }

                            if (g8 != source_square && g8 != r_sq && BitBoardUtils.getBit(occupancy, g8)) {
                                empty = false;
                            }
                        }

                        // make sure king's path is not under attacks
                        if (empty) {
                            boolean safe = true;

                            int min_king_pos = Math.min(source_square, g8);
                            int max_king_pos = Math.max(source_square, g8);

                            for (int sq = min_king_pos; sq <= max_king_pos; sq++) {
                                if (isSquareAttacked(chessboard, sq, white)) {
                                    safe = false;
                                    break;
                                }
                            }
                            if (safe) {
                                moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(source_square, r_sq,
                                        piece, 0, false, false, false, true));
                            }
                        }
                    }

                    // queen side castling is available
                    if ((chessboard.castle & CastlingRights.BQ) != 0 && chessboard.queen_side_rook_file != -1) {
                        int r_sq = chessboard.queen_side_rook_file;
                        boolean empty = true;

                        int min_king_rook_pos = Math.min(source_square, r_sq);
                        int max_king_rook_pos = Math.max(source_square, r_sq);

                        // make sure the square between king and queen's rook are empty
                        for (int sq = min_king_rook_pos + 1; sq < max_king_rook_pos; sq++) {
                            if (BitBoardUtils.getBit(occupancy, sq)) {
                                empty = false;
                                break;
                            }
                        }

                        // make sure the final squares are empty (excluding king and rook themselves)
                        if (empty) {
                            if (c8 != source_square && c8 != r_sq && BitBoardUtils.getBit(occupancy, c8)) {
                                empty = false;
                            }

                            if (d8 != source_square && d8 != r_sq && BitBoardUtils.getBit(occupancy, d8)) {
                                empty = false;
                            }
                        }

                        // make sure king's path is not under attacks
                        if (empty) {
                            boolean safe = true;

                            int min_king_pos = Math.min(source_square, c8);
                            int max_king_pos = Math.max(source_square, c8);

                            for (int sq = min_king_pos; sq <= max_king_pos; sq++) {
                                if (isSquareAttacked(chessboard, sq, white)) {
                                    safe = false;
                                    break;
                                }
                            }
                            if (safe) {
                                moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(source_square, r_sq,
                                        piece, 0, false, false, false, true));
                            }
                        }
                    }
                }
            }

            // generate knight moves
            if((chessboard.side == white) ? piece == N : piece == n){
                // loop over source square of piece bitboard copy
                while (bitboard != 0){
                    // init source square
                    source_square = BitBoardUtils.getLS1BIndex(bitboard);

                    // init piece attacks in order to get set of target squares
                    attacks = Attacks.knight_attacks[source_square] & ((chessboard.side == white)
                            ? ~chessboard.occupancies[white] : ~chessboard.occupancies[black]);

                    // loop over target squares available from generated attacks
                    while (attacks != 0){
                        // init target square
                        target_square = BitBoardUtils.getLS1BIndex(attacks);


                        boolean isCapture = BitBoardUtils.getBit(
                                (chessboard.side == white) ? chessboard.occupancies[black] : chessboard.occupancies[white],
                                target_square
                        );

                        moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(
                                source_square, target_square, piece, 0, isCapture, false, false, false));


                        // pop ls1b of the current attacks set
                        attacks = BitBoardUtils.popBit(attacks, target_square);
                    }

                    // pop ls1b of the current piece bitboard copy
                    bitboard = BitBoardUtils.popBit(bitboard, source_square);
                }
            }

            // generate slider pieces
            else if (
                    (chessboard.side == white && (piece == B || piece == R || piece == Q)) ||
                            (chessboard.side == black && (piece == b || piece == r || piece == q))
            ) {
                while (bitboard != 0) {
                    source_square = BitBoardUtils.getLS1BIndex(bitboard);

                    if (piece == B || piece == b) {
                        attacks = Attacks.getBishopAttacks(source_square, occupancy);
                    } else if (piece == R || piece == r) {
                        attacks = Attacks.getRookAttacks(source_square, occupancy);
                    } else {
                        attacks = Attacks.getQueenAttacks(source_square, occupancy);
                    }

                    attacks &= ((chessboard.side == white) ? ~chessboard.occupancies[white] : ~chessboard.occupancies[black]);

                    while (attacks != 0) {
                        target_square = BitBoardUtils.getLS1BIndex(attacks);

                        boolean isCapture = BitBoardUtils.getBit(
                                (chessboard.side == white) ? chessboard.occupancies[black] : chessboard.occupancies[white],
                                target_square
                        );

                        moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(
                                source_square, target_square, piece, 0, isCapture, false, false, false));

                        attacks = BitBoardUtils.popBit(attacks, target_square);
                    }
                    bitboard = BitBoardUtils.popBit(bitboard, source_square);
                }
            }

            // generate king moves
            else if((chessboard.side == white) ? piece == K : piece == k){
                // loop over source square of piece bitboard copy
                while (bitboard != 0){
                    // init source square
                    source_square = BitBoardUtils.getLS1BIndex(bitboard);

                    // init piece attacks in order to get set of target squares
                    attacks = Attacks.king_attacks[source_square]
                            & ((chessboard.side == white)
                            ? ~chessboard.occupancies[white] : ~chessboard.occupancies[black]);

                    // loop over target squares available from generated attacks
                    while (attacks != 0){
                        // init target square
                        target_square = BitBoardUtils.getLS1BIndex(attacks);

                        boolean isCapture = BitBoardUtils.getBit(
                                (chessboard.side == white) ? chessboard.occupancies[black] : chessboard.occupancies[white],
                                target_square
                        );

                        moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(
                                source_square, target_square, piece, 0, isCapture, false, false, false));


                        // pop ls1b of the current attacks set
                        attacks = BitBoardUtils.popBit(attacks, target_square);
                    }

                    // pop ls1b of the current piece bitboard copy
                    bitboard = BitBoardUtils.popBit(bitboard, source_square);
                }
            }
        }

        return moveCount;
    }

    private static int addPawnPromotionMoves(int[] moveArray, int moveCount, int source, int target, int piece, boolean isCapture) {
        int turn = (piece == P) ? white : black;
        int q = (turn == white) ? EncodedPieces.Q : EncodedPieces.q;
        int r = (turn == white) ? EncodedPieces.R : EncodedPieces.r;
        int b = (turn == white) ? EncodedPieces.B : EncodedPieces.b;
        int n = (turn == white) ? EncodedPieces.N : EncodedPieces.n;

        moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(source, target, piece, q, isCapture,
                false, false, false));
        moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(source, target, piece, r, isCapture,
                false, false, false));
        moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(source, target, piece, b, isCapture,
                false, false, false));
        moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(source, target, piece, n, isCapture,
                false, false, false));

        return moveCount;
    }

    public static boolean isLegalMove(Chessboard chessboard, int move) {
        int[] move_list = MoveCache.MOVE_GENERATOR_CACHE.get();
        int move_count = MoveGenerator.generateMoves(chessboard, move_list);

        for (int count = 0; count < move_count; count++) {
            int possible_move = move_list[count];
            if (EncodeMove.getMoveSource(possible_move) == EncodeMove.getMoveSource(move)
                    && EncodeMove.getMoveTarget(possible_move) == EncodeMove.getMoveTarget(move)
                    && (EncodeMove.getMovePromoted(possible_move) == EncodeMove.getMovePromoted(move))) {
                if (!MoveGenerator.makeMove(chessboard, possible_move)) {
                    return false;
                }

                unmakeMove(chessboard, possible_move);
                return true;
            }
        }

        return false;
    }

    public static int isLegalMove(Chessboard chessboard,int source_square, int target_square, int promotion_type) {
        int[] move_list = MoveCache.MOVE_GENERATOR_CACHE.get();
        int move_count = MoveGenerator.generateMoves(chessboard, move_list);

        if(promotion_type == -1) promotion_type = 0;

        for (int count = 0; count < move_count; count++) {
            int possible_move = move_list[count];

            if (EncodeMove.getMoveSource(possible_move) == source_square
                    && EncodeMove.getMoveTarget(possible_move) == target_square
                    && EncodeMove.getMovePromoted(possible_move) == promotion_type) {
                if (!MoveGenerator.makeMove(chessboard, possible_move)) {
                    return ILLEGAL_MOVE;
                }

                unmakeMove(chessboard, possible_move);
                return possible_move;
            }
        }

        return ILLEGAL_MOVE;
    }
}

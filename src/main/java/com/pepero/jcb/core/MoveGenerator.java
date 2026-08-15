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

    // pre calculated ray mask
    public static final long[][] RAY_BETWEEN = new long[64][64];
    public static final long[][] RAY_LINE = new long[64][64];

    // RAY_BETWEEN [a][b] = between a and b squares mask
    // RAY_LINE    [a][b] = straight line passing through a and b

    // RAY_BETWEEN = for check blocking
    // RAY_LINE    = for pinned piece

    static {
        // Initialize ray masks

        for (int sq1 = 0; sq1 < 64; sq1++) {
            for (int sq2 = 0; sq2 < 64; sq2++) {
                if (sq1 == sq2) continue;

                int r1 = sq1 / 8, f1 = sq1 % 8;
                int r2 = sq2 / 8, f2 = sq2 % 8;

                int dr = Integer.compare(r2, r1);
                int df = Integer.compare(f2, f1);

                // check if square a and square b is on equal diagonal/file/rank
                if (r1 == r2 || f1 == f2 || Math.abs(r2 - r1) == Math.abs(f2 - f1)) {
                    for (int i = 0; i < 8; i++) {
                        // mask line

                        int r = r1 + i * dr;
                        int f = f1 + i * df;
                        if (r >= 0 && r < 8 && f >= 0 && f < 8) {
                            RAY_LINE[sq1][sq2] |= (1L << (r * 8 + f));
                        }
                        r = r1 - i * dr;
                        f = f1 - i * df;
                        if (r >= 0 && r < 8 && f >= 0 && f < 8) {
                            RAY_LINE[sq1][sq2] |= (1L << (r * 8 + f));
                        }
                    }

                    // mask between
                    int currentSq = sq1 + (dr * 8 + df);
                    while (currentSq != sq2 && currentSq >= 0 && currentSq < 64) {
                        RAY_BETWEEN[sq1][sq2] |= (1L << currentSq);
                        currentSq += (dr * 8 + df);
                    }
                }
            }
        }
    }

    /**
     * Get pinned piece(s) bitboard
     *
     * @param chessboard chess board
     * @param kingSq king square
     * @param side if the side is white, check white pinned pieces. otherwise, check black pinned pieces
     * @return pinned piece(s) bitboard
     */
    private static long getPinnedPiecesBitboard(Chessboard chessboard, int kingSq, int side) {
        long pinned = 0L;
        int oppSide = side ^ 1;

        // get rook / bishop / queen pieces
        long enemyRooksQueens = (oppSide == white) ?
                (chessboard.bitboards[R] | chessboard.bitboards[Q]) : (chessboard.bitboards[r] | chessboard.bitboards[q]);
        long enemyBishopsQueens = (oppSide == white) ?
                (chessboard.bitboards[B] | chessboard.bitboards[Q]) : (chessboard.bitboards[b] | chessboard.bitboards[q]);

        // blocker
        long occupancy = chessboard.occupancies[both];

        // get rook attacks on king square to find pin
        long pinnerRaysRQ = Attacks.getRookAttacks(kingSq, 0L) & enemyRooksQueens;
        while (pinnerRaysRQ != 0) {
            int pinnerSq = BitBoardUtils.getLS1BIndex(pinnerRaysRQ);

            // get rook attack blockers
            long blockers = RAY_BETWEEN[kingSq][pinnerSq] & occupancy;

            // check blockers count is 1

            // if blockers count is 0, it is already checked, so it's not pin.
            // if blockers count is 2 or more, it is not pin.
            if (BitBoardUtils.countBits(blockers) == 1) {
                // and check the blocker is my side piece
                // if opponent piece is blocker, it's not pin (discovered attack)
                if ((blockers & chessboard.occupancies[side]) != 0) {
                    pinned |= blockers;
                }
            }

            pinnerRaysRQ = BitBoardUtils.popBit(pinnerRaysRQ, pinnerSq);
        }

        // get bishop attacks on king square to find pin
        long pinnerRaysBQ = Attacks.getBishopAttacks(kingSq, 0L) & enemyBishopsQueens;
        while (pinnerRaysBQ != 0) {
            int pinnerSq = BitBoardUtils.getLS1BIndex(pinnerRaysBQ);

            // get bishop attack blockers
            long blockers = RAY_BETWEEN[kingSq][pinnerSq] & occupancy;

            // if blockers count is 0, it is already checked, so it's not pin.
            // if blockers count is 2 or more, it is not pin.
            if (BitBoardUtils.countBits(blockers) == 1) {
                // and check the blocker is my side piece
                // if opponent piece is blocker, it's not pin (discovered attack)
                if ((blockers & chessboard.occupancies[side]) != 0) {
                    pinned |= blockers;
                }
            }
            pinnerRaysBQ = BitBoardUtils.popBit(pinnerRaysBQ, pinnerSq);
        }

        return pinned;
    }

    /**
     * Check enpassant move is safe to move
     * <p>
     * Without this method, the following exception cases would arise: <br>
     * - - - - - - - - <br>
     * r - - p P - K - <br>
     * Let's assume there is a position like this. <br>
     * (the last move black is moving pawn to d5) <br>
     * and if white enpassant, <br>
     * - - - P - - - - <br>
     * r - - - - - K - <br>
     * this is an illegal move because the king is under attack and this is black's turn. <br>
     * but we can't block this without making another enpassant safe check method. <br>
     *
     * @param chessboard chess board
     * @param kingSq king square
     * @param sourceSq enpassant source square
     * @param targetSq enpassant target square
     * @param side playing enpassant side
     * @return true if this enpassant move is safe, false otherwise
     */
    private static boolean isEnPassantSafe(Chessboard chessboard, int kingSq, int sourceSq, int targetSq, int side) {
        int oppSide = side ^ 1;

        // enpassant captured pawn square
        int capturedPawnSq = (side == white) ? targetSq - 8 : targetSq + 8;

        // for checking enpassant safe occupancy
        long tempOccupancy = chessboard.occupancies[both];

        // remove pawn from source square
        tempOccupancy = BitBoardUtils.popBit(tempOccupancy, sourceSq);

        // remove captured pawn
        tempOccupancy = BitBoardUtils.popBit(tempOccupancy, capturedPawnSq);

        // add pawn on target square
        tempOccupancy = BitBoardUtils.setBit(tempOccupancy, targetSq);

        // get pieces slider attacks
        long enemyRooksQueens = (oppSide == white) ?
                (chessboard.bitboards[R] | chessboard.bitboards[Q]) : (chessboard.bitboards[r] | chessboard.bitboards[q]);
        long enemyBishopsQueens = (oppSide == white) ?
                (chessboard.bitboards[B] | chessboard.bitboards[Q]) : (chessboard.bitboards[b] | chessboard.bitboards[q]);

        // get rook/bishop attack mask and check whether the king is attacked
        long kingRookAttacks = Attacks.getRookAttacks(kingSq, tempOccupancy);
        long kingBishopAttacks = Attacks.getBishopAttacks(kingSq, tempOccupancy);

        // check the king is under attack
        return (kingRookAttacks & enemyRooksQueens) == 0 && (kingBishopAttacks & enemyBishopsQueens) == 0;
    }

    /**
     * Generate Strictly legal moves
     *
     * @param chessboard chessboard
     * @param moveArray move array
     * @return move count
     */
    public static int generateMoves(Chessboard chessboard, int[] moveArray) {
        // when 3 check
        if(chessboard.check_count[white] >= 3) return 0;
        if(chessboard.check_count[black] >= 3) return 0;

        int moveCount = 0;
        int side = chessboard.side;
        int oppSide = side ^ 1;

        int checkersInfo = ChessboardUtils.getChecker(chessboard);
        boolean inCheck = (checkersInfo & (1 << 12)) != 0;
        boolean isDoubleCheck = (checkersInfo & (1 << 13)) != 0;

        int kingSq = BitBoardUtils.getLS1BIndex(
                side == white ? chessboard.bitboards[K] : chessboard.bitboards[k]);

        long pinnedPieces = getPinnedPiecesBitboard(chessboard, kingSq, side);

        // avoiding check mask
        long checkMask = ~0L;

        // if in check and it's not a double check
        if (inCheck && !isDoubleCheck) {
            // get checker
            int checkerSq = checkersInfo & 0x3F;
            // avoid check masks

            // avoiding check (when not a double check) methods :

            // capture check piece
            // block attacking piece's path
            // move king (this not includes on this code)

            checkMask = (1L << checkerSq) | RAY_BETWEEN[checkerSq][kingSq];
        }

        // get king moves
        long kingAttacks = Attacks.king_attacks[kingSq] & ~chessboard.occupancies[side];
        while (kingAttacks != 0) {
            int targetSq = BitBoardUtils.getLS1BIndex(kingAttacks);

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
            long tempOcc = BitBoardUtils.popBit(chessboard.occupancies[both], kingSq);
            boolean isSafe = !isSquareAttackedWithOcc(chessboard, targetSq, oppSide, tempOcc);

            if (isSafe) {
                boolean isCapture = BitBoardUtils.getBit(chessboard.occupancies[oppSide], targetSq);
                moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(
                        kingSq, targetSq, (side == white ? K : k), 0, isCapture, false, false, false));
            }
            kingAttacks = BitBoardUtils.popBit(kingAttacks, targetSq);
        }

        // if double check, the legal moves list is only king moves so return it
        if (isDoubleCheck) return moveCount;

        int start_piece = (side == white) ? P : p;
        int end_piece = (side == white) ? Q : q;

        for (int piece = start_piece; piece <= end_piece; piece++) {
            long bitboard = chessboard.bitboards[piece];

            while (bitboard != 0) {
                int sourceSq = BitBoardUtils.getLS1BIndex(bitboard);

                // piece moves bitboard
                long pieceMoves = 0L;
                boolean isPawn = (piece == P || piece == p);

                // get whether this piece is pinned
                boolean isPinned = BitBoardUtils.getBit(pinnedPieces, sourceSq);
                // if the piece is pinned, get legal moves by pin mask
                long pinRay = isPinned ? RAY_LINE[kingSq][sourceSq] : ~0L;

                if (!isPawn) {
                    // get piece moves
                    if (piece == N || piece == n) {
                        pieceMoves = Attacks.knight_attacks[sourceSq];
                    } else if (piece == B || piece == b) {
                        pieceMoves = Attacks.getBishopAttacks(sourceSq, chessboard.occupancies[both]);
                    } else if (piece == R || piece == r) {
                        pieceMoves = Attacks.getRookAttacks(sourceSq, chessboard.occupancies[both]);
                    } else if (piece == Q || piece == q) {
                        pieceMoves = Attacks.getQueenAttacks(sourceSq, chessboard.occupancies[both]);
                    }

                    // remove my side's pieces
                    pieceMoves &= ~chessboard.occupancies[side];

                    // mask pin
                    pieceMoves &= pinRay;

                    // mask check
                    pieceMoves &= checkMask;

                    // add moves all
                    while (pieceMoves != 0) {
                        int targetSq = BitBoardUtils.getLS1BIndex(pieceMoves);
                        boolean isCapture = BitBoardUtils.getBit(chessboard.occupancies[oppSide], targetSq);

                        moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(
                                sourceSq, targetSq, piece, 0, isCapture, false, false, false));
                        pieceMoves = BitBoardUtils.popBit(pieceMoves, targetSq);
                    }

                } else {
                    int pushDir = (side == white) ? 8 : -8;
                    int pushSq = sourceSq + pushDir;

                    if (!BitBoardUtils.getBit(chessboard.occupancies[both], pushSq)) {
                        if (BitBoardUtils.getBit(pinRay, pushSq) /*make sure target square is on pin ray (pin mask)*/ &&
                                BitBoardUtils.getBit(checkMask, pushSq) /*make sure this move is avoiding check*/) {
                            moveCount = addPawnMoves(moveArray, moveCount, sourceSq, pushSq, piece, false);
                        }

                        // when double push
                        int doublePushSq = sourceSq + (pushDir * 2);

                        // make sure double push pawn is on 2 rank
                        boolean isStartRank = (side == white) ? (sourceSq >= a2 && sourceSq <= h2) : (sourceSq >= a7 && sourceSq <= h7);
                        if (isStartRank &&
                                !BitBoardUtils.getBit(chessboard.occupancies[both], doublePushSq) /*check middle square is empty*/) {
                            if (BitBoardUtils.getBit(pinRay, doublePushSq) && BitBoardUtils.getBit(checkMask, doublePushSq)) {
                                moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(
                                        sourceSq, doublePushSq, piece, 0, false, true, false, false));
                            }
                        }
                    }

                    // pawn attacks
                    long pawnAttacks = Attacks.pawn_attacks[side][sourceSq] & chessboard.occupancies[oppSide];

                    // mask pin
                    pawnAttacks &= pinRay;

                    // mask check
                    pawnAttacks &= checkMask;

                    // add all pawn moves
                    while (pawnAttacks != 0) {
                        int targetSq = BitBoardUtils.getLS1BIndex(pawnAttacks);
                        moveCount = addPawnMoves(moveArray, moveCount, sourceSq, targetSq, piece, true);
                        pawnAttacks = BitBoardUtils.popBit(pawnAttacks, targetSq);
                    }

                    // if enpassant square is not 'no_sq'
                    if (chessboard.enpassant != no_sq) {
                        // get enpassant attack
                        long epAttacks = Attacks.pawn_attacks[side][sourceSq] & (1L << chessboard.enpassant);
                        if (epAttacks != 0) {
                            int targetSq = BitBoardUtils.getLS1BIndex(epAttacks);
                            // enpassant captured pawn square
                            int capturedPawnSq = (side == white) ? targetSq - 8 : targetSq + 8;

                            // make sure avoiding check
                            if (BitBoardUtils.getBit(checkMask, targetSq) || BitBoardUtils.getBit(checkMask, capturedPawnSq)) {
                                // make sure it's not pinned and check enpassant safe
                                if (BitBoardUtils.getBit(pinRay, targetSq) && isEnPassantSafe(chessboard, kingSq, sourceSq, targetSq, side)) {
                                    moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(
                                            sourceSq, targetSq, piece, 0, true, false, true, false));
                                }
                            }
                        }
                    }
                }

                bitboard &= (bitboard - 1);
            }
        }

        if (!inCheck) {
            // generate castling moves
            moveCount = generateCastlingMovesStrict(chessboard, moveArray, moveCount, kingSq, side);
        }

        if (chessboard.gameVariants == GameVariants.CRAZY_HOUSE) {
            // generate crazy house drop moves
            moveCount = generateDropMoves(chessboard, moveArray, moveCount, checkMask);
        }

        return moveCount;
    }

    /**
     * Check the square is attacked with another occupancy
     *
     * @param chessboard chess board
     * @param square square
     * @param attackerSide attacker side
     * @param tempOcc occupancy
     * @return true if the square is attacked, false otherwise
     */
    private static boolean isSquareAttackedWithOcc(Chessboard chessboard, int square, int attackerSide, long tempOcc) {
        if (attackerSide == white && (Attacks.pawn_attacks[black][square] & chessboard.bitboards[P]) != 0) return true;
        if (attackerSide == black && (Attacks.pawn_attacks[white][square] & chessboard.bitboards[p]) != 0) return true;
        if ((Attacks.knight_attacks[square] & (attackerSide == white ? chessboard.bitboards[N] : chessboard.bitboards[n])) != 0) return true;
        if ((Attacks.king_attacks[square] & (attackerSide == white ? chessboard.bitboards[K] : chessboard.bitboards[k])) != 0) return true;

        long bishopsQueens = (attackerSide == white) ? (chessboard.bitboards[B] | chessboard.bitboards[Q]) : (chessboard.bitboards[b] | chessboard.bitboards[q]);
        if ((Attacks.getBishopAttacks(square, tempOcc) & bishopsQueens) != 0) return true;

        long rooksQueens = (attackerSide == white) ? (chessboard.bitboards[R] | chessboard.bitboards[Q]) : (chessboard.bitboards[r] | chessboard.bitboards[q]);
        if ((Attacks.getRookAttacks(square, tempOcc) & rooksQueens) != 0) return true;

        return false;
    }

    /**
     * Add pawn moves
     *
     * @param moveArray move array
     * @param moveCount current move count
     * @param source pawn source square
     * @param target pawn target square
     * @param piece pawn piece (for distinguishing white and black)
     * @param isCapture is this pawn move capture
     * @return move count
     */
    private static int addPawnMoves(int[] moveArray, int moveCount, int source, int target, int piece, boolean isCapture) {
        // get moving piece turn
        int turn = (piece == P) ? white : black;

        // get whether this move is promotion
        boolean isPromotion = (turn == white && target >= a8) || (turn == black && target <= h1);

        if (isPromotion) {
            // if promotion move, add four moves (queen, rook, bishop, knight)
            int queen = (turn == white) ? Q : q;
            int rook = (turn == white) ? R : r;
            int bishop = (turn == white) ? B : b;
            int knight = (turn == white) ? N : n;

            addMove(moveArray, moveCount, EncodeMove.encodeMove(source, target, piece, queen, isCapture,
                    false, false, false));
            addMove(moveArray, moveCount + 1, EncodeMove.encodeMove(source, target, piece, rook, isCapture,
                    false, false, false));
            addMove(moveArray, moveCount + 2, EncodeMove.encodeMove(source, target, piece, bishop, isCapture,
                    false, false, false));
            addMove(moveArray, moveCount + 3, EncodeMove.encodeMove(source, target, piece, knight, isCapture,
                    false, false, false));

            return moveCount + 4;
        } else {
            // if not promotion, just add one move
            addMove(moveArray, moveCount, EncodeMove.encodeMove(source, target, piece, 0, isCapture,
                    false, false, false));

            return moveCount + 1;
        }
    }

    /**
     * Generate Castling moves
     *
     * @param chessboard chess board
     * @param moveArray move array
     * @param moveCount current move count
     * @param kingSq king square
     * @param side is this white's / black's castling move
     * @return added move count
     */
    private static int generateCastlingMovesStrict(Chessboard chessboard, int[] moveArray, int moveCount, int kingSq, int side) {
        int oppSide = side ^ 1;

        long occupancy = chessboard.occupancies[both];

        // if chess 960, returns KxR moves like e1h1
        // if standard chess, returns normal moves like e1g1
        boolean is960 = chessboard.gameVariants == GameVariants.CHESS960;

        if (side == white) {
            // make sure the white king side castling right
            if ((chessboard.castle & CastlingRights.WK) != 0 && chessboard.king_side_rook_file != -1) {
                // rook square
                int r_sq = chessboard.king_side_rook_file;
                // "+ 56" is converting black king side rook file to white king side rook file

                // make sure the rook exists
                if (BitBoardUtils.getBit(chessboard.bitboards[R], r_sq)) {
                    // get squares between king square and rook square
                    long betweenMask = RAY_BETWEEN[kingSq][r_sq];

                    // the target king and rook square mask
                    long finalMask = (1L << g1) | (1L << f1);

                    // remove king square and rook square because

                    // if chess 960, it can be like this position
                    // R - - K - R - -
                    // and if king side castles,
                    // R - - - - R K -
                    // and the rook position is the same as before.
                    // but it detects incorrectly there is a piece on f1, but it is white's rook.
                    // so we have to remove rook square, king square (in the same way as before)

                    finalMask &= ~((1L << kingSq) | (1L << r_sq));

                    // make sure there is no pieces between king and rook, and the target squares
                    if ((betweenMask & occupancy) == 0 && (finalMask & occupancy) == 0) {
                        boolean safe = true;
                        // make sure the way king moves across is not under attacked
                        // and the king is not under attacked
                        for (int sq = Math.min(kingSq, g1); sq <= Math.max(kingSq, g1); sq++) {
                            if (isSquareAttacked(chessboard, sq, oppSide)) { safe = false; break; }
                        }
                        if (safe) {
                            // if chess 960, target square is rook position.
                            // if standard,  target square is g1
                            int targetSq = is960 ? r_sq : g1;
                            moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(kingSq, targetSq, K, 0, false, false, false, true));
                        }
                    }
                }
            }

            // the next is the same

            if ((chessboard.castle & CastlingRights.WQ) != 0 && chessboard.queen_side_rook_file != -1) {
                int r_sq = chessboard.queen_side_rook_file;

                if (BitBoardUtils.getBit(chessboard.bitboards[R], r_sq)) {
                    long betweenMask = RAY_BETWEEN[kingSq][r_sq];
                    long finalMask = (1L << c1) | (1L << d1);
                    finalMask &= ~((1L << kingSq) | (1L << r_sq));

                    if ((betweenMask & occupancy) == 0 && (finalMask & occupancy) == 0) {
                        boolean safe = true;
                        for (int sq = Math.min(kingSq, c1); sq <= Math.max(kingSq, c1); sq++) {
                            if (isSquareAttacked(chessboard, sq, oppSide)) { safe = false; break; }
                        }
                        if (safe) {
                            int targetSq = is960 ? r_sq : c1;
                            moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(kingSq, targetSq, K, 0, false, false, false, true));
                        }
                    }
                }
            }
        } else {
            if ((chessboard.castle & CastlingRights.BK) != 0 && chessboard.king_side_rook_file != -1) {
                int r_sq = chessboard.king_side_rook_file + 56;

                if (BitBoardUtils.getBit(chessboard.bitboards[r], r_sq)) {
                    long betweenMask = RAY_BETWEEN[kingSq][r_sq];
                    long finalMask = (1L << g8) | (1L << f8);
                    finalMask &= ~((1L << kingSq) | (1L << r_sq));

                    if ((betweenMask & occupancy) == 0 && (finalMask & occupancy) == 0) {
                        boolean safe = true;
                        for (int sq = Math.min(kingSq, g8); sq <= Math.max(kingSq, g8); sq++) {
                            if (isSquareAttacked(chessboard, sq, oppSide)) { safe = false; break; }
                        }
                        if (safe) {
                            int targetSq = is960 ? r_sq : g8;
                            moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(kingSq, targetSq, k, 0, false, false, false, true));
                        }
                    }
                }
            }

            if ((chessboard.castle & CastlingRights.BQ) != 0 && chessboard.queen_side_rook_file != -1) {
                int r_sq = chessboard.queen_side_rook_file + 56;

                if (BitBoardUtils.getBit(chessboard.bitboards[r], r_sq)) {
                    long betweenMask = RAY_BETWEEN[kingSq][r_sq];
                    long finalMask = (1L << c8) | (1L << d8);
                    finalMask &= ~((1L << kingSq) | (1L << r_sq));

                    if ((betweenMask & occupancy) == 0 && (finalMask & occupancy) == 0) {
                        boolean safe = true;
                        for (int sq = Math.min(kingSq, c8); sq <= Math.max(kingSq, c8); sq++) {
                            if (isSquareAttacked(chessboard, sq, oppSide)) { safe = false; break; }
                        }
                        if (safe) {
                            int targetSq = is960 ? r_sq : c8;
                            moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(kingSq, targetSq, k, 0, false, false, false, true));
                        }
                    }
                }
            }
        }
        return moveCount;
    }

    /**
     * Generate Crazy house drop moves <p>
     * if it's not the crazy house variant, just returns current move count
     *
     * @param chessboard chess board
     * @param moveArray move array
     * @param currentMoveCount current move count
     * @param checkMask current check blocking mask
     * @return added move count
     */
    public static int generateDropMoves(Chessboard chessboard, int[] moveArray, int currentMoveCount, long checkMask) {
        // if it's not the crazy house variant, just returns current move count
        if (chessboard.gameVariants != GameVariants.CRAZY_HOUSE) return currentMoveCount;
        int moveCount = currentMoveCount;
        int mySide = chessboard.side;
        int startPiece = (mySide == white) ? P : p;
        int endPiece   = (mySide == white) ? Q : q;

        // empty squares
        long emptySquares = ~chessboard.occupancies[both];
        // apply check blocking mask
        emptySquares &= checkMask;

        for (int piece = startPiece; piece <= endPiece; piece++) {
            // if there is a piece in pocket,
            if (chessboard.pocket[piece] > 0) {
                // last calculation of drop move
                long dropTargets = emptySquares;
                // pawn shouldn't be on rank 1, rank 8
                if (piece == P || piece == p) {
                    dropTargets &= ~(BitBoardUtils.RANK_1 | BitBoardUtils.RANK_8);
                }

                while (dropTargets != 0) {
                    // add drop moves
                    int target_square = BitBoardUtils.getLS1BIndex(dropTargets);
                    moveCount = addMove(moveArray, moveCount, EncodeMove.encodeDropMove(piece, target_square));
                    dropTargets = BitBoardUtils.popBit(dropTargets, target_square);
                }
            }
        }
        return moveCount;
    }


    /**
     * Add move into move array and ++ the moveCount
     * <p>
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

    // move types

    // when illegal move
    public static final int ILLEGAL_MOVE = -1;

    /**
     * Make a standard move on chess board (CrazyHouse & Chess960 removed)
     * @param chessboard chess board
     * @param move encoded move
     */
    public static void makeStandardMove(Chessboard chessboard, int move){
        chessboard.ensureCapacity();

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
            int king_target, rook_target, rook_piece, rook_source;
            rook_source = target_square;

            if (chessboard.side == white) {
                rook_piece = R;
                if (target_square > source_square) {
                    king_target = g1;
                    rook_target = f1;
                    if (chessboard.gameVariants != GameVariants.CHESS960) rook_source = h1;
                }
                else {
                    king_target = c1;
                    rook_target = d1;
                    if (chessboard.gameVariants != GameVariants.CHESS960) rook_source = a1;
                }
            } else {
                rook_piece = r;
                if (target_square > source_square) {
                    king_target = g8;
                    rook_target = f8;
                    if (chessboard.gameVariants != GameVariants.CHESS960) rook_source = h8;
                }
                else {
                    king_target = c8;
                    rook_target = d8;
                    if (chessboard.gameVariants != GameVariants.CHESS960) rook_source = a8;
                }
            }

            // king
            chessboard.bitboards[piece] = BitBoardUtils.popBit(chessboard.bitboards[piece], source_square);
            chessboard.bitboards[piece] = BitBoardUtils.setBit(chessboard.bitboards[piece], king_target);
            chessboard.hash_key ^= Zobrist.piece_keys[piece][source_square];
            chessboard.hash_key ^= Zobrist.piece_keys[piece][king_target];

            // rook
            chessboard.bitboards[rook_piece] = BitBoardUtils.popBit(chessboard.bitboards[rook_piece], rook_source);
            chessboard.bitboards[rook_piece] = BitBoardUtils.setBit(chessboard.bitboards[rook_piece], rook_target);
            chessboard.hash_key ^= Zobrist.piece_keys[rook_piece][rook_source];
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
            if (chessboard.side == white){
                chessboard.bitboards[p] = BitBoardUtils.popBit(chessboard.bitboards[p], target_square - 8);
                chessboard.hash_key ^= Zobrist.piece_keys[p][target_square - 8];
            } else {
                chessboard.bitboards[P] = BitBoardUtils.popBit(chessboard.bitboards[P], target_square + 8);
                chessboard.hash_key ^= Zobrist.piece_keys[P][target_square + 8];
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
            // white to move
            if (chessboard.side == white){
                // set enpassant square
                chessboard.enpassant = target_square - 8;

                // hash enpassant
                chessboard.hash_key ^= Zobrist.enpassant_keys[target_square - 8];
            }

            // black to move
            else {
                // set enpassant square
                chessboard.enpassant = target_square + 8;

                // hash enpassant
                chessboard.hash_key ^= Zobrist.enpassant_keys[target_square + 8];
            }
        }

        // remove castling rights if king or rook moved
        chessboard.hash_key ^= Zobrist.castling_keys[chessboard.castle];
        chessboard.castle &= CastlingRights.UPDATE_MASK[source_square] & CastlingRights.UPDATE_MASK[target_square];
        chessboard.hash_key ^= Zobrist.castling_keys[chessboard.castle];

        // reset occupancies
        chessboard.occupancies[white] = chessboard.bitboards[P] | chessboard.bitboards[N] |
                chessboard.bitboards[B] | chessboard.bitboards[R] |
                chessboard.bitboards[Q] | chessboard.bitboards[K];

        chessboard.occupancies[black] = chessboard.bitboards[p] | chessboard.bitboards[n] |
                chessboard.bitboards[b] | chessboard.bitboards[r] |
                chessboard.bitboards[q] | chessboard.bitboards[k];

        chessboard.occupancies[both]  = chessboard.occupancies[white] | chessboard.occupancies[black];

        // change side
        chessboard.side ^= 1;

        // hash side
        chessboard.hash_key ^= Zobrist.side_key;

        if (capture || piece == p || piece == P ) {
            chessboard.half_ply = 0;
        } else {
            chessboard.half_ply++;
        }

        chessboard.ply++;
        chessboard.full_move++;
    }

    /**
     * Unmake standard Move on chessboard
     *
     * @param chessboard chessboard
     * @param move encoded move
     */
    public static void unmakeStandardMove(Chessboard chessboard, int move) {
        // decrease ply
        chessboard.ply--;
        chessboard.full_move--;
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
            int k_target, r_target, rook_piece, rook_source;
            rook_source = target_square;

            if (chessboard.side == white) {
                rook_piece = R;
                if (target_square > source_square) { // king side castling
                    k_target = g1;
                    r_target = f1;
                    if (chessboard.gameVariants != GameVariants.CHESS960) rook_source = h1;
                }
                else { // queen side castling
                    k_target = c1;
                    r_target = d1;
                    if (chessboard.gameVariants != GameVariants.CHESS960) rook_source = a1;
                }
            } else {
                rook_piece = r;
                if (target_square > source_square) { // king side castling
                    k_target = g8;
                    r_target = f8;
                    if (chessboard.gameVariants != GameVariants.CHESS960) rook_source = h8;
                }
                else { // queen side castling
                    k_target = c8;
                    r_target = d8;
                    if (chessboard.gameVariants != GameVariants.CHESS960) rook_source = a8;
                }
            }

            // unmake king
            chessboard.bitboards[piece] = BitBoardUtils.popBit(chessboard.bitboards[piece], k_target);
            chessboard.bitboards[piece] = BitBoardUtils.setBit(chessboard.bitboards[piece], source_square);

            // unmake rook
            chessboard.bitboards[rook_piece] = BitBoardUtils.popBit(chessboard.bitboards[rook_piece], r_target);
            chessboard.bitboards[rook_piece] = BitBoardUtils.setBit(chessboard.bitboards[rook_piece], rook_source);
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
            chessboard.bitboards[captured_piece] = BitBoardUtils.setBit(chessboard.bitboards[captured_piece],
                    target_square);
        }

        // if enpassant
        if (enpass) {
            if (chessboard.side == white) {
                chessboard.bitboards[p] = BitBoardUtils.setBit(chessboard.bitboards[p], target_square - 8);
            } else {
                chessboard.bitboards[P] = BitBoardUtils.setBit(chessboard.bitboards[P], target_square + 8);
            }
        }

        // set occupancies
        chessboard.occupancies[white] = chessboard.bitboards[P] | chessboard.bitboards[N] |
                chessboard.bitboards[B] | chessboard.bitboards[R] |
                chessboard.bitboards[Q] | chessboard.bitboards[K];

        chessboard.occupancies[black] = chessboard.bitboards[p] | chessboard.bitboards[n] |
                chessboard.bitboards[b] | chessboard.bitboards[r] |
                chessboard.bitboards[q] | chessboard.bitboards[k];

        chessboard.occupancies[both] = chessboard.occupancies[white] | chessboard.occupancies[black];
    }

    /**
     * Make a move on chess board
     * @param chessboard chess board
     * @param move encoded move
     */
    public static void makeMove(Chessboard chessboard, int move){
        chessboard.ensureCapacity();

        // set chessboard history move data
        chessboard.enpassant_history[chessboard.ply] = chessboard.enpassant;
        chessboard.castle_history[chessboard.ply] = chessboard.castle;
        chessboard.half_ply_history[chessboard.ply] = chessboard.half_ply;
        chessboard.hash_key_history[chessboard.ply] = chessboard.hash_key;
        chessboard.captured_piece_history[chessboard.ply] = -1;
        if (chessboard.gameVariants == GameVariants.CRAZY_HOUSE) {
            chessboard.promoted_captured_history[chessboard.ply] = false;
        }

        // parse move
        int source_square = EncodeMove.getMoveSource(move);
        int target_square = EncodeMove.getMoveTarget(move);
        int piece = EncodeMove.getMovePiece(move);
        int promoted_piece = EncodeMove.getMovePromoted(move);
        boolean capture = EncodeMove.getMoveCapture(move);
        boolean double_push = EncodeMove.getMoveDouble(move);
        boolean enpass = EncodeMove.getMoveEnpassant(move);
        boolean castling = EncodeMove.getMoveCastling(move);
        boolean is_drop = EncodeMove.getMoveDrop(move);

        if (capture && !castling && !is_drop) {
            int start_piece, end_piece;

            if (chessboard.side == white) {
                start_piece = p;
                end_piece = k;
            } else {
                start_piece = P;
                end_piece = K;
            }

            for (int bb_piece = start_piece; bb_piece <= end_piece; bb_piece++) {
                if (BitBoardUtils.getBit(chessboard.bitboards[bb_piece], target_square)) {
                    chessboard.bitboards[bb_piece] =
                            BitBoardUtils.popBit(chessboard.bitboards[bb_piece], target_square);

                    chessboard.hash_key ^= Zobrist.piece_keys[bb_piece][target_square];

                    chessboard.captured_piece_history[chessboard.ply] = bb_piece;

                    if (chessboard.gameVariants == GameVariants.CRAZY_HOUSE) {
                        if (BitBoardUtils.getBit(chessboard.promoted_pieces, target_square)) {
                            int pawnToPocket = chessboard.side == white ? P : p;

                            chessboard.hash_key ^=
                                    Zobrist.pocket_keys[pawnToPocket][chessboard.pocket[pawnToPocket]];
                            chessboard.pocket[pawnToPocket]++;
                            chessboard.hash_key ^=
                                    Zobrist.pocket_keys[pawnToPocket][chessboard.pocket[pawnToPocket]];

                            chessboard.hash_key ^= Zobrist.promoted_keys[target_square];
                            chessboard.promoted_captured_history[chessboard.ply] = true;
                            chessboard.promoted_pieces =
                                    BitBoardUtils.popBit(chessboard.promoted_pieces, target_square);
                            chessboard.hash_key ^= Zobrist.promoted_keys[target_square];
                        } else {
                            int pieceToPocket = chessboard.side == white ? bb_piece - 6 : bb_piece + 6;

                            chessboard.hash_key ^=
                                    Zobrist.pocket_keys[pieceToPocket][chessboard.pocket[pieceToPocket]];
                            chessboard.pocket[pieceToPocket]++;
                            chessboard.hash_key ^=
                                    Zobrist.pocket_keys[pieceToPocket][chessboard.pocket[pieceToPocket]];
                        }
                    }

                    break;
                }
            }
        }

        // crazy house drop
        if (is_drop) {
            // hash pocket key
            if (chessboard.gameVariants == GameVariants.CRAZY_HOUSE && chessboard.pocket[piece] > 0) {
                chessboard.hash_key ^= Zobrist.pocket_keys[piece][chessboard.pocket[piece]];
                chessboard.pocket[piece]--;
                chessboard.hash_key ^= Zobrist.pocket_keys[piece][chessboard.pocket[piece]];
            }

            chessboard.bitboards[piece] = BitBoardUtils.setBit(chessboard.bitboards[piece], target_square);

            chessboard.hash_key ^= Zobrist.piece_keys[piece][target_square];
        }
        // castling
        else if(!castling) {
            // move piece
            chessboard.bitboards[piece] = BitBoardUtils.popBit(chessboard.bitboards[piece], source_square);
            chessboard.bitboards[piece] = BitBoardUtils.setBit(chessboard.bitboards[piece], target_square);

            // crazy house
            if (BitBoardUtils.getBit(chessboard.promoted_pieces, source_square) &&
                    chessboard.gameVariants == GameVariants.CRAZY_HOUSE) {
                chessboard.promoted_pieces = BitBoardUtils.popBit(chessboard.promoted_pieces, source_square);
                chessboard.promoted_pieces = BitBoardUtils.setBit(chessboard.promoted_pieces, target_square);

                // remove and add hash
                chessboard.hash_key ^= Zobrist.promoted_keys[source_square];
                chessboard.hash_key ^= Zobrist.promoted_keys[target_square];
            }

            // hash piece
            chessboard.hash_key ^= Zobrist.piece_keys[piece][source_square]; // remove piece from source square in hash key
            chessboard.hash_key ^= Zobrist.piece_keys[piece][target_square]; // set piece to the target square in hash key
        } else {
            int king_target, rook_target, rook_piece, rook_source;
            rook_source = target_square;

            if (chessboard.side == white) {
                rook_piece = R;
                if (target_square > source_square) {
                    king_target = g1;
                    rook_target = f1;
                    if (chessboard.gameVariants != GameVariants.CHESS960) rook_source = h1;
                }
                else {
                    king_target = c1;
                    rook_target = d1;
                    if (chessboard.gameVariants != GameVariants.CHESS960) rook_source = a1;
                }
            } else {
                rook_piece = r;
                if (target_square > source_square) {
                    king_target = g8;
                    rook_target = f8;
                    if (chessboard.gameVariants != GameVariants.CHESS960) rook_source = h8;
                }
                else {
                    king_target = c8;
                    rook_target = d8;
                    if (chessboard.gameVariants != GameVariants.CHESS960) rook_source = a8;
                }
            }

            // king
            chessboard.bitboards[piece] = BitBoardUtils.popBit(chessboard.bitboards[piece], source_square);
            chessboard.bitboards[piece] = BitBoardUtils.setBit(chessboard.bitboards[piece], king_target);
            chessboard.hash_key ^= Zobrist.piece_keys[piece][source_square];
            chessboard.hash_key ^= Zobrist.piece_keys[piece][king_target];

            // rook
            chessboard.bitboards[rook_piece] = BitBoardUtils.popBit(chessboard.bitboards[rook_piece], rook_source);
            chessboard.bitboards[rook_piece] = BitBoardUtils.setBit(chessboard.bitboards[rook_piece], rook_target);
            chessboard.hash_key ^= Zobrist.piece_keys[rook_piece][rook_source];
            chessboard.hash_key ^= Zobrist.piece_keys[rook_piece][rook_target];
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

            if(chessboard.gameVariants == GameVariants.CRAZY_HOUSE) {
                chessboard.promoted_pieces |= (1L << target_square);
                chessboard.hash_key ^= Zobrist.promoted_keys[target_square];
            }

            // add promoted piece into the hash key
            chessboard.hash_key ^= Zobrist.piece_keys[promoted_piece][target_square];
        }

        // handle enpassant captures
        if (enpass){
            if (chessboard.side == white){
                chessboard.bitboards[p] = BitBoardUtils.popBit(chessboard.bitboards[p], target_square - 8);
                chessboard.hash_key ^= Zobrist.piece_keys[p][target_square - 8];

                if (chessboard.gameVariants == GameVariants.CRAZY_HOUSE) {
                    chessboard.hash_key ^= Zobrist.pocket_keys[P][chessboard.pocket[P]];
                    chessboard.pocket[P]++;
                    chessboard.hash_key ^= Zobrist.pocket_keys[P][chessboard.pocket[P]];
                }
            } else {
                chessboard.bitboards[P] = BitBoardUtils.popBit(chessboard.bitboards[P], target_square + 8);
                chessboard.hash_key ^= Zobrist.piece_keys[P][target_square + 8];

                if (chessboard.gameVariants == GameVariants.CRAZY_HOUSE) {
                    chessboard.hash_key ^= Zobrist.pocket_keys[p][chessboard.pocket[p]];
                    chessboard.pocket[p]++;
                    chessboard.hash_key ^= Zobrist.pocket_keys[p][chessboard.pocket[p]];
                }
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
                chessboard.enpassant = target_square - 8;

                // hash enpassant
                chessboard.hash_key ^= Zobrist.enpassant_keys[target_square - 8];
            }

            // black to move
            else {
                // set enpassant square
                chessboard.enpassant = target_square + 8;

                // hash enpassant
                chessboard.hash_key ^= Zobrist.enpassant_keys[target_square + 8];
            }
        }

        // remove castling rights if king moved
        chessboard.hash_key ^= Zobrist.castling_keys[chessboard.castle];

        if(chessboard.gameVariants == GameVariants.CHESS960) {
            if (piece == K) chessboard.castle &= ~(CastlingRights.WK | CastlingRights.WQ);
            if (piece == k) chessboard.castle &= ~(CastlingRights.BK | CastlingRights.BQ);

            int wk_rook_sq = (chessboard.king_side_rook_file != -1) ? chessboard.king_side_rook_file : h1;
            int wq_rook_sq = (chessboard.queen_side_rook_file != -1) ? chessboard.queen_side_rook_file : a1;
            int bk_rook_sq = (chessboard.king_side_rook_file != -1) ? chessboard.king_side_rook_file + 56 : h8;
            int bq_rook_sq = (chessboard.queen_side_rook_file != -1) ? chessboard.queen_side_rook_file + 56 : a8;

            if (source_square == wk_rook_sq || target_square == wk_rook_sq) chessboard.castle &= ~CastlingRights.WK;
            if (source_square == wq_rook_sq || target_square == wq_rook_sq) chessboard.castle &= ~CastlingRights.WQ;
            if (source_square == bk_rook_sq || target_square == bk_rook_sq) chessboard.castle &= ~CastlingRights.BK;
            if (source_square == bq_rook_sq || target_square == bq_rook_sq) chessboard.castle &= ~CastlingRights.BQ;
        } else {
            chessboard.castle &= CastlingRights.UPDATE_MASK[source_square] & CastlingRights.UPDATE_MASK[target_square];
        }

        chessboard.hash_key ^= Zobrist.castling_keys[chessboard.castle];

        // reset occupancies
        chessboard.occupancies[white] = chessboard.bitboards[P] | chessboard.bitboards[N] |
                chessboard.bitboards[B] | chessboard.bitboards[R] |
                chessboard.bitboards[Q] | chessboard.bitboards[K];

        chessboard.occupancies[black] = chessboard.bitboards[p] | chessboard.bitboards[n] |
                chessboard.bitboards[b] | chessboard.bitboards[r] |
                chessboard.bitboards[q] | chessboard.bitboards[k];

        chessboard.occupancies[both]  = chessboard.occupancies[white] | chessboard.occupancies[black];

        // change side
        chessboard.side ^= 1;

        // hash side
        chessboard.hash_key ^= Zobrist.side_key;

        // if three check variant,
        if(chessboard.gameVariants == GameVariants.THREE_CHECK) {
            chessboard.check_count_history[white][chessboard.ply] = chessboard.check_count[white];
            chessboard.check_count_history[black][chessboard.ply] = chessboard.check_count[black];

            // get checker
            int checkersInfo = ChessboardUtils.getChecker(chessboard);

            // if king is in check, (opponent)
            boolean inCheck = (checkersInfo & (1 << 12)) != 0;

            if (chessboard.gameVariants == GameVariants.THREE_CHECK && inCheck) {
                int side = chessboard.side;
                int oldCount = chessboard.check_count[side];

                chessboard.hash_key ^= Zobrist.check_count_keys[side][oldCount];
                chessboard.check_count[side]++;
                chessboard.hash_key ^= Zobrist.check_count_keys[side][oldCount + 1];
            }
        }

        if (
                EncodeMove.getMoveCapture(move) ||
                        EncodeMove.getMovePiece(move) == p ||
                        EncodeMove.getMovePiece(move) == P ) {
            chessboard.half_ply = 0;
        } else {
            chessboard.half_ply++;
        }

        chessboard.ply++;
        chessboard.full_move++;

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
        chessboard.full_move--;
        chessboard.side ^= 1;

        // get enpassant square, castle, half_ply, hash_key
        chessboard.enpassant = chessboard.enpassant_history[chessboard.ply];
        chessboard.castle = chessboard.castle_history[chessboard.ply];
        chessboard.half_ply = chessboard.half_ply_history[chessboard.ply];
        chessboard.hash_key = chessboard.hash_key_history[chessboard.ply];

        // if 3 check,
        if (chessboard.gameVariants == GameVariants.THREE_CHECK) {
            chessboard.check_count[white] = chessboard.check_count_history[white][chessboard.ply];
            chessboard.check_count[black] = chessboard.check_count_history[black][chessboard.ply];
        }

        // get captured piece
        int captured_piece = chessboard.captured_piece_history[chessboard.ply];

        int source_square = EncodeMove.getMoveSource(move);
        int target_square = EncodeMove.getMoveTarget(move);
        int piece = EncodeMove.getMovePiece(move);
        int promoted_piece = EncodeMove.getMovePromoted(move);
        boolean capture = EncodeMove.getMoveCapture(move);
        boolean enpass = EncodeMove.getMoveEnpassant(move);
        boolean castling = EncodeMove.getMoveCastling(move);
        boolean is_drop = EncodeMove.getMoveDrop(move);

        // unmake castling
        if (is_drop) {
            chessboard.pocket[piece]++;

            chessboard.bitboards[piece] = BitBoardUtils.popBit(chessboard.bitboards[piece], target_square);
        } else if (castling) {
            int k_target, r_target, rook_piece, rook_source;
            rook_source = target_square;

            if (chessboard.side == white) {
                rook_piece = R;
                if (target_square > source_square) { // king side castling
                    k_target = g1;
                    r_target = f1;
                    if (chessboard.gameVariants != GameVariants.CHESS960) rook_source = h1;
                }
                else { // queen side castling
                    k_target = c1;
                    r_target = d1;
                    if (chessboard.gameVariants != GameVariants.CHESS960) rook_source = a1;
                }
            } else {
                rook_piece = r;
                if (target_square > source_square) { // king side castling
                    k_target = g8;
                    r_target = f8;
                    if (chessboard.gameVariants != GameVariants.CHESS960) rook_source = h8;
                }
                else { // queen side castling
                    k_target = c8;
                    r_target = d8;
                    if (chessboard.gameVariants != GameVariants.CHESS960) rook_source = a8;
                }
            }

            // unmake king
            chessboard.bitboards[piece] = BitBoardUtils.popBit(chessboard.bitboards[piece], k_target);
            chessboard.bitboards[piece] = BitBoardUtils.setBit(chessboard.bitboards[piece], source_square);

            // unmake rook
            chessboard.bitboards[rook_piece] = BitBoardUtils.popBit(chessboard.bitboards[rook_piece], r_target);
            chessboard.bitboards[rook_piece] = BitBoardUtils.setBit(chessboard.bitboards[rook_piece], rook_source);
        } else {
            // unmake piece
            if (promoted_piece != 0) {
                chessboard.bitboards[promoted_piece] = BitBoardUtils.popBit(chessboard.bitboards[promoted_piece], target_square);

                if (chessboard.gameVariants == GameVariants.CRAZY_HOUSE) {
                    chessboard.promoted_pieces = BitBoardUtils.popBit(chessboard.promoted_pieces, target_square);
                }
            } else {
                chessboard.bitboards[piece] = BitBoardUtils.popBit(chessboard.bitboards[piece], target_square);

                if (chessboard.gameVariants == GameVariants.CRAZY_HOUSE &&
                        BitBoardUtils.getBit(chessboard.promoted_pieces, target_square)) {
                    chessboard.promoted_pieces = BitBoardUtils.popBit(chessboard.promoted_pieces, target_square);
                    chessboard.promoted_pieces = BitBoardUtils.setBit(chessboard.promoted_pieces, source_square);
                }
            }
            chessboard.bitboards[piece] = BitBoardUtils.setBit(chessboard.bitboards[piece], source_square);
        }

        // if normal capture move
        if (capture && !enpass) {
            chessboard.bitboards[captured_piece] = BitBoardUtils.setBit(chessboard.bitboards[captured_piece],
                    target_square);

            if (chessboard.gameVariants == GameVariants.CRAZY_HOUSE) {
                boolean was_promoted = chessboard.promoted_captured_history[chessboard.ply];
                if (was_promoted) {
                    chessboard.pocket[(chessboard.side == white) ? P : p]--;
                    chessboard.promoted_pieces = BitBoardUtils.setBit(chessboard.promoted_pieces, target_square);
                } else {
                    chessboard.pocket[(chessboard.side == white) ? (captured_piece - 6) : (captured_piece + 6)]--;
                }
            }
        }

        // if enpassant
        if (enpass) {
            if (chessboard.side == white) {
                chessboard.bitboards[p] = BitBoardUtils.setBit(chessboard.bitboards[p], target_square - 8);
                if (chessboard.gameVariants == GameVariants.CRAZY_HOUSE) chessboard.pocket[P]--;
            } else {
                chessboard.bitboards[P] = BitBoardUtils.setBit(chessboard.bitboards[P], target_square + 8);
                if (chessboard.gameVariants == GameVariants.CRAZY_HOUSE) chessboard.pocket[p]--;
            }
        }

        // set occupancies
        chessboard.occupancies[white] = chessboard.bitboards[P] | chessboard.bitboards[N] |
                chessboard.bitboards[B] | chessboard.bitboards[R] |
                chessboard.bitboards[Q] | chessboard.bitboards[K];

        chessboard.occupancies[black] = chessboard.bitboards[p] | chessboard.bitboards[n] |
                chessboard.bitboards[b] | chessboard.bitboards[r] |
                chessboard.bitboards[q] | chessboard.bitboards[k];

        chessboard.occupancies[both]  = chessboard.occupancies[white] | chessboard.occupancies[black];
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

        long occupancy = chessboard.occupancies[both];

        // attacked by bishops
        long bishopsQueens = (side == white) ? (chessboard.bitboards[B] | chessboard.bitboards[Q])
                : (chessboard.bitboards[b] | chessboard.bitboards[q]);
        if ((Attacks.getBishopAttacks(square, occupancy) & bishopsQueens) != 0) return true;

        // attacked by rooks
        long rooksQueens = (side == white) ? (chessboard.bitboards[R] | chessboard.bitboards[Q])
                : (chessboard.bitboards[r] | chessboard.bitboards[q]);
        if ((Attacks.getRookAttacks(square, occupancy) & rooksQueens) != 0) return true;

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
                int square = (7 - rank) * 8 + file;

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

    public static boolean isLegalMove(Chessboard chessboard, int move) {
        int[] move_list = MoveCache.MOVE_GENERATOR_CACHE.get();
        int move_count = MoveGenerator.generateMoves(chessboard, move_list);

        for (int count = 0; count < move_count; count++) {
            int possible_move = move_list[count];
            if (EncodeMove.getMoveSource(possible_move) == EncodeMove.getMoveSource(move)
                    && EncodeMove.getMoveTarget(possible_move) == EncodeMove.getMoveTarget(move)
                    && (EncodeMove.getMovePromoted(possible_move) == EncodeMove.getMovePromoted(move))
                    && EncodeMove.getMoveDrop(possible_move) == EncodeMove.getMoveDrop(move)) {
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
                return possible_move;
            }
        }

        return ILLEGAL_MOVE;
    }

    public static int isLegalDrop(Chessboard chessboard, int target_square, int piece_type) {
        int[] move_list = MoveCache.MOVE_GENERATOR_CACHE.get();
        int move_count = MoveGenerator.generateMoves(chessboard, move_list);

        for (int count = 0; count < move_count; count++) {
            int possible_move = move_list[count];

            if (EncodeMove.getMoveTarget(possible_move) == target_square
                    && EncodeMove.getMoveDrop(possible_move)
                    && EncodeMove.getMovePiece(possible_move) == piece_type) {
                return possible_move;
            }
        }

        return ILLEGAL_MOVE;
    }
}

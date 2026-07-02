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

    static {
        for (int sq1 = 0; sq1 < 64; sq1++) {
            for (int sq2 = 0; sq2 < 64; sq2++) {
                if (sq1 == sq2) continue;

                int r1 = sq1 / 8, f1 = sq1 % 8;
                int r2 = sq2 / 8, f2 = sq2 % 8;

                int dr = Integer.compare(r2, r1);
                int df = Integer.compare(f2, f1);

                if (r1 == r2 || f1 == f2 || Math.abs(r2 - r1) == Math.abs(f2 - f1)) {
                    for (int i = 0; i < 8; i++) {
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

                    int currentSq = sq1 + (dr * 8 + df);
                    while (currentSq != sq2 && currentSq >= 0 && currentSq < 64) {
                        RAY_BETWEEN[sq1][sq2] |= (1L << currentSq);
                        currentSq += (dr * 8 + df);
                    }
                }
            }
        }
    }

    private static long getPinnedPiecesBitboard(Chessboard chessboard, int kingSq, int side) {
        long pinned = 0L;
        int oppSide = side ^ 1;

        long enemyRooksQueens = (oppSide == white) ?
                (chessboard.bitboards[R] | chessboard.bitboards[Q]) : (chessboard.bitboards[r] | chessboard.bitboards[q]);
        long enemyBishopsQueens = (oppSide == white) ?
                (chessboard.bitboards[B] | chessboard.bitboards[Q]) : (chessboard.bitboards[b] | chessboard.bitboards[q]);

        long occupancy = chessboard.occupancies[both];

        long pinnerRaysRQ = Attacks.getRookAttacks(kingSq, 0L) & enemyRooksQueens;
        while (pinnerRaysRQ != 0) {
            int pinnerSq = BitBoardUtils.getLS1BIndex(pinnerRaysRQ);
            long blockers = RAY_BETWEEN[kingSq][pinnerSq] & occupancy;

            if (BitBoardUtils.countBits(blockers) == 1) {
                if ((blockers & chessboard.occupancies[side]) != 0) {
                    pinned |= blockers;
                }
            }
            pinnerRaysRQ = BitBoardUtils.popBit(pinnerRaysRQ, pinnerSq);
        }

        long pinnerRaysBQ = Attacks.getBishopAttacks(kingSq, 0L) & enemyBishopsQueens;
        while (pinnerRaysBQ != 0) {
            int pinnerSq = BitBoardUtils.getLS1BIndex(pinnerRaysBQ);
            long blockers = RAY_BETWEEN[kingSq][pinnerSq] & occupancy;

            if (BitBoardUtils.countBits(blockers) == 1) {
                if ((blockers & chessboard.occupancies[side]) != 0) {
                    pinned |= blockers;
                }
            }
            pinnerRaysBQ = BitBoardUtils.popBit(pinnerRaysBQ, pinnerSq);
        }

        return pinned;
    }

    private static boolean isEnPassantSafe(Chessboard chessboard, int kingSq, int sourceSq, int targetSq, int side) {
        int oppSide = side ^ 1;
        int capturedPawnSq = (side == white) ? targetSq + 8 : targetSq - 8;

        long tempOccupancy = chessboard.occupancies[both];
        tempOccupancy = BitBoardUtils.popBit(tempOccupancy, sourceSq);
        tempOccupancy = BitBoardUtils.popBit(tempOccupancy, capturedPawnSq);
        tempOccupancy = BitBoardUtils.setBit(tempOccupancy, targetSq);

        long enemyRooksQueens = (oppSide == white) ?
                (chessboard.bitboards[R] | chessboard.bitboards[Q]) : (chessboard.bitboards[r] | chessboard.bitboards[q]);
        long enemyBishopsQueens = (oppSide == white) ?
                (chessboard.bitboards[B] | chessboard.bitboards[Q]) : (chessboard.bitboards[b] | chessboard.bitboards[q]);

        long kingRookAttacks = Attacks.getRookAttacks(kingSq, tempOccupancy);
        long kingBishopAttacks = Attacks.getBishopAttacks(kingSq, tempOccupancy);

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
        int moveCount = 0;
        int side = chessboard.side;
        int oppSide = side ^ 1;

        // get check
        int checkersInfo = ChessboardUtils.getChecker(chessboard);
        boolean inCheck = (checkersInfo & (1 << 12)) != 0;
        boolean isDoubleCheck = (checkersInfo & (1 << 13)) != 0;

        int kingSq = BitBoardUtils.getLS1BIndex(
                side == white ? chessboard.bitboards[K] : chessboard.bitboards[k]);

        // get pin mask and defending check
        long pinnedPieces = getPinnedPiecesBitboard(chessboard, kingSq, side);
        long checkMask = ~0L;

        if (inCheck && !isDoubleCheck) {
            int checkerSq = checkersInfo & 0x3F;
            checkMask = (1L << checkerSq) | RAY_BETWEEN[checkerSq][kingSq];
        }

        // king move
        long kingAttacks = Attacks.king_attacks[kingSq] & ~chessboard.occupancies[side];
        while (kingAttacks != 0) {
            int targetSq = BitBoardUtils.getLS1BIndex(kingAttacks);

            // should not attacked king moving square
            long tempOcc = BitBoardUtils.popBit(chessboard.occupancies[both], kingSq);
            boolean isSafe = !isSquareAttackedWithOcc(chessboard, targetSq, oppSide, tempOcc);

            if (isSafe) {
                boolean isCapture = BitBoardUtils.getBit(chessboard.occupancies[oppSide], targetSq);
                moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(
                        kingSq, targetSq, (side == white ? K : k), 0, isCapture, false, false, false));
            }
            kingAttacks = BitBoardUtils.popBit(kingAttacks, targetSq);
        }

        // if double check
        if (isDoubleCheck) return moveCount;

        // other pieces
        int start_piece = (side == white) ? P : p;
        int end_piece = (side == white) ? Q : q;

        for (int piece = start_piece; piece <= end_piece; piece++) {
            long bitboard = chessboard.bitboards[piece];

            while (bitboard != 0) {
                int sourceSq = BitBoardUtils.getLS1BIndex(bitboard);
                long pieceMoves = 0L;
                boolean isPawn = (piece == P || piece == p);

                // get pin
                boolean isPinned = BitBoardUtils.getBit(pinnedPieces, sourceSq);
                long pinRay = isPinned ? RAY_LINE[kingSq][sourceSq] : ~0L;

                if (!isPawn) {
                    if (piece == N || piece == n) {
                        pieceMoves = Attacks.knight_attacks[sourceSq];
                    } else if (piece == B || piece == b) {
                        pieceMoves = Attacks.getBishopAttacks(sourceSq, chessboard.occupancies[both]);
                    } else if (piece == R || piece == r) {
                        pieceMoves = Attacks.getRookAttacks(sourceSq, chessboard.occupancies[both]);
                    } else if (piece == Q || piece == q) {
                        pieceMoves = Attacks.getQueenAttacks(sourceSq, chessboard.occupancies[both]);
                    }

                    // other
                    pieceMoves &= ~chessboard.occupancies[side];

                    pieceMoves &= pinRay;
                    pieceMoves &= checkMask;

                    while (pieceMoves != 0) {
                        int targetSq = BitBoardUtils.getLS1BIndex(pieceMoves);
                        boolean isCapture = BitBoardUtils.getBit(chessboard.occupancies[oppSide], targetSq);

                        moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(
                                sourceSq, targetSq, piece, 0, isCapture, false, false, false));
                        pieceMoves = BitBoardUtils.popBit(pieceMoves, targetSq);
                    }

                } else {
                    // pawn push
                    int pushDir = (side == white) ? -8 : 8;
                    int pushSq = sourceSq + pushDir;

                    // pawn one square push
                    if (!BitBoardUtils.getBit(chessboard.occupancies[both], pushSq)) {
                        if (BitBoardUtils.getBit(pinRay, pushSq) && BitBoardUtils.getBit(checkMask, pushSq)) {
                            addPawnMoves(moveArray, moveCount, sourceSq, pushSq, piece, false);
                            moveCount += (sourceSq >= (side == white ? a7 : a2) && sourceSq <= (side == white ? h7 : h2)) ? 4 : 1;
                        }

                        // pawn two square push
                        int doublePushSq = sourceSq + (pushDir * 2);
                        boolean isStartRank = (side == white) ? (sourceSq >= a2 && sourceSq <= h2) : (sourceSq >= a7 && sourceSq <= h7);
                        if (isStartRank && !BitBoardUtils.getBit(chessboard.occupancies[both], doublePushSq)) {
                            if (BitBoardUtils.getBit(pinRay, doublePushSq) && BitBoardUtils.getBit(checkMask, doublePushSq)) {
                                moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(
                                        sourceSq, doublePushSq, piece, 0, false, true, false, false));
                            }
                        }
                    }

                    // pawn capture
                    long pawnAttacks = Attacks.pawn_attacks[side][sourceSq] & chessboard.occupancies[oppSide];
                    pawnAttacks &= pinRay;
                    pawnAttacks &= checkMask;

                    while (pawnAttacks != 0) {
                        int targetSq = BitBoardUtils.getLS1BIndex(pawnAttacks);
                        addPawnMoves(moveArray, moveCount, sourceSq, targetSq, piece, true);
                        moveCount += (sourceSq >= (side == white ? a7 : a2) && sourceSq <= (side == white ? h7 : h2)) ? 4 : 1;
                        pawnAttacks = BitBoardUtils.popBit(pawnAttacks, targetSq);
                    }

                    // pawn enpassant
                    if (chessboard.enpassant != no_sq) {
                        long epAttacks = Attacks.pawn_attacks[side][sourceSq] & (1L << chessboard.enpassant);
                        if (epAttacks != 0) {
                            int targetSq = BitBoardUtils.getLS1BIndex(epAttacks);
                            int capturedPawnSq = (side == white) ? targetSq + 8 : targetSq - 8;

                            if (BitBoardUtils.getBit(checkMask, targetSq) || BitBoardUtils.getBit(checkMask, capturedPawnSq)) {
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

        // castling
        if (!inCheck) {
            moveCount = generateCastlingMovesStrict(chessboard, moveArray, moveCount, kingSq, side);
        }

        // crazy house drop
        if (chessboard.gameVariants == GameVariants.CRAZY_HOUSE) {
            moveCount = generateDropMoves(chessboard, moveArray, moveCount, checkMask);
        }

        return moveCount;
    }

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

    private static void addPawnMoves(int[] moveArray, int moveCount, int source, int target, int piece, boolean isCapture) {
        int turn = (piece == P) ? white : black;
        boolean isPromotion = (turn == white && target <= h8) || (turn == black && target >= a1);

        if (isPromotion) {
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
        } else {
            addMove(moveArray, moveCount, EncodeMove.encodeMove(source, target, piece, 0, isCapture,
                    false, false, false));
        }
    }

    private static int generateCastlingMovesStrict(Chessboard chessboard, int[] moveArray, int moveCount, int kingSq, int side) {
        int oppSide = side ^ 1;
        long occupancy = chessboard.occupancies[both];
        boolean is960 = chessboard.gameVariants == GameVariants.CHESS960;

        if (side == white) {
            if ((chessboard.castle & CastlingRights.WK) != 0 && chessboard.king_side_rook_file != -1) {
                int r_sq = chessboard.king_side_rook_file + 56;

                long betweenMask = RAY_BETWEEN[kingSq][r_sq];
                long finalMask = (1L << g1) | (1L << f1);
                finalMask &= ~((1L << kingSq) | (1L << r_sq));

                if ((betweenMask & occupancy) == 0 && (finalMask & occupancy) == 0) {
                    boolean safe = true;
                    for (int sq = Math.min(kingSq, g1); sq <= Math.max(kingSq, g1); sq++) {
                        if (isSquareAttacked(chessboard, sq, oppSide)) {
                            safe = false; break;
                        }
                    }
                    if (safe) {
                        int targetSq = is960 ? r_sq : g1;
                        moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(kingSq, targetSq, K, 0, false, false, false, true));
                    }
                }
            }

            if ((chessboard.castle & CastlingRights.WQ) != 0 && chessboard.queen_side_rook_file != -1) {
                int r_sq = chessboard.queen_side_rook_file + 56;

                long betweenMask = RAY_BETWEEN[kingSq][r_sq];
                long finalMask = (1L << c1) | (1L << d1);
                finalMask &= ~((1L << kingSq) | (1L << r_sq));

                if ((betweenMask & occupancy) == 0 && (finalMask & occupancy) == 0) {
                    boolean safe = true;
                    for (int sq = Math.min(kingSq, c1); sq <= Math.max(kingSq, c1); sq++) {
                        if (isSquareAttacked(chessboard, sq, oppSide)) {
                            safe = false; break;
                        }
                    }
                    if (safe) {
                        int targetSq = is960 ? r_sq : c1;
                        moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(kingSq, targetSq, K, 0, false, false, false, true));
                    }
                }
            }
        } else {
            if ((chessboard.castle & CastlingRights.BK) != 0 && chessboard.king_side_rook_file != -1) {
                int r_sq = chessboard.king_side_rook_file;

                long betweenMask = RAY_BETWEEN[kingSq][r_sq];
                long finalMask = (1L << g8) | (1L << f8);
                finalMask &= ~((1L << kingSq) | (1L << r_sq));

                if ((betweenMask & occupancy) == 0 && (finalMask & occupancy) == 0) {
                    boolean safe = true;
                    for (int sq = Math.min(kingSq, g8); sq <= Math.max(kingSq, g8); sq++) {
                        if (isSquareAttacked(chessboard, sq, oppSide)) {
                            safe = false; break;
                        }
                    }
                    if (safe) {
                        int targetSq = is960 ? r_sq : g8;
                        moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(kingSq, targetSq, k, 0, false, false, false, true));
                    }
                }
            }

            if ((chessboard.castle & CastlingRights.BQ) != 0 && chessboard.queen_side_rook_file != -1) {
                int r_sq = chessboard.queen_side_rook_file;

                long betweenMask = RAY_BETWEEN[kingSq][r_sq];
                long finalMask = (1L << c8) | (1L << d8);
                finalMask &= ~((1L << kingSq) | (1L << r_sq));

                if ((betweenMask & occupancy) == 0 && (finalMask & occupancy) == 0) {
                    boolean safe = true;
                    for (int sq = Math.min(kingSq, c8); sq <= Math.max(kingSq, c8); sq++) {
                        if (isSquareAttacked(chessboard, sq, oppSide)) {
                            safe = false; break;
                        }
                    }
                    if (safe) {
                        int targetSq = is960 ? r_sq : c8;
                        moveCount = addMove(moveArray, moveCount, EncodeMove.encodeMove(kingSq, targetSq, k, 0, false, false, false, true));
                    }
                }
            }
        }
        return moveCount;
    }

    public static int generateDropMoves(Chessboard chessboard, int[] moveArray, int currentMoveCount, long checkMask) {
        if (chessboard.gameVariants != GameVariants.CRAZY_HOUSE) return currentMoveCount;
        int moveCount = currentMoveCount;
        int mySide = chessboard.side;
        int startPiece = (mySide == white) ? P : p;
        int endPiece   = (mySide == white) ? Q : q;

        long emptySquares = ~chessboard.occupancies[both];
        emptySquares &= checkMask;

        for (int piece = startPiece; piece <= endPiece; piece++) {
            if (chessboard.pocket[piece] > 0) {
                long dropTargets = emptySquares;
                if (piece == P || piece == p) {
                    dropTargets &= ~(BitBoardUtils.RANK_1 | BitBoardUtils.RANK_8);
                }

                while (dropTargets != 0) {
                    int target_square = BitBoardUtils.getLS1BIndex(dropTargets);
                    moveCount = addMove(moveArray, moveCount, EncodeMove.encodeDropMove(piece, target_square));
                    dropTargets = BitBoardUtils.popBit(dropTargets, target_square);
                }
            }
        }
        return moveCount;
    }

    // move types
    public static final int ILLEGAL_MOVE = -1;

    /**
     * Make a standard move on chess board (CrazyHouse & Chess960 removed)
     * @param chessboard chess board
     * @param move encoded move
     * ( if not generated, returns false. otherwise, returns true )
     */
    public static void makeStandardMove(Chessboard chessboard, int move){
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
            rook_source = target_square; // 체스960의 경우 룩의 시작 위치가 target_square에 담겨있음

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
                chessboard.bitboards[p] = BitBoardUtils.popBit(chessboard.bitboards[p], target_square + 8);
                chessboard.hash_key ^= Zobrist.piece_keys[p][target_square + 8];
            } else {
                chessboard.bitboards[P] = BitBoardUtils.popBit(chessboard.bitboards[P], target_square - 8);
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
                chessboard.bitboards[p] = BitBoardUtils.setBit(chessboard.bitboards[p], target_square + 8);
            } else {
                chessboard.bitboards[P] = BitBoardUtils.setBit(chessboard.bitboards[P], target_square - 8);
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
     * @return whether this move is successfully generated or not
     * ( if not generated, returns false. otherwise, returns true)
     */
    public static void makeMove(Chessboard chessboard, int move){
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
            rook_source = target_square; // 체스960의 경우 룩의 시작 위치가 target_square에 담겨있음

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

                    // crazy house
                    if(chessboard.gameVariants == GameVariants.CRAZY_HOUSE) {
                        if (BitBoardUtils.getBit(chessboard.promoted_pieces, target_square)) {
                            int pawnToPocket = chessboard.side == white ? P : p;

                            if (chessboard.pocket[pawnToPocket] > 0) {
                                chessboard.hash_key ^=
                                        Zobrist.pocket_keys[pawnToPocket][chessboard.pocket[pawnToPocket]];

                                chessboard.pocket[pawnToPocket]++;
                                chessboard.hash_key ^=
                                        Zobrist.pocket_keys[pawnToPocket][chessboard.pocket[pawnToPocket]];

                                chessboard.hash_key ^= Zobrist.promoted_keys[target_square];

                                chessboard.promoted_captured_history[chessboard.ply] = true;
                                chessboard.promoted_pieces = BitBoardUtils.popBit(chessboard.promoted_pieces, target_square);

                                chessboard.hash_key ^= Zobrist.promoted_keys[target_square];
                            }
                        } else {
                            int pieceToPocket = chessboard.side == white ? bb_piece - 6 : bb_piece + 6;

                            if (chessboard.pocket[pieceToPocket] > 0) {
                                chessboard.hash_key ^=
                                        Zobrist.pocket_keys[pieceToPocket][chessboard.pocket[pieceToPocket]];
                                chessboard.pocket[pieceToPocket]++;
                                chessboard.hash_key ^= Zobrist.pocket_keys[pieceToPocket][chessboard.pocket[pieceToPocket]];
                            }
                        }
                    }

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
                chessboard.bitboards[p] = BitBoardUtils.popBit(chessboard.bitboards[p], target_square + 8);
                chessboard.hash_key ^= Zobrist.piece_keys[p][target_square + 8];

                if (chessboard.gameVariants == GameVariants.CRAZY_HOUSE && chessboard.pocket[P] > 0) {
                    chessboard.hash_key ^= Zobrist.pocket_keys[P][chessboard.pocket[P]];
                    chessboard.pocket[P]++;
                    chessboard.hash_key ^= Zobrist.pocket_keys[P][chessboard.pocket[P]];
                }
            } else {
                chessboard.bitboards[P] = BitBoardUtils.popBit(chessboard.bitboards[P], target_square - 8);
                chessboard.hash_key ^= Zobrist.piece_keys[P][target_square - 8];

                if (chessboard.gameVariants == GameVariants.CRAZY_HOUSE && chessboard.pocket[p] > 0) {
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

        if(chessboard.gameVariants == GameVariants.CHESS960) {
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
                chessboard.bitboards[p] = BitBoardUtils.setBit(chessboard.bitboards[p], target_square + 8);
                if (chessboard.gameVariants == GameVariants.CRAZY_HOUSE) chessboard.pocket[P]--;
            } else {
                chessboard.bitboards[P] = BitBoardUtils.setBit(chessboard.bitboards[P], target_square - 8);
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
     * Generate Moves (drop moves on crazy house)
     * @param chessboard chess board
     * @param moveArray the result array
     * @param currentMoveCount start move count
     * @return move counts
     */
    public static int generateDropMoves(Chessboard chessboard, int[] moveArray, int currentMoveCount) {
        if (chessboard.gameVariants != GameVariants.CRAZY_HOUSE) return currentMoveCount;

        int moveCount = currentMoveCount;

        int mySide = chessboard.side;
        int startPiece = (mySide == white) ? P : p;
        int endPiece   = (mySide == white) ? Q : q;

        long emptySquares = ~chessboard.occupancies[both];

        for (int piece = startPiece; piece <= endPiece; piece++) {
            if (chessboard.pocket[piece] > 0) {
                long dropTargets = emptySquares;

                if (piece == P || piece == p) {
                    dropTargets &= ~(BitBoardUtils.RANK_1 | BitBoardUtils.RANK_8);
                }

                long targetsCopy = dropTargets;
                while (targetsCopy != 0) {
                    int target_square = BitBoardUtils.getLS1BIndex(targetsCopy);

                    int dropMove = EncodeMove.encodeDropMove(piece, target_square);

                    moveCount = addMove(moveArray, moveCount, dropMove);

                    targetsCopy = BitBoardUtils.popBit(targetsCopy, target_square);
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

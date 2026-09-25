package com.pepero.jcb.core;

import com.pepero.jcb.core.bitboard.Attacks;
import com.pepero.jcb.core.bitboard.BitBoardUtils;
import com.pepero.jcb.core.constant.BoardSquares;
import com.pepero.jcb.core.constant.CastlingRights;
import com.pepero.jcb.core.constant.EncodedPieces;
import com.pepero.jcb.core.constant.MoveCache;
import com.pepero.jcb.core.encode.EncodeMove;
import com.pepero.jcb.core.hash.Zobrist;

import static com.pepero.jcb.core.constant.SideToMove.*;
import static com.pepero.jcb.core.constant.BoardSquares.*;
import static com.pepero.jcb.core.constant.EncodedPieces.*;

/**
 * Generate moves on given {@link Chessboard} position, make, unmake move, and distinguishing given move is legal move. <br>
 * The move generating logic on {@link #generateMoves(Chessboard, int[])} is {@code fully-legal}, not {@code pseudo-legal}. <br>
 * So don't have to code like this,
 * <pre>
 * {@code
 * int move_list = new int[MoveCache.MAX_MOVE_SIZE];
 * int move_count = MoveGenerator.generateMoves(chessBoard, move_list);
 * for (int i = 0; i < move_count; i++) {
 *     int move = move_list[i];
 *     if(!MoveGenerator.makeMove(chessBoard, move)) {
 *         MoveGenerator.unmakeMove(chessBoard,move);
 *         continue;
 *     }
 *     ...
 *     MoveGenerator.unmakeMove(chessBoard,move);
 * }
 *
 * }
 * </pre>
 * But this,
 * <pre>
 * {@code
 * int move_list = new int[MoveCache.MAX_MOVE_SIZE];
 * int move_count = MoveGenerator.generateMoves(chessBoard, move_list);
 * for (int i = 0; i < move_count; i++) {
 *     int move = move_list[i];
 *     MoveGenerator.makeMove(chessBoard, move);
 *     ...
 *     MoveGenerator.unmakeMove(chessBoard,move);
 * }
 *
 * }
 * </pre>
 *
 * And if you want to use the  <a href="https://chessprogramming.org/Pseudo-Legal_Move">pseudo-legal</a>
 * generator, use {@link #generatePseudoLegalMoves(Chessboard, int[])}.
 */
public class MoveGenerator {
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

    // atomic variant explosion mask
    public static final long[] EXPLOSION_MASK = new long[64];

    static {
        // init explosion mask

        // loop 64 squares
        for (int sq = 0; sq < 64; sq++) {
            // explosion mask ( = removing piece mask) is king attack + moved piece square
            EXPLOSION_MASK[sq] = Attacks.king_attacks[sq] | (1L << sq);
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
     * @param source_square enpassant source square
     * @param target_square enpassant target square
     * @param side playing enpassant side
     * @return true if this enpassant move is safe, false otherwise
     */
    private static boolean isEnPassantSafe(Chessboard chessboard, int kingSq, int source_square, int target_square, int side) {
        int oppSide = side ^ 1;

        // enpassant captured pawn square
        int capturedPawnSq = (side == white) ? target_square - 8 : target_square + 8;

        // for checking enpassant safe occupancy
        long tempOccupancy = chessboard.occupancies[both];

        // remove pawn from source square
        tempOccupancy = BitBoardUtils.popBit(tempOccupancy, source_square);

        // remove captured pawn
        tempOccupancy = BitBoardUtils.popBit(tempOccupancy, capturedPawnSq);

        // add pawn on target square
        tempOccupancy = BitBoardUtils.setBit(tempOccupancy, target_square);

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
     * Generate horde moves
     *
     * @param chessboard chess board
     * @param moveArray move array
     * @param stopAtFirstMove if any move found, stop and return (for hasLegalMoves-style methods)
     * @return move count
     */
    public static int generateHordeMoves(Chessboard chessboard, int[] moveArray, boolean stopAtFirstMove) {
        int move_count = 0;

        if(chessboard.side == white) {
            for (int piece = P; piece <= K; piece++) {
                long bitboard = chessboard.bitboards[piece];

                while (bitboard != 0) {
                    int source_square = BitBoardUtils.getLS1BIndex(bitboard);

                    if(piece == P) {
                        int pushDir = 8;
                        int pushSq = source_square + pushDir;

                        if (!BitBoardUtils.getBit(chessboard.occupancies[both], pushSq)) {
                            move_count = addPawnMoves(moveArray, move_count, source_square, pushSq, piece, false);
                            if(stopAtFirstMove) return move_count;

                            // when double push
                            int doublePushSq = source_square + (pushDir * 2);

                            // make sure double push pawn is on 1 or 2 rank
                            boolean canDoublePush = source_square <= h2;
                            if (canDoublePush &&
                                    !BitBoardUtils.getBit(chessboard.occupancies[both], doublePushSq) /*check middle square is empty*/) {
                                move_count = addMove(moveArray, move_count, EncodeMove.encodeMove(
                                        source_square, doublePushSq, piece, 0, false,
                                        true, false, false));
                            }
                        }

                        // pawn attacks
                        long pawnAttacks = Attacks.pawn_attacks[white][source_square] & chessboard.occupancies[black];

                        // add all pawn moves
                        while (pawnAttacks != 0) {
                            int target_square = BitBoardUtils.getLS1BIndex(pawnAttacks);
                            move_count = addPawnMoves(moveArray, move_count, source_square, target_square, piece, true);
                            if(stopAtFirstMove) return move_count;
                            pawnAttacks = BitBoardUtils.popBit(pawnAttacks, target_square);
                        }

                        // if enpassant square is not 'no_sq'
                        if (chessboard.enpassant != no_sq) {
                            // get enpassant attack
                            long epAttacks = Attacks.pawn_attacks[white][source_square] & (1L << chessboard.enpassant);
                            if (epAttacks != 0) {
                                int target_square = BitBoardUtils.getLS1BIndex(epAttacks);

                                move_count = addMove(moveArray, move_count, EncodeMove.encodeMove(
                                        source_square, target_square, piece, 0, true,
                                        false, true, false));
                                if(stopAtFirstMove) return move_count;
                            }
                        }
                    } else {
                        // get piece moves
                        long pieceMoves = switch (piece) {
                            case N -> Attacks.knight_attacks[source_square];
                            case B -> Attacks.getBishopAttacks(source_square, chessboard.occupancies[both]);
                            case R -> Attacks.getRookAttacks(source_square, chessboard.occupancies[both]);
                            case Q -> Attacks.getQueenAttacks(source_square, chessboard.occupancies[both]);
                            default -> Attacks.king_attacks[source_square];
                        };

                        // remove my side's pieces
                        pieceMoves &= ~chessboard.occupancies[white];

                        // add moves all
                        while (pieceMoves != 0) {
                            int target_square = BitBoardUtils.getLS1BIndex(pieceMoves);
                            boolean isCapture = BitBoardUtils.getBit(chessboard.occupancies[black], target_square);

                            move_count = addMove(moveArray, move_count, EncodeMove.encodeMove(
                                    source_square, target_square, piece, 0, isCapture, false,
                                    false, false));
                            if(stopAtFirstMove) return move_count;
                            pieceMoves = BitBoardUtils.popBit(pieceMoves, target_square);
                        }
                    }

                    bitboard &= (bitboard - 1);
                }
            }

            return move_count;
        }

        return 0;
    }

    /**
     * Generate antichess moves
     *
     * @param chessboard chess board
     * @param moveArray move array
     * @param stopAtFirstMove if any move found, stop and return (for hasLegalMoves-style methods)
     * @return move count
     */
    public static int generateAntiChessMoves(Chessboard chessboard, int[] moveArray, boolean stopAtFirstMove) {
        int move_count = 0;

        int side = chessboard.side;
        int oppSide = chessboard.side ^ 1;

        int start_piece = (side == white) ? P : p;
        int end_piece = (side == white) ? K : k;

        boolean capture = false;
        long[] pieceAttacksCache = new long[64];

        for(int piece = start_piece; piece <= end_piece; piece++) {
            long bitboard = chessboard.bitboards[piece];
            boolean isPawn = (piece == P || piece == p);

            while (bitboard != 0) {
                int source_square = BitBoardUtils.getLS1BIndex(bitboard);

                long pieceAttacks = switch (piece) {
                    case N, n -> Attacks.knight_attacks[source_square];
                    case B, b -> Attacks.getBishopAttacks(source_square, chessboard.occupancies[both]);
                    case R, r -> Attacks.getRookAttacks(source_square, chessboard.occupancies[both]);
                    case Q, q -> Attacks.getQueenAttacks(source_square, chessboard.occupancies[both]);
                    case P, p -> Attacks.pawn_attacks[side][source_square];
                    default -> Attacks.king_attacks[source_square];
                };

                pieceAttacksCache[source_square] = pieceAttacks;

                long epBit = (isPawn && chessboard.enpassant != no_sq) ? (1L << chessboard.enpassant) : 0L;
                if ((pieceAttacks & (chessboard.occupancies[oppSide] | epBit)) != 0L) {
                    capture = true;
                }

                bitboard &= (bitboard - 1);
            }
        }

        for(int piece = start_piece; piece <= end_piece; piece++) {
            long bitboard = chessboard.bitboards[piece];

            if(piece == P || piece == p) {
                while (bitboard != 0) {
                    int source_square = BitBoardUtils.getLS1BIndex(bitboard);

                    if(!capture) {
                        int pushDir = (side == white) ? 8 : -8;
                        int pushSq = source_square + pushDir;

                        if (!BitBoardUtils.getBit(chessboard.occupancies[both], pushSq)) {
                            move_count = addAntiChessPawnMoves(moveArray, move_count, source_square,
                                    pushSq, piece, false);

                            if(stopAtFirstMove) return move_count;

                            // when double push
                            int doublePushSq = source_square + (pushDir * 2);

                            // make sure double push pawn is on 2 rank
                            boolean isStartRank = (side == white) ? (source_square >= a2 && source_square <= h2) : (source_square >= a7 && source_square <= h7);
                            if (isStartRank &&
                                    !BitBoardUtils.getBit(chessboard.occupancies[both], doublePushSq) /*check middle square is empty*/) {
                                move_count = addMove(moveArray, move_count, EncodeMove.encodeMove(
                                        source_square, doublePushSq, piece, 0, false,
                                        true, false, false));
                            }
                        }
                    }

                    // pawn attacks
                    long pawnAttacks = Attacks.pawn_attacks[side][source_square] & chessboard.occupancies[oppSide];

                    // add all pawn moves
                    while (pawnAttacks != 0) {
                        int target_square = BitBoardUtils.getLS1BIndex(pawnAttacks);

                        move_count = addAntiChessPawnMoves(moveArray, move_count, source_square,
                                target_square, piece, true);

                        if(stopAtFirstMove) return move_count;

                        pawnAttacks = BitBoardUtils.popBit(pawnAttacks, target_square);
                    }

                    // if enpassant square is not 'no_sq'
                    if (chessboard.enpassant != no_sq) {
                        // get enpassant attack
                        long epAttacks = Attacks.pawn_attacks[side][source_square] & (1L << chessboard.enpassant);
                        if (epAttacks != 0) {
                            int target_square = BitBoardUtils.getLS1BIndex(epAttacks);

                            move_count = addMove(moveArray, move_count, EncodeMove.encodeMove(
                                    source_square, target_square, piece, 0, true, false,
                                    true, false));

                            if(stopAtFirstMove) return move_count;
                        }
                    }

                    bitboard &= (bitboard - 1);
                }
            } else {
                while (bitboard != 0) {
                    int source_square = BitBoardUtils.getLS1BIndex(bitboard);

                    long pieceAttacks = pieceAttacksCache[source_square] & ~chessboard.occupancies[side];

                    // if capture only,
                    if(capture) {
                        // get capture attacks only
                        if ((pieceAttacks & chessboard.occupancies[oppSide]) != 0L) {
                            long tempOccupancy = pieceAttacks & chessboard.occupancies[oppSide];

                            // add move all
                            while (tempOccupancy != 0L) {
                                int target_square = BitBoardUtils.getLS1BIndex(tempOccupancy);
                                move_count = addMove(moveArray, move_count, EncodeMove.encodeMove(
                                        source_square, target_square, piece, 0, true, false, false,
                                        false
                                ));
                                if(stopAtFirstMove) return move_count;
                                tempOccupancy &= (tempOccupancy - 1);
                            }
                        }
                    } else {
                        // add move all
                        long tempOccupancy = pieceAttacks;
                        while (tempOccupancy != 0L) {
                            int target_square = BitBoardUtils.getLS1BIndex(tempOccupancy);
                            boolean isCapture = BitBoardUtils.getBit(chessboard.occupancies[oppSide], target_square);
                            move_count = addMove(moveArray, move_count, EncodeMove.encodeMove(
                                    source_square, target_square, piece, 0, isCapture, false, false,
                                    false
                            ));
                            if(stopAtFirstMove) return move_count;
                            tempOccupancy &= (tempOccupancy - 1);
                        }
                    }

                    bitboard &= (bitboard - 1);
                }
            }
        }

        return move_count;
    }

    /**
     * Calculate occupancy[both] after explosion
     *
     * @param chessboard chess board
     * @param source_square source square
     * @param target_squareuare target square
     * @param extraRemoveSq extra remove square like enpassant (default -1)
     * @return occupancy[both] after explosion
     */
    private static long computeExplosionRemovalMask(Chessboard chessboard, int source_square,
                                                    int target_squareuare, int extraRemoveSq) {
        long occAfterMove = chessboard.occupancies[both];
        occAfterMove = BitBoardUtils.popBit(occAfterMove, source_square);
        if (extraRemoveSq != -1) occAfterMove = BitBoardUtils.popBit(occAfterMove, extraRemoveSq);
        occAfterMove = BitBoardUtils.setBit(occAfterMove, target_squareuare);

        long pawns = (chessboard.bitboards[P] | chessboard.bitboards[p]) & ~(1L << target_squareuare);

        return EXPLOSION_MASK[target_squareuare] & occAfterMove & ~pawns;
    }

    /**
     * Generate Atomic moves
     *
     * @param chessboard chess board
     * @param moveArray move array
     * @param stopAtFirstMove if any move found, stop and return (for hasLegalMoves-style methods)
     * @return move count
     */
    public static int generateAtomicMoves(Chessboard chessboard, int[] moveArray, boolean stopAtFirstMove) {
        if(chessboard.bitboards[K] == 0L || chessboard.bitboards[k] == 0L) return 0;

        int move_count = 0;
        int side = chessboard.side;
        int oppSide = side ^ 1;

        int kingSq = BitBoardUtils.getLS1BIndex(
                side == white ? chessboard.bitboards[K] : chessboard.bitboards[k]);

        int oppKingSq = BitBoardUtils.getLS1BIndex(
                side == white ? chessboard.bitboards[k] : chessboard.bitboards[K]);

        int checkersInfo = ChessboardUtils.getChecker(chessboard, kingSq);
        boolean inCheck = (checkersInfo & (1 << 12)) != 0;
        boolean isDoubleCheck = (checkersInfo & (1 << 13)) != 0;

        // if kings touching, all check and pin is disabled.
        boolean kingsTouching = (Attacks.king_attacks[kingSq] & (1L << oppKingSq)) != 0;
        long pinnedPieces = (kingsTouching || isDoubleCheck) ? 0L : getPinnedPiecesBitboard(chessboard, kingSq, side);

        // avoiding check mask
        long checkMask = ~0L;

        if (kingsTouching) {
            inCheck = false;
            isDoubleCheck = false;
        }

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
        // in atomic, king cannot capture any piece(s).
        long kingAttacks = Attacks.king_attacks[kingSq] & ~chessboard.occupancies[both];
        long tempOccForKingMoves = BitBoardUtils.popBit(chessboard.occupancies[both], kingSq);
        while (kingAttacks != 0) {
            int target_square = BitBoardUtils.getLS1BIndex(kingAttacks);

            // if target is touching, it's safe
            boolean targetTouching = (Attacks.king_attacks[target_square] & (1L << oppKingSq)) != 0;

            if (targetTouching || !isSquareAttackedWithOccAtomic(chessboard, target_square, oppSide, tempOccForKingMoves)) {
                move_count = addMove(moveArray, move_count, EncodeMove.encodeMove(
                        kingSq, target_square, (side == white ? K : k), 0, false, false, false, false));
                if(stopAtFirstMove) return move_count;
            }

            kingAttacks = BitBoardUtils.popBit(kingAttacks, target_square);
        }

        int start_piece = (side == white) ? P : p;
        int end_piece = (side == white) ? Q : q;

        for (int piece = start_piece; piece <= end_piece; piece++) {
            long bitboard = chessboard.bitboards[piece];

            while (bitboard != 0) {
                int source_square = BitBoardUtils.getLS1BIndex(bitboard);

                // piece moves bitboard
                long pieceMoves;
                boolean isPawn = (piece == P || piece == p);

                // get whether this piece is pinned
                boolean isPinned = BitBoardUtils.getBit(pinnedPieces, source_square);
                // if the piece is pinned, get legal moves by pin mask
                long pinRay = isPinned ? RAY_LINE[kingSq][source_square] : ~0L;

                if (!isPawn) {
                    // get piece moves
                    pieceMoves = getPieceAttacks(chessboard, piece, side, source_square);


                    // remove my side's pieces
                    pieceMoves &= ~chessboard.occupancies[side];

                    if (!isDoubleCheck) {
                        // add quiet moves all
                        long quiet = pieceMoves & ~chessboard.occupancies[both];
                        quiet &= pinRay;
                        quiet &= checkMask;
                        while (quiet != 0) {
                            int target_square = BitBoardUtils.getLS1BIndex(quiet);
                            move_count = addMove(moveArray, move_count, EncodeMove.encodeMove(
                                    source_square, target_square, piece, 0, false, false, false, false));
                            if(stopAtFirstMove) return move_count;
                            quiet = BitBoardUtils.popBit(quiet, target_square);
                        }
                    }

                    long captures = pieceMoves & chessboard.occupancies[oppSide];
                    while (captures != 0) {
                        int target_square = BitBoardUtils.getLS1BIndex(captures);

                        if (isAtomicCaptureLegal(chessboard, side, piece, source_square, target_square,
                                -1, 0, kingSq, oppKingSq, kingsTouching)) {
                            move_count = addMove(moveArray, move_count, EncodeMove.encodeMove(
                                    source_square, target_square, piece, 0, true, false, false, false));
                            if(stopAtFirstMove) return move_count;
                        }
                        captures = BitBoardUtils.popBit(captures, target_square);
                    }
                } else {
                    if (!isDoubleCheck) {
                        // pawn quiet move

                        int pushDir = (side == white) ? 8 : -8;
                        int pushSq = source_square + pushDir;

                        // check target square is empty
                        if (!BitBoardUtils.getBit(chessboard.occupancies[both], pushSq)) {
                            if (BitBoardUtils.getBit(pinRay, pushSq) && BitBoardUtils.getBit(checkMask, pushSq)) {
                                move_count = addPawnMoves(moveArray, move_count, source_square, pushSq, piece, false);
                                if(stopAtFirstMove) return move_count;
                            }
                            int doublePushSq = source_square + (pushDir * 2);
                            boolean isStartRank = (side == white) ? (source_square >= a2 && source_square <= h2) : (source_square >= a7 && source_square <= h7);
                            if (isStartRank && !BitBoardUtils.getBit(chessboard.occupancies[both], doublePushSq)) {
                                if (BitBoardUtils.getBit(pinRay, doublePushSq) && BitBoardUtils.getBit(checkMask, doublePushSq)) {
                                    move_count = addMove(moveArray, move_count, EncodeMove.encodeMove(
                                            source_square, doublePushSq, piece, 0, false, true, false, false));
                                    if(stopAtFirstMove) return move_count;
                                }
                            }
                        }
                    }

                    long pawnCaptures = Attacks.pawn_attacks[side][source_square] & chessboard.occupancies[oppSide];
                    while (pawnCaptures != 0) {
                        // pawn capture move

                        int target_square = BitBoardUtils.getLS1BIndex(pawnCaptures);

                        boolean isPromotion = (side == white && target_square >= a8) || (side == black && target_square <= h1);

                        if (!isPromotion) {
                            if (isAtomicCaptureLegal(chessboard, side, piece, source_square, target_square,
                                    -1, 0, kingSq, oppKingSq, kingsTouching)) {
                                move_count = addMove(moveArray, move_count, EncodeMove.encodeMove(
                                        source_square, target_square, piece, 0, true, false,
                                        false, false));
                                if(stopAtFirstMove) return move_count;
                            }
                        } else {
                            int[] promoPieces = (side == white) ? new int[]{Q,R,B,N} : new int[]{q,r,b,n};
                            for (int promo : promoPieces) {
                                if (isAtomicCaptureLegal(chessboard, side, piece, source_square, target_square,
                                        -1, promo, kingSq, oppKingSq, kingsTouching)) {
                                    move_count = addMove(moveArray, move_count, EncodeMove.encodeMove(
                                            source_square, target_square, piece, promo, true,
                                            false, false, false));
                                    if(stopAtFirstMove) return move_count;
                                }
                            }
                        }
                        pawnCaptures = BitBoardUtils.popBit(pawnCaptures, target_square);
                    }

                    if (chessboard.enpassant != no_sq) {
                        long epAttacks = Attacks.pawn_attacks[side][source_square] & (1L << chessboard.enpassant);
                        if (epAttacks != 0) {
                            int target_square = chessboard.enpassant;
                            int capturedPawnSq = (side == white) ? target_square - 8 : target_square + 8;

                            if (isAtomicCaptureLegal(chessboard, side, piece,
                                    source_square,target_square, capturedPawnSq, 0, kingSq, oppKingSq, kingsTouching)) {
                                move_count = addMove(moveArray, move_count, EncodeMove.encodeMove(
                                        source_square, target_square, piece, 0, true,
                                        false, true, false));
                                if(stopAtFirstMove) return move_count;
                            }
                        }
                    }
                }

                bitboard &= (bitboard - 1);
            }
        }

        if (!inCheck) {
            // generate castling moves
            move_count = generateCastlingMovesStrict(chessboard, moveArray, move_count, kingSq, side, stopAtFirstMove);
        }

        return move_count;
    }

    /**
     * Get piece attacks
     *
     * @param chessboard chess board
     * @param piece piece
     * @param side side
     * @param source_square source square
     * @return piece attacks
     */
    private static long getPieceAttacks(Chessboard chessboard, int piece, int side, int source_square) {
        return switch (piece) {
            case N, n -> Attacks.knight_attacks[source_square];
            case B, b -> Attacks.getBishopAttacks(source_square, chessboard.occupancies[both]);
            case R, r -> Attacks.getRookAttacks(source_square, chessboard.occupancies[both]);
            case Q, q -> Attacks.getQueenAttacks(source_square, chessboard.occupancies[both]);
            case P, p -> Attacks.pawn_attacks[side][source_square];
            default -> Attacks.king_attacks[source_square];
        };
    }

    /**
     * Generate Strictly legal moves
     *
     * @param chessboard chessboard
     * @param moveArray move array
     * @return move count
     */
    public static int generateMoves(Chessboard chessboard, int[] moveArray) {
        return generateMoves(chessboard, moveArray, false, -1, -1);
    }

    /**
     * Generate Strictly legal moves
     *
     * @param chessboard chessboard
     * @param moveArray move array
     * @param stopAtFirstMove if any move found, stop and return (for hasLegalMoves-style methods)
     * @return move count
     */
    public static int generateMoves(Chessboard chessboard, int[] moveArray, boolean stopAtFirstMove) {
        return generateMoves(chessboard, moveArray, stopAtFirstMove, -1, -1);
    }

    /**
     * Generate strictly legal moves, filtering piece type, target square for parsing san or lan
     *
     * @param chessboard chessboard
     * @param moveArray move array
     * @param stopAtFirstMove if any move found, stop and return (for hasLegalMoves-style methods)
     * @param pieceTypeFilter -1 for no filter, otherwise only generate this piece type (as encoded in {@link EncodedPieces})
     * @param targetSquareFilter -1 for no filter, otherwise only generate moves landing on this square
     * @return move count
     */
    public static int generateMoves(Chessboard chessboard, int[] moveArray, boolean stopAtFirstMove,
                                    int pieceTypeFilter, int targetSquareFilter) {
        switch (chessboard.gameVariant) {
            case GIVEAWAY:
            case SUICIDE: {
                int count = generateAntiChessMoves(chessboard, moveArray, stopAtFirstMove);
                return filterMovesInPlace(moveArray, count, pieceTypeFilter, targetSquareFilter);
            }
            case ATOMIC: {
                int count = generateAtomicMoves(chessboard, moveArray, stopAtFirstMove);
                return filterMovesInPlace(moveArray, count, pieceTypeFilter, targetSquareFilter);
            }
            case RACING_KINGS:
                if (ChessboardUtils.getGameResultForRacingKings(chessboard) != ChessboardUtils.ONGOING_VALUE) {
                    return 0;
                }
                break;
            case HORDE:
                if (chessboard.side == white) {
                    int count = generateHordeMoves(chessboard, moveArray, stopAtFirstMove);
                    return filterMovesInPlace(moveArray, count, pieceTypeFilter, targetSquareFilter);
                }
                break;
            case THREE_CHECK:
                if (chessboard.check_count[white] >= 3) return 0;
                if (chessboard.check_count[black] >= 3) return 0;
                break;
            case KING_OF_THE_HILL:
                if ((chessboard.bitboards[K] & BoardSquares.CENTER_SQUARES) != 0) return 0;
                if ((chessboard.bitboards[k] & BoardSquares.CENTER_SQUARES) != 0) return 0;
                break;
            default:
                break;
        }

        int move_count = 0;
        int side = chessboard.side;
        int oppSide = side ^ 1;
        boolean isRacingKings = chessboard.gameVariant == GameVariant.RACING_KINGS;

        int kingSq = BitBoardUtils.getLS1BIndex(
                side == white ? chessboard.bitboards[K] : chessboard.bitboards[k]);

        int oppKingSq = BitBoardUtils.getLS1BIndex(
                side == white ? chessboard.bitboards[k] : chessboard.bitboards[K]);

        int checkersInfo = ChessboardUtils.getChecker(chessboard, kingSq);
        boolean inCheck = (checkersInfo & (1 << 12)) != 0;
        boolean isDoubleCheck = (checkersInfo & (1 << 13)) != 0;

        int myKingPiece = (side == white) ? K : k;
        boolean kingMovesNeeded = (pieceTypeFilter == -1 || pieceTypeFilter == myKingPiece);
        boolean nonKingLoopNeeded = (pieceTypeFilter == -1) || (pieceTypeFilter != myKingPiece);

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
        if (kingMovesNeeded) {
            long kingAttacks = Attacks.king_attacks[kingSq] & ~chessboard.occupancies[side];
            if (targetSquareFilter != -1) kingAttacks &= (1L << targetSquareFilter);
            long tempOccForKingMoves = BitBoardUtils.popBit(chessboard.occupancies[both], kingSq);
            while (kingAttacks != 0) {
                int target_square = BitBoardUtils.getLS1BIndex(kingAttacks);

                // if racing kings, check move is not possible
                if (isRacingKings && wouldGiveCheck(chessboard, side, (side == white ? K : k), kingSq, target_square,
                        -1, oppKingSq, 0)) {
                    kingAttacks = BitBoardUtils.popBit(kingAttacks, target_square);
                    continue;
                }

                // pop king pos on occupancy because

                // let's assume this is the position
                // - R - - - k - -
                // - - - - - - - -

                // and the expected is
                // - R - - - k - -
                // - - - - 1 1 1 -

                // but if we don't pop the king square, the attack is blocked by king square so
                // - R - - - k 1 -
                // - - - - 1 1 1 -
                // and this is not we wanted.

                // long tempOcc = BitBoardUtils.popBit(chessboard.occupancies[both], kingSq);

                boolean isSafe = !isSquareAttackedWithOcc(chessboard, target_square, oppSide, tempOccForKingMoves);

                if (isSafe) {
                    boolean isCapture = BitBoardUtils.getBit(chessboard.occupancies[oppSide], target_square);
                    move_count = addMove(moveArray, move_count, EncodeMove.encodeMove(
                            kingSq, target_square, (side == white ? K : k), 0, isCapture,
                            false, false, false));
                    if(stopAtFirstMove) return move_count;
                }
                kingAttacks = BitBoardUtils.popBit(kingAttacks, target_square);
            }
        }

        // if double check, the legal moves list is only king moves so return it
        if (isDoubleCheck) return move_count;

        if (nonKingLoopNeeded) {
            long pinnedPieces = getPinnedPiecesBitboard(chessboard, kingSq, side);

            int start_piece, end_piece;
            if (pieceTypeFilter != -1) {
                start_piece = end_piece = pieceTypeFilter;
            } else {
                start_piece = (side == white) ? P : p;
                end_piece = (side == white) ? Q : q;
            }

            for (int piece = start_piece; piece <= end_piece; piece++) {
                long bitboard = chessboard.bitboards[piece];

                while (bitboard != 0) {
                    int source_square = BitBoardUtils.getLS1BIndex(bitboard);

                    // piece moves bitboard
                    long pieceMoves;
                    boolean isPawn = (piece == P || piece == p);

                    // get whether this piece is pinned
                    boolean isPinned = BitBoardUtils.getBit(pinnedPieces, source_square);
                    // if the piece is pinned, get legal moves by pin mask
                    long pinRay = isPinned ? RAY_LINE[kingSq][source_square] : ~0L;

                    if (!isPawn) {
                        // get piece moves
                        pieceMoves = getPieceAttacks(chessboard, piece, side, source_square);


                        // remove my side's pieces
                        pieceMoves &= ~chessboard.occupancies[side];

                        // mask pin
                        pieceMoves &= pinRay;

                        // mask check
                        pieceMoves &= checkMask;

                        // narrow down to the requested target square, if any
                        if (targetSquareFilter != -1) pieceMoves &= (1L << targetSquareFilter);

                        // add moves all
                        while (pieceMoves != 0) {
                            int target_square = BitBoardUtils.getLS1BIndex(pieceMoves);

                            // if racing kings, check move is not possible
                            if (isRacingKings && wouldGiveCheck(chessboard, side, piece, source_square, target_square,
                                    -1, oppKingSq, 0)) {
                                pieceMoves = BitBoardUtils.popBit(pieceMoves, target_square);
                                continue;
                            }

                            boolean isCapture = BitBoardUtils.getBit(chessboard.occupancies[oppSide], target_square);

                            move_count = addMove(moveArray, move_count, EncodeMove.encodeMove(
                                    source_square, target_square, piece, 0, isCapture, false, false, false));

                            if(stopAtFirstMove) return move_count;

                            pieceMoves = BitBoardUtils.popBit(pieceMoves, target_square);
                        }

                    } else {
                        int pushDir = (side == white) ? 8 : -8;
                        int pushSq = source_square + pushDir;

                        if (!BitBoardUtils.getBit(chessboard.occupancies[both], pushSq)) {
                            if ((targetSquareFilter == -1 || targetSquareFilter == pushSq) &&
                                    BitBoardUtils.getBit(pinRay, pushSq) /*make sure target square is on pin ray (pin mask)*/ &&
                                    BitBoardUtils.getBit(checkMask, pushSq) /*make sure this move is avoiding check*/) {
                                // if racing kings, check move is not possible
                                if (isRacingKings) {
                                    move_count = addPawnMovesKingRaceSafe(chessboard, side, oppKingSq,moveArray, move_count,
                                            source_square, pushSq, piece, false, -1);
                                } else {
                                    move_count = addPawnMoves(moveArray, move_count, source_square, pushSq, piece, false);
                                }
                                if (stopAtFirstMove && move_count > 0) return move_count;
                            }

                            // when double push
                            int doublePushSq = source_square + (pushDir * 2);

                            // make sure double push pawn is on 2 rank
                            boolean isStartRank = (side == white) ? (source_square >= a2 && source_square <= h2) : (source_square >= a7 && source_square <= h7);
                            if ((targetSquareFilter == -1 || targetSquareFilter == doublePushSq) && isStartRank &&
                                    !BitBoardUtils.getBit(chessboard.occupancies[both], doublePushSq) /*check middle square is empty*/) {
                                if (BitBoardUtils.getBit(pinRay, doublePushSq) && BitBoardUtils.getBit(checkMask, doublePushSq)) {
                                    // if racing kings, check move is not possible
                                    if (isRacingKings) {
                                        if (!wouldGiveCheck(chessboard, side, piece, source_square, doublePushSq,
                                                -1, oppKingSq, 0)) {
                                            move_count = addMove(moveArray, move_count, EncodeMove.encodeMove(
                                                    source_square, doublePushSq, piece, 0, false, true,
                                                    false, false));
                                            if (stopAtFirstMove) return move_count;
                                        }
                                    } else {
                                        move_count = addMove(moveArray, move_count, EncodeMove.encodeMove(
                                                source_square, doublePushSq, piece, 0, false, true,
                                                false, false));
                                        if (stopAtFirstMove) return move_count;
                                    }
                                }
                            }
                        }

                        // pawn attacks
                        long pawnAttacks = Attacks.pawn_attacks[side][source_square] & chessboard.occupancies[oppSide];

                        // mask pin
                        pawnAttacks &= pinRay;

                        // mask check
                        pawnAttacks &= checkMask;

                        // narrow down to the requested target square, if any
                        if (targetSquareFilter != -1) pawnAttacks &= (1L << targetSquareFilter);

                        // add all pawn moves
                        while (pawnAttacks != 0) {
                            int target_square = BitBoardUtils.getLS1BIndex(pawnAttacks);

                            if (isRacingKings) {
                                move_count = addPawnMovesKingRaceSafe(chessboard, side, oppKingSq,
                                        moveArray, move_count, source_square, target_square, piece, true, -1);
                            } else {
                                move_count = addPawnMoves(moveArray, move_count, source_square, target_square, piece, true);
                            }
                            if (stopAtFirstMove && move_count > 0) return move_count;

                            pawnAttacks = BitBoardUtils.popBit(pawnAttacks, target_square);
                        }

                        // if enpassant square is not 'no_sq'
                        if (chessboard.enpassant != no_sq &&
                                (targetSquareFilter == -1 || targetSquareFilter == chessboard.enpassant)) {
                            // get enpassant attack
                            long epAttacks = Attacks.pawn_attacks[side][source_square] & (1L << chessboard.enpassant);
                            if (epAttacks != 0) {
                                int target_square = BitBoardUtils.getLS1BIndex(epAttacks);
                                // enpassant captured pawn square
                                int capturedPawnSq = (side == white) ? target_square - 8 : target_square + 8;

                                // make sure avoiding check
                                if (BitBoardUtils.getBit(checkMask, target_square)
                                        || BitBoardUtils.getBit(checkMask, capturedPawnSq)) {
                                    // make sure it's not pinned and check enpassant safe
                                    if (BitBoardUtils.getBit(pinRay, target_square) &&
                                            isEnPassantSafe(chessboard, kingSq, source_square, target_square, side)) {
                                        // if racing kings, check move is not possible
                                        if(isRacingKings) {
                                            if (!wouldGiveCheck(chessboard, side, piece, source_square, target_square, capturedPawnSq,
                                                    oppKingSq, 0)) {
                                                move_count = addMove(moveArray, move_count, EncodeMove.encodeMove(
                                                        source_square, target_square, piece, 0, true, false,
                                                        true, false));
                                                if (stopAtFirstMove) return move_count;
                                            }
                                        } else {
                                            move_count = addMove(moveArray, move_count, EncodeMove.encodeMove(
                                                    source_square, target_square, piece, 0, true, false,
                                                    true, false));
                                            if (stopAtFirstMove) return move_count;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    bitboard &= (bitboard - 1);
                }
            }
        }

        if (!inCheck && pieceTypeFilter == -1) {
            // generate castling moves
            move_count = generateCastlingMovesStrict(chessboard, moveArray, move_count, kingSq, side, stopAtFirstMove);
        }

        if (chessboard.gameVariant == GameVariant.CRAZY_HOUSE && pieceTypeFilter == -1) {
            // generate crazy house drop moves
            move_count = generateDropMoves(chessboard, moveArray, move_count, checkMask, stopAtFirstMove);
        }

        return move_count;
    }

    /**
     * Filter the given move array with piece type, target square
     *
     * @return the new move count after filtering
     */
    private static int filterMovesInPlace(int[] moveArray, int move_count, int pieceTypeFilter, int targetSquareFilter) {
        if (pieceTypeFilter == -1 && targetSquareFilter == -1) return move_count;

        int write = 0;
        for (int i = 0; i < move_count; i++) {
            int move = moveArray[i];
            if (pieceTypeFilter != -1 && EncodeMove.getMovePiece(move) != pieceTypeFilter) continue;
            if (targetSquareFilter != -1 && EncodeMove.getMoveTarget(move) != targetSquareFilter) continue;
            moveArray[write++] = move;
        }
        return write;
    }

    /**
     * Generate all pseudo-legal moves for the side to move. <br>
     * If you want to know what is the pseudo legal, go to
     * <a href="https://chessprogramming.org/Pseudo-Legal_Move">'pseudo-legal (chess programming wiki)'</a>
     *
     * @param chessboard chessboard
     * @param moveArray move array
     * @return move count
     */
    public static int generatePseudoLegalMoves(Chessboard chessboard, int[] moveArray) {
        switch (chessboard.gameVariant) {
            case GIVEAWAY:
            case SUICIDE:
            case ATOMIC:
            case RACING_KINGS:
                return generateMoves(chessboard, moveArray);
            case HORDE:
                if (chessboard.side == white) {
                    return generateMoves(chessboard, moveArray);
                }
                break;
            case THREE_CHECK:
                if (chessboard.check_count[white] >= 3) return 0;
                if (chessboard.check_count[black] >= 3) return 0;
                break;
            case KING_OF_THE_HILL:
                if ((chessboard.bitboards[K] & BoardSquares.CENTER_SQUARES) != 0) return 0;
                if ((chessboard.bitboards[k] & BoardSquares.CENTER_SQUARES) != 0) return 0;
                break;
            default:
                break;
        }

        int move_count = 0;
        int side = chessboard.side;

        int start_piece = (side == white) ? P : p;
        int end_piece = (side == white) ? K : k;

        for (int piece = start_piece; piece <= end_piece; piece++) {
            long bitboard = chessboard.bitboards[piece];
            while (bitboard != 0) {
                int source_square = BitBoardUtils.getLS1BIndex(bitboard);
                move_count = generatePseudoLegalMovesForSquare(chessboard, source_square, moveArray, move_count);
                bitboard &= (bitboard - 1);
            }
        }

        if (chessboard.gameVariant == GameVariant.CRAZY_HOUSE) {
            move_count = generateDropMoves(chessboard, moveArray, move_count, ~0L, false);
        }

        return move_count;
    }

    /**
     * Generate pseudo legal moves for given piece square position <br>
     * Example :
     *
     * <pre>{@code
     *    8   r n b q k b n r
     *    7   p p p p . p p p
     *    6   . . . . . . . .
     *    5   . . . . p . . .
     *    4   . . . . P . . .
     *    3   . . . . . N . .
     *    2   P P P P . P P P
     *    1   R N B Q K B . R
     *
     *        a b c d e f g h
     *
     *        Side:     black
     *        Enpassant:   no
     *        Castling:  KQkq
     *        FEN : rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2
     *  }</pre>
     *
     *  On this position and generates move with this method like this,
     *  <pre>{@code generatePseudoLegalMovesForSquare(chessboard, d8, move_list)}</pre>
     *  (queen on d8) <br>
     *  The result will be {@code e7, f6, g5, h4}.
     *
     * <pre>{@code
     *    8   r n b q k b n r
     *    7   p p p p @ p p p
     *    6   . . . . . @ . .
     *    5   . . . . p . @ .
     *    4   . . . . P . . @
     *    3   . . . . . N . .
     *    2   P P P P . P P P
     *    1   R N B Q K B . R
     *
     *        a b c d e f g h
     *
     *        Side:     black
     *        Enpassant:   no
     *        Castling:  KQkq
     *        FEN : rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2
     *  }</pre>
     */
    public static int generatePseudoLegalMovesForSquare(Chessboard chessboard, int source_square, int[] moveArray) {
        return generatePseudoLegalMovesForSquare(chessboard, source_square, moveArray, 0);
    }

    /**
     * Generate pseudo legal moves for given piece square position. <br>
     * Used at {@link #generatePseudoLegalMoves(Chessboard, int[])} and starts at given move count
     */
    public static int generatePseudoLegalMovesForSquare(Chessboard chessboard, int source_square, int[] moveArray,
                                                        int move_count) {
        int side = chessboard.side;
        int oppSide = side ^ 1;

        // get piece type
        int piece = ChessboardUtils.getPieceTypeOnSquare(chessboard, source_square);
        // if the piece on source_square is empty, exit.
        if (piece == NO_PIECE_CONSTANT) return move_count;

        // if the source square piece is not own piece, exit.
        boolean isOwnPiece = (side == white) ? (piece >= P && piece <= K) : (piece >= p && piece <= k);
        if (!isOwnPiece) return move_count;

        // when king moves
        if (piece == K || piece == k) {
            long kingAttacks = Attacks.king_attacks[source_square] & ~chessboard.occupancies[side];
            while (kingAttacks != 0) {
                int target_square = BitBoardUtils.getLS1BIndex(kingAttacks);
                boolean isCapture = BitBoardUtils.getBit(chessboard.occupancies[oppSide], target_square);
                move_count = addMove(moveArray, move_count, EncodeMove.encodeMove(
                        source_square, target_square, piece, 0, isCapture, false, false, false));
                kingAttacks = BitBoardUtils.popBit(kingAttacks, target_square);
            }

            return generateCastlingMovesStrict(chessboard, moveArray, move_count, source_square, side, false);
        }

        if (piece == P || piece == p) {
            return addPseudoLegalPawnMoves(chessboard, moveArray, move_count, source_square, piece, side, oppSide);
        }

        long pieceMoves = getPieceAttacks(chessboard, piece, side, source_square) & ~chessboard.occupancies[side];
        while (pieceMoves != 0) {
            int target_square = BitBoardUtils.getLS1BIndex(pieceMoves);
            boolean isCapture = BitBoardUtils.getBit(chessboard.occupancies[oppSide], target_square);
            move_count = addMove(moveArray, move_count, EncodeMove.encodeMove(
                    source_square, target_square, piece, 0, isCapture, false, false, false));
            pieceMoves = BitBoardUtils.popBit(pieceMoves, target_square);
        }
        return move_count;
    }

    private static int addPseudoLegalPawnMoves(Chessboard chessboard, int[] moveArray, int move_count,
                                               int source_square, int piece, int side, int oppSide) {
        int pushDir = (side == white) ? 8 : -8;
        int pushSq = source_square + pushDir;

        if (pushSq >= 0 && pushSq < 64 && !BitBoardUtils.getBit(chessboard.occupancies[both], pushSq)) {
            move_count = addPawnMoves(moveArray, move_count, source_square, pushSq, piece, false);

            int doublePushSq = source_square + (pushDir * 2);
            boolean isStartRank = (side == white)
                    ? (source_square >= a2 && source_square <= h2) : (source_square >= a7 && source_square <= h7);
            if (isStartRank && !BitBoardUtils.getBit(chessboard.occupancies[both], doublePushSq)) {
                move_count = addMove(moveArray, move_count, EncodeMove.encodeMove(
                        source_square, doublePushSq, piece, 0, false, true, false, false));
            }
        }

        long pawnAttacks = Attacks.pawn_attacks[side][source_square] & chessboard.occupancies[oppSide];
        while (pawnAttacks != 0) {
            int target_square = BitBoardUtils.getLS1BIndex(pawnAttacks);
            move_count = addPawnMoves(moveArray, move_count, source_square, target_square, piece, true);
            pawnAttacks = BitBoardUtils.popBit(pawnAttacks, target_square);
        }

        if (chessboard.enpassant != no_sq) {
            long epAttacks = Attacks.pawn_attacks[side][source_square] & (1L << chessboard.enpassant);
            if (epAttacks != 0) {
                move_count = addMove(moveArray, move_count, EncodeMove.encodeMove(
                        source_square, chessboard.enpassant, piece, 0, true, false, true, false));
            }
        }

        return move_count;
    }

    /**
     * Get whether target king square is attacked or not <br>
     * king attack not included
     *
     * @param chessboard chess board
     * @param side moving side
     * @param piece piece type
     * @param source_square source square
     * @param target_square target square
     * @param extraRemoveSq extra remove square like enpassant (default -1)
     * @param promotedPiece promotion piece (default 0)
     * @param removalMask removal mask for atomic chess (default 0L)
     * @param targetKingSq target king square
     * @param attackerSide attacker side
     * @return whether target king square is attacked or not
     */
    private static boolean isSquareAttackedAfterMove(Chessboard chessboard, int side, int piece,
                                                     int source_square, int target_square, int extraRemoveSq,
                                                     int promotedPiece, long removalMask,
                                                     int targetKingSq, int attackerSide) {
        long tempOcc = chessboard.occupancies[both];
        tempOcc = BitBoardUtils.popBit(tempOcc, source_square);
        if (extraRemoveSq != -1) tempOcc = BitBoardUtils.popBit(tempOcc, extraRemoveSq);
        tempOcc = BitBoardUtils.setBit(tempOcc, target_square);
        tempOcc &= ~removalMask;

        int targetPiece = (promotedPiece != 0) ? promotedPiece : piece;

        long bishopsQueens = ((attackerSide == white) ? (chessboard.bitboards[B] | chessboard.bitboards[Q])
                : (chessboard.bitboards[b] | chessboard.bitboards[q])) & ~removalMask;
        long rooksQueens = ((attackerSide == white) ? (chessboard.bitboards[R] | chessboard.bitboards[Q])
                : (chessboard.bitboards[r] | chessboard.bitboards[q])) & ~removalMask;
        long knights = ((attackerSide == white) ? chessboard.bitboards[N] : chessboard.bitboards[n]) & ~removalMask;
        long pawns   = ((attackerSide == white) ? chessboard.bitboards[P] : chessboard.bitboards[p]) & ~removalMask;

        if (side == attackerSide) {
            switch (piece) {
                case Q: case q:
                    bishopsQueens = BitBoardUtils.popBit(bishopsQueens, source_square);
                    rooksQueens = BitBoardUtils.popBit(rooksQueens, source_square);
                    break;
                case B: case b:
                    bishopsQueens = BitBoardUtils.popBit(bishopsQueens, source_square);
                    break;
                case R: case r:
                    rooksQueens = BitBoardUtils.popBit(rooksQueens, source_square);
                    break;
                case N: case n:
                    knights = BitBoardUtils.popBit(knights, source_square);
                    break;
                case P: case p:
                    pawns = BitBoardUtils.popBit(pawns, source_square);
                    break;
            }
        }

        if (!BitBoardUtils.getBit(removalMask, target_square) && side == attackerSide) {
            switch (targetPiece) {
                case Q: case q:
                    bishopsQueens = BitBoardUtils.setBit(bishopsQueens, target_square);
                    rooksQueens = BitBoardUtils.setBit(rooksQueens, target_square);
                    break;
                case B: case b:
                    bishopsQueens = BitBoardUtils.setBit(bishopsQueens, target_square);
                    break;
                case R: case r:
                    rooksQueens = BitBoardUtils.setBit(rooksQueens, target_square);
                    break;
                case N: case n:
                    knights = BitBoardUtils.setBit(knights, target_square);
                    break;
                case P: case p:
                    pawns = BitBoardUtils.setBit(pawns, target_square);
                    break;
            }
        }

        if ((Attacks.getBishopAttacks(targetKingSq, tempOcc) & bishopsQueens) != 0) return true;
        if ((Attacks.getRookAttacks(targetKingSq, tempOcc) & rooksQueens) != 0) return true;
        if ((Attacks.knight_attacks[targetKingSq] & knights) != 0) return true;

        long pawnAttackers = (attackerSide == white)
                ? (Attacks.pawn_attacks[black][targetKingSq] & pawns)
                : (Attacks.pawn_attacks[white][targetKingSq] & pawns);
        if (pawnAttackers != 0) return true;

        return false;
    }

    /**
     * Check whether this move would give check or not (for racing kings)
     *
     * @param chessboard chess board
     * @param side attacker side
     * @param piece piece type
     * @param source_square source square
     * @param target_square target square
     * @param extraRemoveSq extra remove square like enpassant (if no extra square, -1)
     * @param oppKingSq opponent king square
     * @param promotedPiece promoting piece (if not promoting, 0)
     * @return whether this move would give check or not
     */
    private static boolean wouldGiveCheck(Chessboard chessboard, int side, int piece,
                                          int source_square, int target_square, int extraRemoveSq,
                                          int oppKingSq, int promotedPiece) {
        return isSquareAttackedAfterMove(chessboard, side, piece, source_square, target_square, extraRemoveSq,
                promotedPiece, 0L, oppKingSq, side);
    }

    /**
     * Check whether this capturing atomic move is legal move
     *
     * @param chessboard chess board
     * @param side moving side
     * @param piece piece type
     * @param source_square source square
     * @param target_square target square
     * @param extraRemoveSq extra removing square like enpassant (default -1)
     * @param promotedPiece promotion piece (default 0)
     * @return whether this capturing atomic move is legal move
     */
    private static boolean isAtomicCaptureLegal(Chessboard chessboard, int side, int piece,
                                                int source_square, int target_square, int extraRemoveSq,
                                                int promotedPiece, int ourKing, int oppKing,
                                                boolean kingsTouching) {
        long removalMask = computeExplosionRemovalMask(chessboard, source_square, target_square, extraRemoveSq);

        if(BitBoardUtils.getBit(removalMask, ourKing)) return false;
        if(BitBoardUtils.getBit(removalMask, oppKing)) return true;

        if (kingsTouching) return true;

        int oppSide = side ^ 1;
        return !isSquareAttackedAfterMove(chessboard, side, piece, source_square, target_square, extraRemoveSq,
                promotedPiece, removalMask, ourKing, oppSide);
    }

    /**
     * Add pawn moves
     *
     * @param moveArray move array
     * @param move_count current move count
     * @param source pawn source square
     * @param target pawn target square
     * @param piece pawn piece (for distinguishing white and black)
     * @param isCapture is this pawn move capture
     * @return move count
     */
    private static int addPawnMoves(int[] moveArray, int move_count, int source, int target, int piece, boolean isCapture) {
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

            addMove(moveArray, move_count, EncodeMove.encodeMove(source, target, piece, queen, isCapture,
                    false, false, false));
            addMove(moveArray, move_count + 1, EncodeMove.encodeMove(source, target, piece, rook, isCapture,
                    false, false, false));
            addMove(moveArray, move_count + 2, EncodeMove.encodeMove(source, target, piece, bishop, isCapture,
                    false, false, false));
            addMove(moveArray, move_count + 3, EncodeMove.encodeMove(source, target, piece, knight, isCapture,
                    false, false, false));

            return move_count + 4;
        } else {

            // if not promotion, just add one move
            addMove(moveArray, move_count, EncodeMove.encodeMove(source, target, piece, 0, isCapture,
                    false, false, false));

            return move_count + 1;
        }
    }

    /**
     * Add anti chess pawn moves
     *
     * @param moveArray move array
     * @param move_count current move count
     * @param source pawn source square
     * @param target pawn target square
     * @param piece pawn piece (for distinguishing white and black)
     * @param isCapture is this pawn move capture
     * @return move count
     */
    private static int addAntiChessPawnMoves(int[] moveArray, int move_count, int source, int target,
                                             int piece, boolean isCapture) {
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
            int king = (turn == white) ? K : k;

            addMove(moveArray, move_count, EncodeMove.encodeMove(source, target, piece, queen, isCapture,
                    false, false, false));
            addMove(moveArray, move_count + 1, EncodeMove.encodeMove(source, target, piece, rook, isCapture,
                    false, false, false));
            addMove(moveArray, move_count + 2, EncodeMove.encodeMove(source, target, piece, bishop, isCapture,
                    false, false, false));
            addMove(moveArray, move_count + 3, EncodeMove.encodeMove(source, target, piece, knight, isCapture,
                    false, false, false));
            addMove(moveArray, move_count + 4, EncodeMove.encodeMove(source, target, piece, king, isCapture,
                    false, false, false));

            return move_count + 5;
        } else {
            // if not promotion, just add one move
            addMove(moveArray, move_count, EncodeMove.encodeMove(source, target, piece, 0, isCapture,
                    false, false, false));

            return move_count + 1;
        }
    }

    /**
     * Add pawn moves
     *
     * @param moveArray move array
     * @param move_count current move count
     * @param source pawn source square
     * @param target pawn target square
     * @param piece pawn piece (for distinguishing white and black)
     * @param isCapture is this pawn move capture
     * @param extraRemoveSq extra remove square like enpassant (default -1)
     * @return move count
     */
    private static int addPawnMovesKingRaceSafe(Chessboard chessboard, int side, int oppKingSq,
                                                int[] moveArray, int move_count, int source, int target, int piece,
                                                boolean isCapture, int extraRemoveSq) {
        // get moving piece turn
        int turn = (piece == P) ? white : black;

        // get whether this move is promotion
        boolean isPromotion = (turn == white && target >= a8) || (turn == black && target <= h1);

        if (!isPromotion) {
            // if not promotion, just add one move
            if (!wouldGiveCheck(chessboard, side, piece, source, target, extraRemoveSq, oppKingSq, 0)) {
                // should not be a check move
                move_count = addMove(moveArray, move_count, EncodeMove.encodeMove(
                        source, target, piece, 0, isCapture, false, false, false));
            }
            return move_count;
        }

        // if promotion move, add four moves (queen, rook, bishop, knight)
        int queen  = (turn == white) ? Q : q;
        int rook   = (turn == white) ? R : r;
        int bishop = (turn == white) ? B : b;
        int knight = (turn == white) ? N : n;

        int[] promoPieces = { queen, rook, bishop, knight };
        for (int promo : promoPieces) {
            if (!wouldGiveCheck(chessboard, side, piece, source, target, extraRemoveSq, oppKingSq, promo)) {
                // should not be a check move
                move_count = addMove(moveArray, move_count, EncodeMove.encodeMove(
                        source, target, piece, promo, isCapture, false, false, false));
            }
        }
        return move_count;
    }

    /**
     * Generate Castling moves
     *
     * @param chessboard chess board
     * @param moveArray move array
     * @param move_count current move count
     * @param kingSq king square
     * @param side is this white's / black's castling move
     * @param stopAtFirstMove if any move found, stop and return (for hasLegalMoves-style methods)
     * @return added move count
     */
    private static int generateCastlingMovesStrict(Chessboard chessboard, int[] moveArray,
                                                   int move_count, int kingSq, int side, boolean stopAtFirstMove) {
        if (chessboard.gameVariant == GameVariant.RACING_KINGS) return move_count;
        int oppSide = side ^ 1;

        long occupancy = chessboard.occupancies[both];

        // if chess 960, returns KxR moves like e1h1
        // if standard chess, returns normal moves like e1g1
        boolean is960 = chessboard.isChess960;

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
                            int target_square = is960 ? r_sq : g1;
                            move_count = addMove(moveArray, move_count, EncodeMove.encodeMove(kingSq, target_square, K, 0, false, false, false, true));
                            if (stopAtFirstMove) return move_count;
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
                            int target_square = is960 ? r_sq : c1;
                            move_count = addMove(moveArray, move_count, EncodeMove.encodeMove(kingSq, target_square, K, 0, false, false, false, true));
                            if (stopAtFirstMove) return move_count;
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
                            int target_square = is960 ? r_sq : g8;
                            move_count = addMove(moveArray, move_count, EncodeMove.encodeMove(kingSq, target_square, k, 0, false, false, false, true));
                            if (stopAtFirstMove) return move_count;
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
                            int target_square = is960 ? r_sq : c8;
                            move_count = addMove(moveArray, move_count, EncodeMove.encodeMove(kingSq, target_square, k, 0, false, false, false, true));
                            if (stopAtFirstMove) return move_count;
                        }
                    }
                }
            }
        }
        return move_count;
    }

    /**
     * Generate Crazy house drop moves <p>
     * if it's not the crazy house variant, just returns current move count
     *
     * @param chessboard chess board
     * @param moveArray move array
     * @param current_move_count current move count
     * @param checkMask current check blocking mask
     * @param stopAtFirstMove if any move found, stop and return (for hasLegalMoves-style methods)
     * @return added move count
     */
    public static int generateDropMoves(Chessboard chessboard, int[] moveArray,
                                        int current_move_count, long checkMask, boolean stopAtFirstMove) {
        // if it's not the crazy house variant, just returns current move count
        if (chessboard.gameVariant != GameVariant.CRAZY_HOUSE) return current_move_count;
        int move_count = current_move_count;
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
                    dropTargets &= ~(RANK_1 | RANK_8);
                }

                while (dropTargets != 0) {
                    // add drop moves
                    int target_square = BitBoardUtils.getLS1BIndex(dropTargets);
                    move_count = addMove(moveArray, move_count, EncodeMove.encodeDropMove(piece, target_square));
                    if(stopAtFirstMove) return move_count;
                    dropTargets = BitBoardUtils.popBit(dropTargets, target_square);
                }
            }
        }
        return move_count;
    }


    /**
     * Add move into move array and ++ the move_count
     * <p>
     * Usage : move_count = addMove(int[] moveArray, int move_count, int moveData);
     *
     * @param moveArray move array
     * @param move_count move index
     * @param moveData encoded move data
     * @return move count + 1
     */
    public static int addMove(int[] moveArray, int move_count, int moveData){
        moveArray[move_count] = moveData;
        return ++move_count;
    }

    // move types

    // when illegal move
    public static final int ILLEGAL_MOVE = -1;

    /**
     * Apply atomic capture explosion <br>
     * returns updated castling mask to AND with chessboard.castle
     *
     * @param chessboard chess board
     * @param target_square target square
     * @return updated castling mask to AND with chessboard.castle
     */
    private static int applyAtomicExplosion(Chessboard chessboard, int target_square) {
        long occ = chessboard.bitboards[P] | chessboard.bitboards[N] | chessboard.bitboards[B] |
                chessboard.bitboards[R] | chessboard.bitboards[Q] | chessboard.bitboards[K] |
                chessboard.bitboards[p] | chessboard.bitboards[n] | chessboard.bitboards[b] |
                chessboard.bitboards[r] | chessboard.bitboards[q] | chessboard.bitboards[k];

        long pawns = (chessboard.bitboards[P] | chessboard.bitboards[p]) & ~(1L << target_square);

        long removalMask = (EXPLOSION_MASK[target_square] & occ & ~pawns) | (1L << target_square);

        int expCount = 0;
        long mask = removalMask;
        while (mask != 0) {
            int sq = BitBoardUtils.getLS1BIndex(mask);
            for (int bb = P; bb <= k; bb++) {
                if (BitBoardUtils.getBit(chessboard.bitboards[bb], sq)) {
                    chessboard.bitboards[bb] = BitBoardUtils.popBit(chessboard.bitboards[bb], sq);
                    chessboard.mailbox[sq] = NO_PIECE_CONSTANT;
                    chessboard.hash_key ^= Zobrist.piece_keys[bb][sq];
                    chessboard.explosion_piece_history[chessboard.ply][expCount] = bb;
                    chessboard.explosion_square_history[chessboard.ply][expCount] = sq;
                    expCount++;
                    break;
                }
            }
            mask = BitBoardUtils.popBit(mask, sq);
        }
        chessboard.explosion_count_history[chessboard.ply] = expCount;

        int castleMask = 0xF;
        long m2 = removalMask;
        while (m2 != 0) {
            int sq = BitBoardUtils.getLS1BIndex(m2);
            castleMask &= CastlingRights.UPDATE_MASK[sq];
            m2 = BitBoardUtils.popBit(m2, sq);
        }
        return castleMask;
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
        if (chessboard.gameVariant == GameVariant.CRAZY_HOUSE) {
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

        int captured_bb_piece = chessboard.mailbox[target_square];

        boolean isAtomic = chessboard.gameVariant == GameVariant.ATOMIC;
        int atomicCastleMask = 0xF;

        if (capture && !enpass && !castling && !is_drop) {
            chessboard.bitboards[captured_bb_piece] =
                    BitBoardUtils.popBit(chessboard.bitboards[captured_bb_piece], target_square);

            chessboard.hash_key ^= Zobrist.piece_keys[captured_bb_piece][target_square];

            chessboard.captured_piece_history[chessboard.ply] = captured_bb_piece;

            if (chessboard.gameVariant == GameVariant.CRAZY_HOUSE) {
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
                    int pieceToPocket = EncodedPieces.normalizePieceColor(captured_bb_piece, chessboard.side);

                    chessboard.hash_key ^=
                            Zobrist.pocket_keys[pieceToPocket][chessboard.pocket[pieceToPocket]];
                    chessboard.pocket[pieceToPocket]++;
                    chessboard.hash_key ^=
                            Zobrist.pocket_keys[pieceToPocket][chessboard.pocket[pieceToPocket]];
                }
            }
        }

        // crazy house drop
        if (is_drop) {
            // hash pocket key
            if (chessboard.gameVariant == GameVariant.CRAZY_HOUSE && chessboard.pocket[piece] > 0) {
                chessboard.hash_key ^= Zobrist.pocket_keys[piece][chessboard.pocket[piece]];
                chessboard.pocket[piece]--;
                chessboard.hash_key ^= Zobrist.pocket_keys[piece][chessboard.pocket[piece]];
            }

            chessboard.bitboards[piece] = BitBoardUtils.setBit(chessboard.bitboards[piece], target_square);
            chessboard.mailbox[target_square] = piece;

            chessboard.hash_key ^= Zobrist.piece_keys[piece][target_square];
        }
        else if(!castling) {
            // move piece
            chessboard.bitboards[piece] = BitBoardUtils.popBit(chessboard.bitboards[piece], source_square);
            chessboard.bitboards[piece] = BitBoardUtils.setBit(chessboard.bitboards[piece], target_square);

            chessboard.mailbox[source_square] = NO_PIECE_CONSTANT;
            chessboard.mailbox[target_square] = piece;

            // crazy house
            if (BitBoardUtils.getBit(chessboard.promoted_pieces, source_square) &&
                    chessboard.gameVariant == GameVariant.CRAZY_HOUSE) {
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
            // castling
            int king_target, rook_target, rook_piece, rook_source;
            rook_source = target_square;

            if (chessboard.side == white) {
                rook_piece = R;
                if (target_square > source_square) {
                    king_target = g1;
                    rook_target = f1;
                    if (!chessboard.isChess960) rook_source = h1;
                }
                else {
                    king_target = c1;
                    rook_target = d1;
                    if (!chessboard.isChess960) rook_source = a1;
                }
            } else {
                rook_piece = r;
                if (target_square > source_square) {
                    king_target = g8;
                    rook_target = f8;
                    if (!chessboard.isChess960) rook_source = h8;
                }
                else {
                    king_target = c8;
                    rook_target = d8;
                    if (!chessboard.isChess960) rook_source = a8;
                }
            }

            // king
            chessboard.bitboards[piece] = BitBoardUtils.popBit(chessboard.bitboards[piece], source_square);
            chessboard.bitboards[piece] = BitBoardUtils.setBit(chessboard.bitboards[piece], king_target);
            chessboard.hash_key ^= Zobrist.piece_keys[piece][source_square];
            chessboard.hash_key ^= Zobrist.piece_keys[piece][king_target];

            chessboard.mailbox[source_square] = NO_PIECE_CONSTANT;
            chessboard.mailbox[king_target] = piece;

            // rook
            chessboard.bitboards[rook_piece] = BitBoardUtils.popBit(chessboard.bitboards[rook_piece], rook_source);
            chessboard.bitboards[rook_piece] = BitBoardUtils.setBit(chessboard.bitboards[rook_piece], rook_target);
            chessboard.hash_key ^= Zobrist.piece_keys[rook_piece][rook_source];
            chessboard.hash_key ^= Zobrist.piece_keys[rook_piece][rook_target];

            chessboard.mailbox[rook_source] = NO_PIECE_CONSTANT;
            chessboard.mailbox[rook_target] = rook_piece;
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

            chessboard.mailbox[target_square] = promoted_piece;

            if(chessboard.gameVariant == GameVariant.CRAZY_HOUSE) {
                chessboard.promoted_pieces |= (1L << target_square);
                chessboard.hash_key ^= Zobrist.promoted_keys[target_square];
            }

            // add promoted piece into the hash key
            chessboard.hash_key ^= Zobrist.piece_keys[promoted_piece][target_square];
        }

        if (isAtomic && capture && !castling && !enpass) {
            // if atomic and capture move, apply atomic explosion
            atomicCastleMask = applyAtomicExplosion(chessboard, target_square);
        }

        // handle enpassant captures
        if (enpass){
            if (chessboard.side == white){
                chessboard.bitboards[p] = BitBoardUtils.popBit(chessboard.bitboards[p], target_square - 8);
                chessboard.mailbox[target_square - 8] = NO_PIECE_CONSTANT;
                chessboard.hash_key ^= Zobrist.piece_keys[p][target_square - 8];

                if (chessboard.gameVariant == GameVariant.CRAZY_HOUSE) {
                    chessboard.hash_key ^= Zobrist.pocket_keys[P][chessboard.pocket[P]];
                    chessboard.pocket[P]++;
                    chessboard.hash_key ^= Zobrist.pocket_keys[P][chessboard.pocket[P]];
                }
            } else {
                chessboard.bitboards[P] = BitBoardUtils.popBit(chessboard.bitboards[P], target_square + 8);
                chessboard.mailbox[target_square + 8] = NO_PIECE_CONSTANT;
                chessboard.hash_key ^= Zobrist.piece_keys[P][target_square + 8];

                if (chessboard.gameVariant == GameVariant.CRAZY_HOUSE) {
                    chessboard.hash_key ^= Zobrist.pocket_keys[p][chessboard.pocket[p]];
                    chessboard.pocket[p]++;
                    chessboard.hash_key ^= Zobrist.pocket_keys[p][chessboard.pocket[p]];
                }
            }

            if (isAtomic) {
                // if atomic and enpassant, apply atomic explosion
                atomicCastleMask = applyAtomicExplosion(chessboard, target_square);
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

        if(chessboard.isChess960) {
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

        // AND chessboard.castle with atomic castle mask
        chessboard.castle &= atomicCastleMask;

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
        if(chessboard.gameVariant == GameVariant.THREE_CHECK) {
            chessboard.check_count_history[white][chessboard.ply] = chessboard.check_count[white];
            chessboard.check_count_history[black][chessboard.ply] = chessboard.check_count[black];

            // get checker
            int checkersInfo = ChessboardUtils.getChecker(chessboard);

            // if king is in check, (opponent)
            boolean inCheck = (checkersInfo & (1 << 12)) != 0;

            if (chessboard.gameVariant == GameVariant.THREE_CHECK && inCheck) {
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
        if (chessboard.gameVariant == GameVariant.THREE_CHECK) {
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

        boolean isAtomic = chessboard.gameVariant == GameVariant.ATOMIC;
        boolean hadExplosion = isAtomic && capture;

        // if had explosion on last move,
        if (hadExplosion) {
            // restore it
            int expCount = chessboard.explosion_count_history[chessboard.ply];
            for (int i = 0; i < expCount; i++) {
                int bb = chessboard.explosion_piece_history[chessboard.ply][i];
                int sq = chessboard.explosion_square_history[chessboard.ply][i];
                chessboard.bitboards[bb] = BitBoardUtils.setBit(chessboard.bitboards[bb], sq);
                chessboard.mailbox[sq] = bb;
            }
        }

        if (is_drop) {
            // unmake drop
            chessboard.pocket[piece]++;

            chessboard.bitboards[piece] = BitBoardUtils.popBit(chessboard.bitboards[piece], target_square);
            chessboard.mailbox[target_square] = NO_PIECE_CONSTANT;
        } else if (castling) {
            // unmake castling
            int k_target, r_target, rook_piece, rook_source;
            rook_source = target_square;

            if (chessboard.side == white) {
                rook_piece = R;
                if (target_square > source_square) { // king side castling
                    k_target = g1;
                    r_target = f1;
                    if (!chessboard.isChess960) rook_source = h1;
                }
                else { // queen side castling
                    k_target = c1;
                    r_target = d1;
                    if (!chessboard.isChess960) rook_source = a1;
                }
            } else {
                rook_piece = r;
                if (target_square > source_square) { // king side castling
                    k_target = g8;
                    r_target = f8;
                    if (!chessboard.isChess960) rook_source = h8;
                }
                else { // queen side castling
                    k_target = c8;
                    r_target = d8;
                    if (!chessboard.isChess960) rook_source = a8;
                }
            }

            // unmake king
            chessboard.bitboards[piece] = BitBoardUtils.popBit(chessboard.bitboards[piece], k_target);
            chessboard.bitboards[piece] = BitBoardUtils.setBit(chessboard.bitboards[piece], source_square);
            chessboard.mailbox[k_target] = NO_PIECE_CONSTANT;
            chessboard.mailbox[source_square] = piece;

            // unmake rook
            chessboard.bitboards[rook_piece] = BitBoardUtils.popBit(chessboard.bitboards[rook_piece], r_target);
            chessboard.bitboards[rook_piece] = BitBoardUtils.setBit(chessboard.bitboards[rook_piece], rook_source);
            chessboard.mailbox[r_target] = NO_PIECE_CONSTANT;
            chessboard.mailbox[rook_source] = rook_piece;
        } else {
            // unmake piece
            if (promoted_piece != 0) {
                chessboard.bitboards[promoted_piece] =
                        BitBoardUtils.popBit(chessboard.bitboards[promoted_piece],
                                target_square);

                if (chessboard.gameVariant == GameVariant.CRAZY_HOUSE) {
                    chessboard.promoted_pieces =
                            BitBoardUtils.popBit(chessboard.promoted_pieces,
                                    target_square);
                }
            } else {
                chessboard.bitboards[piece] = BitBoardUtils.popBit(chessboard.bitboards[piece], target_square);

                if (chessboard.gameVariant == GameVariant.CRAZY_HOUSE &&
                        BitBoardUtils.getBit(chessboard.promoted_pieces, target_square)) {
                    chessboard.promoted_pieces = BitBoardUtils.popBit(chessboard.promoted_pieces, target_square);
                    chessboard.promoted_pieces = BitBoardUtils.setBit(chessboard.promoted_pieces, source_square);
                }
            }
            chessboard.bitboards[piece] = BitBoardUtils.setBit(chessboard.bitboards[piece], source_square);

            chessboard.mailbox[target_square] = NO_PIECE_CONSTANT;
            chessboard.mailbox[source_square] = piece;
        }

        // if normal capture move
        if (capture && !enpass) {
            chessboard.bitboards[captured_piece] =
                    BitBoardUtils.setBit(chessboard.bitboards[captured_piece], target_square);
            chessboard.mailbox[target_square] = captured_piece;

            if (chessboard.gameVariant == GameVariant.CRAZY_HOUSE) {
                boolean was_promoted = chessboard.promoted_captured_history[chessboard.ply];
                if (was_promoted) {
                    chessboard.pocket[(chessboard.side == white) ? P : p]--;
                    chessboard.promoted_pieces = BitBoardUtils.setBit(chessboard.promoted_pieces, target_square);
                } else {
                    chessboard.pocket[normalizePieceColor(captured_piece, chessboard.side)]--;
                }
            }
        }

        // if enpassant
        if (enpass) {
            if (chessboard.side == white) {
                chessboard.bitboards[p] = BitBoardUtils.setBit(chessboard.bitboards[p], target_square - 8);
                chessboard.mailbox[target_square - 8] = p;
                if (chessboard.gameVariant == GameVariant.CRAZY_HOUSE) chessboard.pocket[P]--;
            } else {
                chessboard.bitboards[P] = BitBoardUtils.setBit(chessboard.bitboards[P], target_square + 8);
                chessboard.mailbox[target_square + 8] = P;
                if (chessboard.gameVariant == GameVariant.CRAZY_HOUSE) chessboard.pocket[p]--;
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
     * Check the square is attacked with another occupancy
     *
     * @param chessboard chess board
     * @param square square
     * @param attackerSide attacker side
     * @param tempOcc occupancy
     * @return true if the square is attacked, false otherwise
     */
    public static boolean isSquareAttackedWithOcc(Chessboard chessboard, int square, int attackerSide, long tempOcc) {
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
     * Get whether this square is attacked or not with occupancy, bitboards (for atomic)
     *
     * @param chessboard chess board
     * @param square square
     * @param attackerSide attacker side
     * @param tempOcc temp occupancy
     * @return whether this square is attacked or not
     */
    public static boolean isSquareAttackedWithOccAtomic(Chessboard chessboard, int square, int attackerSide,
                                                        long tempOcc) {
        if (attackerSide == white &&
                (Attacks.pawn_attacks[black][square] & chessboard.bitboards[P]) != 0) return true;
        if (attackerSide == black &&
                (Attacks.pawn_attacks[white][square] & chessboard.bitboards[p]) != 0) return true;
        if ((Attacks.knight_attacks[square] &
                (attackerSide == white ? chessboard.bitboards[N] : chessboard.bitboards[n])) != 0) return true;

        long bishopsQueens = (attackerSide == white) ?
                (chessboard.bitboards[B] | chessboard.bitboards[Q])
                : (chessboard.bitboards[b] | chessboard.bitboards[q]);
        if ((Attacks.getBishopAttacks(square, tempOcc) & bishopsQueens) != 0) return true;

        long rooksQueens = (attackerSide == white) ?
                (chessboard.bitboards[R] | chessboard.bitboards[Q])
                : (chessboard.bitboards[r] | chessboard.bitboards[q]);
        if ((Attacks.getRookAttacks(square, tempOcc) & rooksQueens) != 0) return true;

        return false;
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
        if (chessboard.gameVariant != GameVariant.ATOMIC &&
                (Attacks.king_attacks[square] & (side == white ?
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

    /**
     * Normalize castling move target square to normal castling rook move style (e1g1, e1c1, etc.) if the
     * given chess board isn't chess 960 <p>
     *
     * Example : given chess board's chess 960 flag is false, and source square is 'e1', target square is 'h1',
     * and the target result square is 'g1'
     *
     * @return normalized target square
     */
    public static int normalizeCastleTarget(Chessboard chessboard, int source_square, int target_square) {
        if (chessboard.isChess960) return target_square;

        int pieceType = ChessboardUtils.getPieceTypeOnSquare(chessboard, source_square);
        return normalizeCastleTarget(chessboard, source_square, target_square, pieceType);
    }

    /**
     * Normalize castling move target square to normal castling rook move style (e1g1, e1c1, etc.) if the
     * given chess board isn't chess 960 <p>
     *
     * Example : given chess board's chess 960 flag is false, and source square is 'e1', target square is 'h1',
     * and the target result square is 'g1'
     *
     * @param pieceType piece type on source square (should be king)
     * @return normalized target square
     */
    public static int normalizeCastleTarget(Chessboard chessboard, int source_square, int target_square,
                                            int pieceType) {
        if (chessboard.isChess960) return target_square;

        int targetType = ChessboardUtils.getPieceTypeOnSquare(chessboard, target_square);

        if (pieceType == K && targetType == R) {
            if (source_square == e1 && target_square == h1) return g1;
            if (source_square == e1 && target_square == a1) return c1;
        }
        if (pieceType == k && targetType == r) {
            if (source_square == e8 && target_square == h8) return g8;
            if (source_square == e8 && target_square == a8) return c8;
        }
        return target_square;
    }

    /**
     * Get whether this move is king safe. <br>
     * Should not the king is in check.
     */
    private static boolean isMoveKingSafeNotInCheck(Chessboard chessboard, int move, int kingSq,
                                                    long pinnedPieces) {
        if (EncodeMove.getMoveCastling(move)) return true;

        int side = chessboard.side;
        int oppSide = side ^ 1;
        int source_square = EncodeMove.getMoveSource(move);
        int target_square = EncodeMove.getMoveTarget(move);
        int piece = EncodeMove.getMovePiece(move);

        if (piece == K || piece == k) {
            long tempOcc = BitBoardUtils.popBit(chessboard.occupancies[both], source_square);
            return !isSquareAttackedWithOcc(chessboard, target_square, oppSide, tempOcc);
        }

        boolean isPinned = BitBoardUtils.getBit(pinnedPieces, source_square);
        long pinRay = isPinned ? RAY_LINE[kingSq][source_square] : ~0L;

        if (EncodeMove.getMoveEnpassant(move)) {
            return BitBoardUtils.getBit(pinRay, target_square)
                    && isEnPassantSafe(chessboard, kingSq, source_square, target_square, side);
        }

        return BitBoardUtils.getBit(pinRay, target_square);
    }

    /**
     * Check whether this move is legal or not <br>
     * This doesn't check the drop move. if you want to check drop move, go to {@link #isLegalDrop(Chessboard, int, int)}.
     *
     * @param source_square source square
     * @param target_square target square
     * @param promotion_type promotion type
     * @return whether this move is legal or not
     */
    public static int isLegalMove(Chessboard chessboard, int source_square, int target_square, int promotion_type) {
        target_square = normalizeCastleTarget(chessboard, source_square, target_square);
        if (promotion_type == NO_PIECE_CONSTANT) promotion_type = 0;

        switch (chessboard.gameVariant) {
            case GIVEAWAY:
            case SUICIDE:
            case ATOMIC:
            case RACING_KINGS:
                // these variants don't have king check masks so don't have to go pseudo legal
                return isLegalMoveFullScan(chessboard, source_square, target_square, promotion_type);
            case HORDE:
                if (chessboard.side == white) {
                    // if white to move (pawn side), don't have king check masks so don't have to go pseudo legal
                    return isLegalMoveFullScan(chessboard, source_square, target_square, promotion_type);
                }
                // but if black to move (normal chess move), go to pseudo-legal
                break;
            default:
                break;
        }

        int side = chessboard.side;
        int kingSq = BitBoardUtils.getLS1BIndex(side == white ? chessboard.bitboards[K] : chessboard.bitboards[k]);

        if (isSquareAttacked(chessboard, kingSq, side ^ 1)) {
            // if king is under attacked, generate full move and check whether this defensing move is legal
            return isLegalMoveFullScan(chessboard, source_square, target_square, promotion_type);
        }

        int[] pieceMoves = MoveCache.MOVE_GENERATOR_CACHE.get();

        // generate pseudo legal moves for source_square
        int piece_move_count = generatePseudoLegalMovesForSquare(chessboard, source_square, pieceMoves);

        // get pinned pieces for checking this move is legal
        long pinnedPieces = getPinnedPiecesBitboard(chessboard, kingSq, side);

        for (int i = 0; i < piece_move_count; i++) {
            int move = pieceMoves[i];
            // don't have to check the source square because this already masked on generatePseudoLegalMovesForSquare
            if (EncodeMove.getMoveTarget(move) == target_square
                    && EncodeMove.getMovePromoted(move) == promotion_type) {
                // make sure this move is king safe.
                return isMoveKingSafeNotInCheck(chessboard, move, kingSq, pinnedPieces) ? move : ILLEGAL_MOVE;
            }
        }

        return ILLEGAL_MOVE;
    }

    /**
     * Get whether this move is legal move with generating fully-legal, and generating all possible moves.
     */
    private static int isLegalMoveFullScan(Chessboard chessboard, int source_square, int target_square,
                                           int promotion_type) {
        int[] move_list = MoveCache.MOVE_GENERATOR_CACHE.get();
        int move_count = MoveGenerator.generateMoves(chessboard, move_list);

        for (int count = 0; count < move_count; count++) {
            int possible_move = move_list[count];

            if (EncodeMove.getMoveSource(possible_move) == source_square
                    && EncodeMove.getMoveTarget(possible_move) == target_square
                    && EncodeMove.getMovePromoted(possible_move) == promotion_type
                    && !EncodeMove.getMoveDrop(possible_move)) {
                return possible_move;
            }
        }

        return ILLEGAL_MOVE;
    }

    /**
     * Check whether dropping the given piece type on the target square is legal (Crazyhouse).
     * <p>
     * The piece type's color is normalized to match {@code chessboard.side} automatically,
     * so callers do not need to pass a color-correct piece constant
     * (e.g. passing {@code N} while Black to move is treated the same as passing {@code n}).
     *
     * @param chessboard chessboard
     * @param target_square drop destination square
     * @param piece_type piece type to drop (color is normalized to the side to move)
     * @return the encoded move if legal, otherwise {@link MoveGenerator#ILLEGAL_MOVE}
     */
    public static int isLegalDrop(Chessboard chessboard, int target_square, int piece_type) {
        int[] move_list = MoveCache.MOVE_GENERATOR_CACHE.get();
        int move_count = MoveGenerator.generateMoves(chessboard, move_list);

        piece_type = normalizePieceColor(piece_type, chessboard.side);

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
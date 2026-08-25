package com.pepero.jcb.api.analyze;

import com.pepero.jcb.api.enums.Square;
import com.pepero.jcb.core.bitboard.Attacks;
import com.pepero.jcb.core.bitboard.BitBoardUtils;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.MoveGenerator;

import java.util.ArrayList;
import java.util.List;

import static com.pepero.jcb.core.ChessboardUtils.getPieceTypeOnSquare;
import static com.pepero.jcb.core.MoveGenerator.isSquareAttacked;
import static com.pepero.jcb.core.constant.SideToMove.*;
import static com.pepero.jcb.core.constant.EncodedPieces.*;

public class ChessTacticUtils {

    /**
     * Get attacks for 'pieceType'
     *
     * @param chessboard chess board
     * @param square square
     * @param pieceType piece type
     * @return attack for pieceType
     */
    public static long getAttacksFor(Chessboard chessboard, int square, int pieceType) {
        long occupancy = chessboard.occupancies[both];

        return switch (pieceType) {
            case N, n -> Attacks.knight_attacks[square];
            case B, b -> Attacks.getBishopAttacks(square, occupancy);
            case R, r -> Attacks.getRookAttacks(square, occupancy);
            case Q, q -> Attacks.getBishopAttacks(square, occupancy) | Attacks.getRookAttacks(square, occupancy);
            case K, k -> Attacks.king_attacks[square];
            case P -> Attacks.pawn_attacks[white][square];
            case p -> Attacks.pawn_attacks[black][square];
            default -> 0L;
        };
    }

    /**
     * Normalize piece type to white piece type (0~5)
     *
     * @param pieceType piece type
     * @return normalized piece type
     */
    private static int normalize(int pieceType) {
        return pieceType <= K ? pieceType : pieceType - 6;
    }

    // P N B R Q K
    private static final int[] PIECE_VALUE = {1,3,3,5,9,100};

    /**
     * Get skewered piece index
     *
     * @param chessboard chess board
     * @param attackerSq attacker square
     * @param frontSq blocking piece square
     * @param white_attacking is white attacking
     * @return skewered square (if not exists, -1)
     */
    public static int getSkeweredSquare(Chessboard chessboard, int attackerSq, int frontSq, boolean white_attacking) {
        int frontType = getPieceTypeOnSquare(chessboard, frontSq);
        if (frontType == -1) return -1;

        // if front square is our piece, it's not skewer
        boolean frontIsWhite = frontType <= K;
        if (frontIsWhite == white_attacking) return -1;

        if (ChessTacticUtils.isPinnedToKing(chessboard, attackerSq)) {
            int attackerType = getPieceTypeOnSquare(chessboard, attackerSq);
            boolean attackerIsWhite = attackerType <= K;
            int kingSq = BitBoardUtils.getLS1BIndex(
                    attackerIsWhite ? chessboard.bitboards[K] : chessboard.bitboards[k]);
            long pinLine = MoveGenerator.RAY_LINE[kingSq][attackerSq];

            if ((pinLine & (1L << frontSq)) == 0L) {
                return -1;
            }
        }

        long ray = MoveGenerator.RAY_LINE[attackerSq][frontSq];
        if (ray == 0L) return -1;

        long occupancy = chessboard.occupancies[both];
        long occupancyWithoutFront = occupancy & ~(1L << frontSq);

        long behindAttacks = Attacks.getBishopAttacks(frontSq, occupancyWithoutFront)
                | Attacks.getRookAttacks(frontSq, occupancyWithoutFront);
        behindAttacks &= ray;

        long towardAttackerSide = MoveGenerator.RAY_BETWEEN[attackerSq][frontSq] | (1L << attackerSq);
        behindAttacks &= ~towardAttackerSide;
        behindAttacks &= ~(1L << frontSq);

        behindAttacks &= occupancy;

        if (behindAttacks == 0L) return -1;

        int behindSq = BitBoardUtils.getLS1BIndex(behindAttacks);
        int behindType = getPieceTypeOnSquare(chessboard, behindSq);
        if (behindType == -1) return -1;

        boolean behindIsWhite = behindType <= K;
        if (behindIsWhite != frontIsWhite) return -1;

        int frontValue = PIECE_VALUE[normalize(frontType)];
        int behindValue = PIECE_VALUE[normalize(behindType)];

        if (frontValue < behindValue) return -1;
        if (see(chessboard, frontSq, attackerSq) <= 0) return -1;
        if (see(chessboard, behindSq, attackerSq, occupancyWithoutFront) <= 0) return -1;

        return behindSq;
    }

    /**
     * Get attacker attacking 'square' behind 'blockerSq' (xray)
     *
     * @param chessboard chess board
     * @param square square
     * @param blockerSq blocker square
     * @param white_attacking is white attacking
     * @return discovered attacker bitboard
     */
    public static long getXrayBehind(Chessboard chessboard, int square, int blockerSq, boolean white_attacking) {
        long ray = MoveGenerator.RAY_LINE[square][blockerSq];
        if (ray == 0L) return 0L;

        long occupancy = chessboard.occupancies[both];
        long occupancyWithoutBlocker = occupancy & ~(1L << blockerSq);

        long bishopLike = white_attacking ?
                (chessboard.bitboards[B] | chessboard.bitboards[Q]) :
                (chessboard.bitboards[b] | chessboard.bitboards[q]);
        long rookLike = white_attacking ?
                (chessboard.bitboards[R] | chessboard.bitboards[Q]) :
                (chessboard.bitboards[r] | chessboard.bitboards[q]);

        long xrayAttackers = 0L;

        long diagonalAttacks = Attacks.getBishopAttacks(square, occupancyWithoutBlocker);
        xrayAttackers |= diagonalAttacks & bishopLike & ray;

        long straightAttacks = Attacks.getRookAttacks(square, occupancyWithoutBlocker);
        xrayAttackers |= straightAttacks & rookLike & ray;

        xrayAttackers &= ~(1L << blockerSq);

        return xrayAttackers;
    }

    /**
     * Get fork target candidate pieces that can be captured
     *
     * @param chessboard chess board
     * @param attackerSq attacker square
     * @param attackBitboard attacking bitboard
     * @return target bitboard
     */
    public static long getLegalForkTargets(Chessboard chessboard, int attackerSq, long attackBitboard) {
        int attackerType = getPieceTypeOnSquare(chessboard, attackerSq);
        boolean isWhite = attackerType <= K;

        long enemyOccupancy = isWhite ? chessboard.occupancies[black] : chessboard.occupancies[white];
        long targets = attackBitboard & enemyOccupancy;

        if (Long.bitCount(targets) < 2) return 0L;

        // king can't be pinned
        if (attackerType != K && attackerType != k) {
            if (ChessTacticUtils.isPinnedToKing(chessboard, attackerSq)) {
                int kingSq = BitBoardUtils.getLS1BIndex(
                        isWhite ? chessboard.bitboards[K] : chessboard.bitboards[k]);
                long pinLine = MoveGenerator.RAY_LINE[kingSq][attackerSq];
                targets &= pinLine;
            }
        }

       if(Long.bitCount(targets) < 2)
           return 0L;

        return targets;
    }

    /**
     * Get attacker is forking
     *
     * @param chessboard chess board
     * @param attackerSq attacker square
     * @param targets target mask (can be gotten on 'getLegalForkTargets' method)
     * @return whether it is a fork
     */
    public static boolean isFork(Chessboard chessboard, int attackerSq, long targets) {
        if (Long.bitCount(targets) < 2) return false;

        int attackerType = getPieceTypeOnSquare(chessboard, attackerSq);

        // get piece value
        int attackerValue = PIECE_VALUE[normalize(attackerType)];

        // get fork value
        int highest = -1, secondHighest = -1;

        // whether king is under attack
        boolean kingIncluded = false;
        int otherUndefendedCount = 0;

        long temp = targets;
        while (temp != 0L) {
            int sq = BitBoardUtils.getLS1BIndex(temp);
            int type = getPieceTypeOnSquare(chessboard, sq);
            int value = PIECE_VALUE[normalize(type)];

            boolean isKing = (type == K || type == k);
            boolean isUndefended = ChessTacticUtils.isHanging(chessboard, sq);

            if (isKing) {
                kingIncluded = true;
            } else if (isUndefended) {
                otherUndefendedCount++;
            }

            if (value > highest) {
                secondHighest = highest;
                highest = value;
            } else if (value > secondHighest) {
                secondHighest = value;
            }

            temp = BitBoardUtils.popBit(temp, sq);
        }

        boolean valueCondition = secondHighest > attackerValue;
        boolean safetyCondition = (kingIncluded && otherUndefendedCount >= 1)
                || (!kingIncluded && otherUndefendedCount >= 2);

        return valueCondition || safetyCondition;
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

        if (piece_type == -1 || piece_type == K || piece_type == k) return false;

        boolean is_white = piece_type <= K;
        long occupancy = chessboard.occupancies[both];

        long ray = MoveGenerator.RAY_LINE[square][pinned_piece];
        if (ray == 0L) return false;

        if ((MoveGenerator.RAY_BETWEEN[square][pinned_piece] & occupancy) != 0) {
            return false;
        }

        long enemy_sliders;

        if (square / 8 == pinned_piece / 8 || square % 8 == pinned_piece % 8) {
            enemy_sliders = is_white ?
                    (chessboard.bitboards[r] | chessboard.bitboards[q]) :
                    (chessboard.bitboards[R] | chessboard.bitboards[Q]);

            long attacks = Attacks.getRookAttacks(pinned_piece, occupancy) & ray;
            return (attacks & enemy_sliders) != 0;
        }
        else {
            enemy_sliders = is_white ?
                    (chessboard.bitboards[b] | chessboard.bitboards[q]) :
                    (chessboard.bitboards[B] | chessboard.bitboards[Q]);

            long attacks = Attacks.getBishopAttacks(pinned_piece, occupancy) & ray;
            return (attacks & enemy_sliders) != 0;
        }
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
     * Get piece square pining 'pinned_piece'
     *
     * @param chessboard chessboard
     * @param square king square or something
     * @param pinned_piece the piece to get another piece that pining this piece
     *
     * @return pining piece square
     */
    public static int getPinnerSquare(Chessboard chessboard, int square, int pinned_piece) {
        int piece_type = getPieceTypeOnSquare(chessboard, pinned_piece);
        if (piece_type == -1 || piece_type == K || piece_type == k) return -1;

        boolean is_white = piece_type <= K;
        long occupancy = chessboard.occupancies[both];

        long ray = MoveGenerator.RAY_LINE[square][pinned_piece];
        if (ray == 0L) return -1;

        if ((MoveGenerator.RAY_BETWEEN[square][pinned_piece] & occupancy) != 0) {
            return -1;
        }

        long enemy_sliders;
        long attacks;

        if (square / 8 == pinned_piece / 8 || square % 8 == pinned_piece % 8) {
            enemy_sliders = is_white ?
                    (chessboard.bitboards[r] | chessboard.bitboards[q]) :
                    (chessboard.bitboards[R] | chessboard.bitboards[Q]);
            attacks = Attacks.getRookAttacks(pinned_piece, occupancy) & ray;
        } else {
            enemy_sliders = is_white ?
                    (chessboard.bitboards[b] | chessboard.bitboards[q]) :
                    (chessboard.bitboards[B] | chessboard.bitboards[Q]);
            attacks = Attacks.getBishopAttacks(pinned_piece, occupancy) & ray;
        }

        long pinner = attacks & enemy_sliders;
        if (pinner == 0L) return -1;

        return BitBoardUtils.getLS1BIndex(pinner);
    }

    /**
     * Get pieces that attacking the square
     *
     * @param chessboard chessboard
     * @param square square
     * @param white_attacking is white/black attacking
     */
    public static long getAttackersTo(Chessboard chessboard, int square, boolean white_attacking) {
        return getAttackersTo(chessboard, square, white_attacking, chessboard.occupancies[both]);
    }

    /**
     * Get pieces that attacking the square
     *
     * @param chessboard chessboard
     * @param square square
     * @param white_attacking is white/black attacking
     * @param occupancy current position occupancy
     */
    public static long getAttackersTo(Chessboard chessboard, int square, boolean white_attacking, long occupancy) {
        long attackers = 0L;

        long pawns = white_attacking ? chessboard.bitboards[P] : chessboard.bitboards[p];
        attackers |= Attacks.pawn_attacks[white_attacking ? black : white][square] & pawns;

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

        return attackers & occupancy;
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
        return (MoveGenerator.RAY_LINE[sq1][sq2] & (1L << sq3)) != 0;
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

    /**
     * Among the attackers targeting `targetSq`, <br>
     * identify the starting attacker that yields the maximum SEE from the attacker's perspective.
     *
     * @return attacker square
     */
    public static int bestSeeAgainst(Chessboard chessboard, int targetSq, boolean attackerIsWhite) {
        long attackers = getAttackersTo(chessboard, targetSq, attackerIsWhite);

        int best = Integer.MIN_VALUE;
        while (attackers != 0L) {
            int sq = BitBoardUtils.getLS1BIndex(attackers);
            int value = see(chessboard, targetSq, sq);
            if (value > best) best = value;
            attackers = BitBoardUtils.popBit(attackers, sq);
        }

        return best;
    }

    /**
     * Get whether this piece on 'square' is hanging or not (by see calculation)
     *
     * @param chessboard chess board
     * @param square square
     * @return whether the piece on this square is hanging or not
     */
    public static boolean isHanging(Chessboard chessboard, int square) {
        int pieceType = getPieceTypeOnSquare(chessboard, square);
        if (pieceType == -1) return false;

        boolean targetIsWhite = pieceType <= K;
        long best = bestSeeAgainst(chessboard, square, !targetIsWhite);
        return best > 0;
    }

    public static List<Square> findHangingPieces(Chessboard chessboard, boolean whiteAttacking) {
        List<Square> hanging = new ArrayList<>();

        long enemyOccupancy = whiteAttacking ? chessboard.occupancies[black] : chessboard.occupancies[white];
        while (enemyOccupancy != 0L) {
            int sq = BitBoardUtils.getLS1BIndex(enemyOccupancy);

            long attackers = ChessTacticUtils.getAttackersTo(chessboard, sq, whiteAttacking);
            if (attackers != 0L && ChessTacticUtils.isHanging(chessboard, sq)) {
                hanging.add(Square.fromIndex(sq));
            }

            enemyOccupancy = BitBoardUtils.popBit(enemyOccupancy, sq);
        }

        return hanging;
    }

    private static long leastValuableAttacker(Chessboard chessboard, long attackers, boolean isWhite) {
        int[] order = isWhite ? new int[]{P, N, B, R, Q, K} : new int[]{p, n, b, r, q, k};
        for (int type : order) {
            long bb = chessboard.bitboards[type] & attackers;
            if (bb != 0L) return bb & -bb;
        }
        return 0L;
    }

    /**
     * Static Exchange Evaluation
     *
     * @return piece evaluation
     */
    public static int see(Chessboard chessboard, int targetSq, int attackerSq) {
        return see(chessboard, targetSq, attackerSq, chessboard.occupancies[both]);
    }

    /**
     * Static Exchange Evaluation, starting from a given occupancy
     * (e.g. a hypothetical position where some blocker has already been removed)
     *
     * @param occupancy starting occupancy to simulate the exchange from
     * @return piece evaluation
     */
    public static int see(Chessboard chessboard, int targetSq, int attackerSq, long occupancy) {
        int targetType = getPieceTypeOnSquare(chessboard, targetSq);
        int attackerType = getPieceTypeOnSquare(chessboard, attackerSq);
        if (attackerType == -1) return 0;

        int[] gain = new int[32];
        int d = 0;
        gain[0] = (targetType == -1) ? 0 : PIECE_VALUE[normalize(targetType)];

        long fromBit = 1L << attackerSq;
        int currentType = attackerType;
        boolean sideIsWhite = currentType <= K;

        while (fromBit != 0L) {
            d++;
            gain[d] = PIECE_VALUE[normalize(currentType)] - gain[d - 1];

            if (Math.max(-gain[d - 1], gain[d]) < 0) break;

            occupancy &= ~fromBit;
            sideIsWhite = !sideIsWhite;

            long attackers = getAttackersTo(chessboard, targetSq, sideIsWhite, occupancy);
            fromBit = leastValuableAttacker(chessboard, attackers, sideIsWhite);
            if (fromBit != 0L) {
                currentType = getPieceTypeOnSquare(chessboard, BitBoardUtils.getLS1BIndex(fromBit));
            }
        }

        while (--d > 0) {
            gain[d - 1] = -Math.max(-gain[d - 1], gain[d]);
        }

        return gain[0];
    }
}

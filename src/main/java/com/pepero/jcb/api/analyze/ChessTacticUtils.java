package com.pepero.jcb.api.analyze;

import com.pepero.jcb.bitboard.Attacks;
import com.pepero.jcb.bitboard.BitBoardUtils;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.MoveGenerator;

import static com.pepero.jcb.constant.EncodedPieces.*;
import static com.pepero.jcb.constant.SideToMove.*;
import static com.pepero.jcb.core.ChessboardUtils.getPieceTypeOnSquare;
import static com.pepero.jcb.core.MoveGenerator.isSquareAttacked;

public class ChessTacticUtils {

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
}

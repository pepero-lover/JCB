package com.pepero.jcb.api.analyze;

import com.pepero.jcb.api.enums.Square;
import com.pepero.jcb.bitboard.BitBoardUtils;
import com.pepero.jcb.core.Chessboard;

import java.util.ArrayList;
import java.util.List;

import static com.pepero.jcb.constant.EncodedPieces.*;

public class TacticAnalyzer {

    /**
     * Analyze and return Tactic DTO
     *
     * @param chessboard chessboard
     * @param whiteAttacking is white attacking
     * @return tactic dto
     */
    public static List<TacticFinding> analyze(Chessboard chessboard, boolean whiteAttacking) {
        List<TacticFinding> findings = new ArrayList<>();
        findings.addAll(findForks(chessboard, whiteAttacking));
        findings.addAll(findSkewers(chessboard, whiteAttacking));
        findings.addAll(findXrays(chessboard, whiteAttacking));
        findings.addAll(findPins(chessboard, whiteAttacking));
        return findings;
    }

    private static List<TacticFinding> findForks(Chessboard chessboard, boolean whiteAttacking) {
        List<TacticFinding> results = new ArrayList<>();

        int[] pieceTypes = whiteAttacking
                ? new int[]{P, N, B, R, Q, K}
                : new int[]{p, n, b, r, q, k};

        for (int pieceType : pieceTypes) {
            long bitboard = chessboard.bitboards[pieceType];

            while (bitboard != 0L) {
                int sq = BitBoardUtils.getLS1BIndex(bitboard);

                long attackBitboard = ChessTacticUtils.getAttacksFor(chessboard, sq, pieceType);
                long targets = ChessTacticUtils.getLegalForkTargets(chessboard, sq, attackBitboard);

                if (ChessTacticUtils.isFork(chessboard, sq, targets)) {
                    List<Square> targetList = new ArrayList<>();
                    long temp = targets;
                    while (temp != 0L) {
                        int t = BitBoardUtils.getLS1BIndex(temp);
                        targetList.add(Square.fromIndex(t));
                        temp = BitBoardUtils.popBit(temp, t);
                    }
                    results.add(new TacticFinding(TacticType.FORK, Square.fromIndex(sq), targetList));
                }

                bitboard = BitBoardUtils.popBit(bitboard, sq);
            }
        }

        return results;
    }

    private static List<TacticFinding> findSkewers(Chessboard chessboard, boolean whiteAttacking) {
        List<TacticFinding> results = new ArrayList<>();

        int[] sliderTypes = whiteAttacking
                ? new int[]{B, R, Q}
                : new int[]{b, r, q};

        long enemyOccupancy = whiteAttacking
                ? chessboard.occupancies[1] // black
                : chessboard.occupancies[0]; // white

        for (int pieceType : sliderTypes) {
            long bitboard = chessboard.bitboards[pieceType];

            while (bitboard != 0L) {
                int attackerSq = BitBoardUtils.getLS1BIndex(bitboard);

                long attackBitboard = ChessTacticUtils.getAttacksFor(chessboard, attackerSq, pieceType);
                long directTargets = attackBitboard & enemyOccupancy;

                long temp = directTargets;
                while (temp != 0L) {
                    int frontSq = BitBoardUtils.getLS1BIndex(temp);

                    int behindSq = ChessTacticUtils.getSkeweredSquare(chessboard, attackerSq, frontSq, whiteAttacking);
                    if (behindSq != -1) {
                        results.add(new TacticFinding(
                                TacticType.SKEWER,
                                Square.fromIndex(attackerSq),
                                List.of(Square.fromIndex(frontSq), Square.fromIndex(behindSq))
                        ));
                    }

                    temp = BitBoardUtils.popBit(temp, frontSq);
                }

                bitboard = BitBoardUtils.popBit(bitboard, attackerSq);
            }
        }

        return results;
    }

    private static List<TacticFinding> findPins(Chessboard chessboard, boolean whiteAttacking) {
        List<TacticFinding> results = new ArrayList<>();

        boolean defenderIsWhite = !whiteAttacking;
        long defenderOccupancy = defenderIsWhite ? chessboard.occupancies[0] : chessboard.occupancies[1]; // white/black
        int kingSq = BitBoardUtils.getLS1BIndex(
                defenderIsWhite ? chessboard.bitboards[K] : chessboard.bitboards[k]);

        long temp = defenderOccupancy;
        while (temp != 0L) {
            int sq = BitBoardUtils.getLS1BIndex(temp);

            if (sq != kingSq) {
                int pinnerSq = ChessTacticUtils.getPinnerSquare(chessboard, kingSq, sq);
                if (pinnerSq != -1) {
                    results.add(new TacticFinding(TacticType.PIN, Square.fromIndex(pinnerSq),
                            List.of(Square.fromIndex(sq))));
                }
            }

            temp = BitBoardUtils.popBit(temp, sq);
        }

        return results;
    }

    private static List<TacticFinding> findXrays(Chessboard chessboard, boolean whiteAttacking) {
        List<TacticFinding> results = new ArrayList<>();

        long myOccupancy = whiteAttacking ? chessboard.occupancies[0] : chessboard.occupancies[1]; // white/black
        long enemySliders = whiteAttacking
                ? (chessboard.bitboards[b] | chessboard.bitboards[r] | chessboard.bitboards[q])
                : (chessboard.bitboards[B] | chessboard.bitboards[R] | chessboard.bitboards[Q]);

        long targetTemp = myOccupancy;
        while (targetTemp != 0L) {
            int targetSq = BitBoardUtils.getLS1BIndex(targetTemp);

            long directAttackers = ChessTacticUtils.getAttackersTo(chessboard, targetSq, !whiteAttacking);
            long directSliders = directAttackers & enemySliders;

            long blockerTemp = directSliders;
            while (blockerTemp != 0L) {
                int blockerSq = BitBoardUtils.getLS1BIndex(blockerTemp);

                long xrayBitboard = ChessTacticUtils.getXrayBehind(chessboard, targetSq, blockerSq, !whiteAttacking);
                long xrayTemp = xrayBitboard & enemySliders;

                while (xrayTemp != 0L) {
                    int xraySq = BitBoardUtils.getLS1BIndex(xrayTemp);
                    results.add(new TacticFinding(TacticType.XRAY, Square.fromIndex(xraySq),
                            List.of(Square.fromIndex(blockerSq), Square.fromIndex(targetSq))));
                    xrayTemp = BitBoardUtils.popBit(xrayTemp, xraySq);
                }

                blockerTemp = BitBoardUtils.popBit(blockerTemp, blockerSq);
            }

            targetTemp = BitBoardUtils.popBit(targetTemp, targetSq);
        }

        return results;
    }
}

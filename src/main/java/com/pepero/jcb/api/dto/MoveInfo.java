package com.pepero.jcb.api.dto;

import com.pepero.jcb.api.enums.Piece;
import com.pepero.jcb.api.enums.PieceType;
import com.pepero.jcb.api.enums.Square;
import com.pepero.jcb.core.GameVariants;
import com.pepero.jcb.encode.EncodeMove;

import java.util.Objects;

public record MoveInfo(
        Square sourceSquare,
        Square targetSquare,
        Piece pieceType,
        PieceType promotionPiece,
        boolean capture,
        boolean pawnDoublePush,
        boolean enpassant,
        boolean castling,
        boolean isDrop,
        int originEncodedData
) {
    public MoveInfo(int moveData) {
        this(
                Square.fromIndex(EncodeMove.getMoveSource(moveData)),
                Square.fromIndex(EncodeMove.getMoveTarget(moveData)),
                Piece.fromIndex(EncodeMove.getMovePiece(moveData)),
                EncodeMove.getMovePromoted(moveData) == 0 ?
                        PieceType.NONE : PieceType.fromIndex(EncodeMove.getMovePromoted(moveData)),
                EncodeMove.getMoveCapture(moveData),
                EncodeMove.getMoveDouble(moveData),
                EncodeMove.getMoveEnpassant(moveData),
                EncodeMove.getMoveCastling(moveData),
                EncodeMove.getMoveDrop(moveData),
                moveData
        );
    }

    @Override
    public String toString() {
        return toLanString();
    }

    public String toLanString() {
        return toLanString(GameVariants.STANDARD);
    }

    public String toLanString(GameVariants variants) {
        if (castling && variants == GameVariants.STANDARD) {
            if (sourceSquare == Square.e1 && targetSquare == Square.h1) return "e1g1";
            if (sourceSquare == Square.e1 && targetSquare == Square.a1) return "e1c1";
            if (sourceSquare == Square.e8 && targetSquare == Square.h8) return "e8g8";
            if (sourceSquare == Square.e8 && targetSquare == Square.a8) return "e8c8";
        }

        return EncodeMove.moveToString(originEncodedData);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MoveInfo moveInfo = (MoveInfo) o;
        return sourceSquare == moveInfo.sourceSquare &&
                targetSquare == moveInfo.targetSquare &&
                promotionPiece == moveInfo.promotionPiece;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceSquare, targetSquare, promotionPiece);
    }
}

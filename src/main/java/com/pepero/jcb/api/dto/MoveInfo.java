package com.pepero.jcb.api.dto;

import com.pepero.jcb.api.enums.Piece;
import com.pepero.jcb.api.enums.PieceType;
import com.pepero.jcb.api.enums.Square;
import com.pepero.jcb.encode.EncodeMove;

import java.util.Objects;

public final class MoveInfo {
    private final int originEncodedData;

    public MoveInfo(int moveData) {
        this.originEncodedData = moveData;
    }

    public int originEncodedData() { return originEncodedData; }
    public Square sourceSquare() { return Square.fromIndex(EncodeMove.getMoveSource(originEncodedData)); }
    public Square targetSquare() { return Square.fromIndex(EncodeMove.getMoveTarget(originEncodedData)); }
    public Piece pieceType() { return Piece.fromIndex(EncodeMove.getMovePiece(originEncodedData)); }
    public PieceType promotionPiece() {
        int promo = EncodeMove.getMovePromoted(originEncodedData);
        return promo == 0 ? PieceType.NONE : PieceType.fromIndex(promo);
    }
    public boolean capture() { return EncodeMove.getMoveCapture(originEncodedData); }
    public boolean pawnDoublePush() { return EncodeMove.getMoveDouble(originEncodedData); }
    public boolean enpassant() { return EncodeMove.getMoveEnpassant(originEncodedData); }
    public boolean castling() { return EncodeMove.getMoveCastling(originEncodedData); }
    public boolean isDrop() { return EncodeMove.getMoveDrop(originEncodedData); }

    @Override
    public String toString() {
        return toLanString();
    }

    public String toLanString() {
        return EncodeMove.moveToString(originEncodedData);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MoveInfo other)) return false;
        return originEncodedData == other.originEncodedData;
    }

    @Override
    public int hashCode() {
        return originEncodedData;
    }
}
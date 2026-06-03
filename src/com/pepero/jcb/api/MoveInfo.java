package com.pepero.jcb.api;

import com.pepero.jcb.encode.EncodeMove;

public class MoveInfo {
    private final Square sourceSquare;
    private final Square targetSquare;
    private final Piece pieceType;
    private final PieceType promotionPiece;
    private final boolean capture;
    private final boolean pawnDoublePush;
    private final boolean enpassant;
    private final boolean castling;

    private final int originEncodedData;

    public MoveInfo(int moveData) {
        this.sourceSquare = Square.fromIndex(EncodeMove.getMoveSource(moveData));
        this.targetSquare = Square.fromIndex(EncodeMove.getMoveTarget(moveData));
        this.pieceType = Piece.fromIndex(EncodeMove.getMovePiece(moveData));
        this.promotionPiece = EncodeMove.getMovePromoted(moveData) == 0 ? PieceType.NONE :
                PieceType.fromIndex(EncodeMove.getMovePromoted(moveData));
        this.capture = EncodeMove.getMoveCapture(moveData);
        this.pawnDoublePush = EncodeMove.getMoveDouble(moveData);
        this.enpassant = EncodeMove.getMoveEnpassant(moveData);
        this.castling = EncodeMove.getMoveCastling(moveData);

        this.originEncodedData = moveData;
    }

    public Square getSourceSquare() {
        return sourceSquare;
    }

    public Square getTargetSquare() {
        return targetSquare;
    }

    public Piece getPieceType() {
        return pieceType;
    }

    public PieceType getPromotionPiece() {
        return promotionPiece;
    }

    public boolean isCapture() {
        return capture;
    }

    public boolean isPawnDoublePush() {
        return pawnDoublePush;
    }

    public boolean isEnpassant() {
        return enpassant;
    }

    public boolean isCastling() {
        return castling;
    }


    public int getOriginEncodedData() {
        return originEncodedData;
    }

    @Override
    public String toString() {
        boolean promotion = promotionPiece != PieceType.NONE;

        return String.valueOf(sourceSquare) + targetSquare + (promotion ? promotionPiece : "");
    }
}

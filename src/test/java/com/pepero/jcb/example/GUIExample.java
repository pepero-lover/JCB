package com.pepero.jcb.example;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.enums.Square;
import java.util.List;

public class GUIExample {
    public static void main(String[] args) {
        ChessGame chessGame = ChessGame.startPosition();

        // Assume the user clicked on the white pawn on e2.
        Square clickedSquare = Square.e2;

        // Get all legal moves for the piece on e2.
        List<MoveInfo> legalMoves = chessGame.getLegalMovesForSource(clickedSquare);

        System.out.println("List of moves available for the piece on e2 (LAN)");
        for (MoveInfo move : legalMoves) {
            System.out.println("- " + move.toString());
        }

        // Check the current piece score (positive: White is ahead / negative: Black is ahead)
        System.out.println("Current piece score (from White's perspective): " + chessGame.getPieceScore());
    }
}

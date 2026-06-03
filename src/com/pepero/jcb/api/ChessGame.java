package com.pepero.jcb.api;

import com.pepero.jcb.api.exception.EmptyMoveUndoException;
import com.pepero.jcb.api.exception.FENConvertException;
import com.pepero.jcb.api.exception.IllegalMoveException;
import com.pepero.jcb.bitboard.BitBoardUtils;
import com.pepero.jcb.constant.BoardSquares;
import com.pepero.jcb.constant.CastlingRights;
import com.pepero.jcb.convertstr.ConvertStringMoveUtils;
import com.pepero.jcb.core.ChessBoardUtils;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.Initializer;
import com.pepero.jcb.core.MoveGenerator;
import com.pepero.jcb.encode.EncodeMove;

import java.util.*;

import static com.pepero.jcb.constant.BoardSquares.no_sq;
import static com.pepero.jcb.constant.EncodedPieces.*;
import static com.pepero.jcb.constant.SideToMove.white;
import static com.pepero.jcb.core.ChessBoardUtils.ascii_pieces;
import static com.pepero.jcb.core.MoveGenerator.ILLEGAL_MOVE;
import static com.pepero.jcb.core.MoveGenerator.generateMoves;

public class ChessGame {
    static {
        Initializer.init();
    }

    // Chessboard class
    private final Chessboard chessboard;

    // Move history
    private final List<MoveInfo> moveHistory;

    /**
     * Initialize position with FEN string
     * @param fen fen string
     */
    public ChessGame(String fen) {
        if (fen == null || fen.trim().isEmpty()) {
            throw new FENConvertException("FEN string is empty!");
        }

        chessboard = new Chessboard();
        moveHistory = new ArrayList<>();

        try {
            ChessBoardUtils.parseFen(this.chessboard, fen);
        } catch (Exception e){
            throw new FENConvertException("Could not parse the fen.");
        }
    }

    /**
     * Initialize position to start position
     */
    public ChessGame() {
        this.chessboard = new Chessboard();
        this.moveHistory = new ArrayList<>();
        ChessBoardUtils.parseFen(this.chessboard, Chessboard.start_position);
    }



    /**
     * Get FEN on this ChessGame
     * @return fen
     */
    public String getFEN() {
        return ChessBoardUtils.getFen(this.chessboard);
    }



    /**
     * Make move on this ChessGame (LAN MOVE)
     *
     * @param moveString move like e2e4, e7e5 (LAN move string)
     */
    public void makeMove(String moveString) {
        int encoded_move = ConvertStringMoveUtils.parseLanToEncodedMove(
                this.chessboard, moveString
        );

        boolean isSuccess = MoveGenerator.makeMove(this.chessboard, encoded_move);
        if(!isSuccess) throw new IllegalMoveException(moveString);

        moveHistory.add(new MoveInfo(encoded_move));
    }

    /**
     * Make move on this ChessGame (Source square, Target square, Promotion Type)
     *
     * @param sourceSquare Source square (you can make square on BoardSquares.java)
     * @param targetSquare Target square (you can make square on BoardSquares.java)
     * @param promotionType Promotion type like queen, rook, bishop and knight (PieceType.QUEEN, PieceType.ROOK ... )
     */
    public void makeMove(Square sourceSquare, Square targetSquare, PieceType promotionType) {
        Objects.requireNonNull(sourceSquare, "The source square can not be null!");
        Objects.requireNonNull(targetSquare, "The target square can not be null!");
        Objects.requireNonNull(promotionType, "The promotion type can not be null!");

        if(promotionType != PieceType.NONE && promotionType != PieceType.QUEEN && promotionType != PieceType.ROOK &&
            promotionType != PieceType.BISHOP && promotionType != PieceType.KNIGHT)
            throw new IllegalMoveException("Promotion Piece type is unknown! please use like PieceType.QUEEN, PieceType.ROOK");

        int isLegal = MoveGenerator.isLegalMove(this.chessboard, sourceSquare.getIndex(), targetSquare.getIndex(),
                promotionType.getPieceType());

        if(isLegal == ILLEGAL_MOVE){
            String promoChar = (promotionType != PieceType.NONE) ?
                    String.valueOf(ChessBoardUtils.promotion_pieces[promotionType.getPieceType()]) : "";
            throw new IllegalMoveException(BoardSquares.square_to_coordinates[sourceSquare.getIndex()]
                    + BoardSquares.square_to_coordinates[targetSquare.getIndex()]
                    + promoChar);
        }

        int encoded_move = ConvertStringMoveUtils.parseMoveDataToEncodedMove(
                this.chessboard, sourceSquare.getIndex(), targetSquare.getIndex(), promotionType.getPieceType()
        );

        boolean isSuccess = MoveGenerator.makeMove(this.chessboard, encoded_move);
        if(!isSuccess) throw new IllegalMoveException(
                String.valueOf(sourceSquare) + targetSquare + (promotionType != PieceType.NONE ? promotionType : "")
        );

        moveHistory.add(new MoveInfo(encoded_move));
    }

    /**
     * Make move on this ChessGame (Source square, Target square)
     *
     * @param sourceSquare Source square (you can make square on BoardSquares.java)
     * @param targetSquare Target square (you can make square on BoardSquares.java)
     */
    public void makeMove(Square sourceSquare, Square targetSquare) {
        makeMove(sourceSquare, targetSquare, PieceType.NONE);
    }

    /**
     * Make move on this ChessGame (MoveInfo)
     *
     * @param moveInfo MoveInfo class
     */
    public void makeMove(MoveInfo moveInfo) {
        boolean isSuccess = MoveGenerator.makeMove(this.chessboard, moveInfo.getOriginEncodedData());
        if(!isSuccess) throw new IllegalMoveException(moveInfo.toString());

        moveHistory.add(new MoveInfo(moveInfo.getOriginEncodedData()));
    }

    /**
     * Unmake previous move on this ChessGame
     *
     * @return unmade move info
     */
    public MoveInfo unmakeMove() {
        if(moveHistory.isEmpty()) {
            throw new EmptyMoveUndoException();
        }

        MoveInfo moveInfo = moveHistory.getLast();

        this.chessboard.takeBack();
        moveHistory.removeFirst();

        return moveInfo;
    }

    /**
     * Get move history
     *
     * @return move history
     */
    public List<MoveInfo> getMoveHistory(){
        return Collections.unmodifiableList(moveHistory);
    }

    public MoveInfo getLastMove() {
        if (this.moveHistory.isEmpty()) return null;
        return this.moveHistory.getLast();
    }

    /**
     * Get white turn
     *
     * @return white turn
     */
    public boolean getTurn() {
        return this.chessboard.side == white;
    }

    /**
     * Get captured piece
     *
     * @param isWhite if white, returns black captured piece. if black, returns white captured piece.
     * @return captured piece
     */
    public Map<PieceType, Integer> getCapturedPieces(boolean isWhite) {
        Map<PieceType, Integer> captured = new EnumMap<>(PieceType.class);

        int initPawn = 8, initKnight = 2, initBishop = 2, initRook = 2, initQueen = 1;

        if (isWhite) {
            captured.put(PieceType.PAWN, initPawn - BitBoardUtils.countBits(chessboard.getBitboardPiece(p)));
            captured.put(PieceType.KNIGHT, initKnight - BitBoardUtils.countBits(chessboard.getBitboardPiece(n)));
            captured.put(PieceType.BISHOP, initBishop - BitBoardUtils.countBits(chessboard.getBitboardPiece(b)));
            captured.put(PieceType.ROOK, initRook - BitBoardUtils.countBits(chessboard.getBitboardPiece(r)));
            captured.put(PieceType.QUEEN, initQueen - BitBoardUtils.countBits(chessboard.getBitboardPiece(q)));
        } else {
            captured.put(PieceType.PAWN, initPawn - BitBoardUtils.countBits(chessboard.getBitboardPiece(P)));
            captured.put(PieceType.KNIGHT, initKnight - BitBoardUtils.countBits(chessboard.getBitboardPiece(N)));
            captured.put(PieceType.BISHOP, initBishop - BitBoardUtils.countBits(chessboard.getBitboardPiece(B)));
            captured.put(PieceType.ROOK, initRook - BitBoardUtils.countBits(chessboard.getBitboardPiece(R)));
            captured.put(PieceType.QUEEN, initQueen - BitBoardUtils.countBits(chessboard.getBitboardPiece(Q)));
        }

        captured.values().removeIf(count -> count <= 0);

        return captured;
    }

    /**
     * Get piece score
     *
     * @return piece score
     */
    public int getPieceScore() {
        int piece_score = 0;

        for(int white_piece = P; white_piece <= K; white_piece++) {
            int multiply = switch (white_piece) {
                case P -> 1;
                case N, B -> 3;
                case R -> 5;
                case Q -> 9;
                case K -> 100;
                default -> 0;
            };

            piece_score += BitBoardUtils.countBits(this.chessboard.getBitboardPiece(white_piece)) * multiply;
        }

        for(int black_piece = p; black_piece <= k; black_piece++) {
            int multiply = switch (black_piece) {
                case p -> 1;
                case n, b -> 3;
                case r -> 5;
                case q -> 9;
                case k -> 100;
                default -> 0;
            };

            piece_score -= BitBoardUtils.countBits(this.chessboard.getBitboardPiece(black_piece)) * multiply;
        }

        return piece_score;
    }

    /**
     * Get piece type on square
     * if not found, returns NONE
     *
     * @param square square
     * @return piece type
     */
    public Piece getPieceOnSquare(Square square){
        int piece_type = ChessBoardUtils.getPieceTypeOnSquare(this.chessboard, square.getIndex());

        return Piece.fromIndex(piece_type);
    }



    /**
     * Get legal moves on this chess game
     *
     * @return legal moves
     */
    public List<MoveInfo> getLegalMoves() {
        int[] move_list = new int[255];
        int move_count = generateMoves(this.chessboard, move_list);
        List<MoveInfo> result = new ArrayList<>(move_count);

        for (int count = 0; count < move_count; count++){
            int encodedMove = move_list[count];
            if(!MoveGenerator.makeMove(this.chessboard ,encodedMove))
                continue;
            this.chessboard.takeBack();
            result.add(new MoveInfo(encodedMove));
        }

        return result;
    }

    /**
     * Generate moves only one piece
     * <p>
     * Example : chessboard = start pos, square = e2, returns e2e3, e2e4
     *
     * @return generated move
     */
    public List<MoveInfo> getLegalMovesForPiece(Square square) {
        Objects.requireNonNull(square, "Square is null!");

        int[] move_list = new int[255];
        int move_count = generateMoves(this.chessboard, move_list);
        List<MoveInfo> result = new ArrayList<>(move_count);

        for (int count = 0; count < move_count; count++){
            int encodedMove = move_list[count];
            if(EncodeMove.getMoveSource(encodedMove) != square.getIndex()) continue;

            if(!MoveGenerator.makeMove(this.chessboard ,encodedMove))
                continue;
            this.chessboard.takeBack();
            result.add(new MoveInfo(encodedMove));
        }

        return result;
    }



    /**
     * Get board state (Map)(square)(piece)
     *
     * @return board state (Map)
     */
    public Map<Square, Piece> getBoardStateMap() {
        Map<Square, Piece> result = new HashMap<>();
        for(Square square : Square.values()) {
            Piece piece = getPieceOnSquare(square);
            if (piece != Piece.NONE) {
                result.put(square, piece);
            }
        }

        return result;
    }

    /**
     * Get whether this square is empty
     *
     * @param square square
     * @return whether this square is empty
     */
    public boolean isEmpty(Square square) {
        return getPieceOnSquare(square) == Piece.NONE;
    }



    /**
     * Get whether the king is under attack
     *
     * @return whether the king is under attack
     */
    public boolean isCheck() {
        return ChessBoardUtils.isCheck(this.chessboard);
    }

    /**
     * Get whether this position is checkmate
     *
     * @return whether this position is checkmate
     */
    public boolean isCheckmate() {
        return ChessBoardUtils.isCheckmate(this.chessboard);
    }

    /**
     * Get whether this position is stalemate
     *
     * @return whether this position is stalemate
     */
    public boolean isStalemate() {
        return ChessBoardUtils.isStaleMate(this.chessboard);
    }

    /**
     * Get whether this position is threefold repetition
     *
     * @return whether this position is threefold repetition
     */
    public boolean isThreefoldRepetition(){
        return ChessBoardUtils.getRepetitionCount(this.chessboard) == 3;
        // because getRepetitionCount method returns 3 if the position is repeated over 3 times
    }

    /**
     * Get whether this position is fifty moves draw
     *
     * @return whether this position is fifty moves draw
     */
    public boolean isFiftyMoves() {
        return chessboard.half_ply >= 50;
    }

    /**
     * Get whether this game overed.
     * If not, return GameOverReason.NOTGAMEOVER.
     * <p>
     * Types : CHECKMATE, STALEMATE, THREEFOLD REPETITION, FIFTY MOVES DRAW
     *
     * @return game over reason (if not, return GameOverReason.NOTGAMEOVER)
     */
    public GameOverReason isGameOver() {
        if(isCheckmate()) return GameOverReason.CHECKMATE;
        if(isStalemate()) return GameOverReason.STALEMATE;
        if(isThreefoldRepetition()) return GameOverReason.THREEFOLD;
        if(isFiftyMoves()) return GameOverReason.FIFTYMOVES;

        return GameOverReason.NOTGAMEOVER;
    }

    /**
     * Convert LAN (like e2e4 e7e5 g1f3) to SAN (like e4 e5 Nf3)
     *
     * @param lanMove LAN move
     * @return converted SAN move
     */
    public String toSan(String lanMove){
        return ConvertStringMoveUtils.translateLanSequence(this.chessboard, lanMove).trim();
    }

    /**
     * Convert move data to SAN (like e4 e5 Nf3)
     *
     * @param moveData move data
     * @return converted SAN move
     */
    public String toSan(List<MoveInfo> moveData){
        StringBuilder sb = new StringBuilder();
        for(MoveInfo moveInfo : moveData) {
            sb.append(moveInfo.toString()).append(" ");
        }

        return ConvertStringMoveUtils.translateLanSequence(this.chessboard, sb.toString()).trim();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(256);
        char[] board = new char[64];

        // initialize board with dots
        Arrays.fill(board, '.');

        // loop over all piece types
        for (int piece = P; piece <= k; piece++) {
            long bitboardPiece = chessboard.getBitboardPiece(piece);
            char pieceChar = ascii_pieces[piece];

            // a bit scanning: find all set bits for this piece type
            while (bitboardPiece != 0L) {
                int square = BitBoardUtils.getLS1BIndex(bitboardPiece);
                board[square] = pieceChar;
                bitboardPiece = BitBoardUtils.popBit(bitboardPiece,square);
            }
        }

        sb.append('\n');

        // loop over board ranks
        for (int rank = 0; rank < 8; rank++) {
            // append ranks
            sb.append("  ").append(8 - rank).append("  ");

            // loop over board files
            for (int file = 0; file < 8; file++) {
                int square = rank * 8 + file;
                // prints char piece from our mapped board
                sb.append(" ").append(board[square]);
            }
            // print new line every rank
            sb.append('\n');
        }

        // print board files
        sb.append("\n      a b c d e f g h \n\n");

        // print side to move
        sb.append("      Side:     ").append(chessboard.side == white ? "white" : "black").append("\n");

        // print enpassant square
        sb.append("      Enpassant:   ").append((chessboard.enpassant != no_sq) ?
                BoardSquares.square_to_coordinates[chessboard.enpassant] : "no").append("\n");

        // print castling rights
        sb.append("      Castling:  ")
                .append(((chessboard.castle & CastlingRights.WK) != 0) ? 'K' : '-')
                .append(((chessboard.castle & CastlingRights.WQ) != 0) ? 'Q' : '-')
                .append(((chessboard.castle & CastlingRights.BK) != 0) ? 'k' : '-')
                .append(((chessboard.castle & CastlingRights.BQ) != 0) ? 'q' : '-')
                .append("\n");

        return sb.toString();
    }
}

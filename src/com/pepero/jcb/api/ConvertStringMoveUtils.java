package com.pepero.jcb.api;

import com.pepero.jcb.api.enums.Square;
import com.pepero.jcb.api.exception.ConvertMoveException;
import com.pepero.jcb.api.exception.IllegalMoveException;
import com.pepero.jcb.constant.BoardSquares;
import com.pepero.jcb.constant.EncodedPieces;
import com.pepero.jcb.core.ChessBoardUtils;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.MoveGenerator;
import com.pepero.jcb.encode.EncodeMove;

import static com.pepero.jcb.constant.EncodedPieces.*;
import static com.pepero.jcb.constant.SideToMove.white;
import static com.pepero.jcb.core.MoveGenerator.ILLEGAL_MOVE;

public class ConvertStringMoveUtils {
    private static class TranslateResult {
        String moveString;
        int moveData;

        TranslateResult(String moveString, int moveData) {
            this.moveString = moveString;
            this.moveData = moveData;
        }
    }

    /**
     * Convert lan move to san move
     * <p>
     * examples. <p>
     * e2e4 -> e4 (it depends, but if start position, this is right) <p>
     * g1f3 -> Nf3
     * @param lan string like e2e4 d7c8q
     * @return converted san string (if error occurs, returns null)
     *
     * @throws ConvertMoveException - if converting move failed
     */
    private static TranslateResult translateLan(Chessboard chessboard, String lan){
        // check the length
        if(lan.length() < 4 || lan.length() >= 6) {
            // the length is too short ( or too long )
            throw new ConvertMoveException("Length of the string is too short ( or too long )!", lan);
        }

        int source_square = BoardSquares.coordinates_to_square(lan.substring(0,2));
        int target_square = BoardSquares.coordinates_to_square(lan.substring(2,4));

        // lan is not correct
        if(source_square == -1 || target_square == -1) {
            throw new ConvertMoveException("Square string is not correct!", lan);
        }

        int type = ChessBoardUtils.getPieceTypeOnSquare(chessboard, source_square);

        // if piece is not found
        if(type == -1) {
            throw new ConvertMoveException("Piece not found!", lan);
        }

        StringBuilder sb = new StringBuilder();

        boolean castle = false;

        if (type == k || type == K) {
            int file_diff = Math.abs((target_square % 8) - (source_square % 8));
            if (file_diff == 2) {
                if ((target_square % 8) == 6) sb.append("O-O");
                else if ((target_square % 8) == 2) sb.append("O-O-O");

                castle = true;
            }
        }

        int source_file = source_square % 8;
        int source_rank = source_square / 8;

        int target_file = target_square % 8;
        int target_rank = target_square / 8;

        boolean is_capture = ChessBoardUtils.getPieceTypeOnSquare(chessboard, target_square) != -1;

        if(type == p || type == P){
            boolean is_pawn_capture = source_file != target_file;

            // check if this move is capture
            if (is_pawn_capture){
                // add file number
                sb.append(BoardSquares.square_to_coordinates[source_square].charAt(0));

                // add capture
                sb.append("x");
            }

            // if the move is promotion
            if (target_rank == 0 || target_rank == 7){
                try {
                    sb
                            .append("=")
                            .append(String.valueOf(lan.charAt(4)).toUpperCase()); // get promotion string
                } catch (IndexOutOfBoundsException e){
                    // promotion char not found
                    return null;
                }
            }
        } else {
            if(!castle) {
                // add piece type
                sb.append(EncodedPieces.encodedPieceToString(type));

                // add disambiguation
                if (!(type == k || type == K)) {
                    int[] move_list = new int[255];
                    int move_count = MoveGenerator.generateMoves(chessboard, move_list);

                    int going_piece_count = 0;
                    boolean equal_file = false;
                    boolean equal_rank = false;

                    for (int count = 0; count < move_count; count++) {
                        int move = move_list[count];

                        if (EncodeMove.getMovePiece(move) == type &&
                                EncodeMove.getMoveTarget(move) == target_square) {
                            if (EncodeMove.getMoveSource(move) == source_square) continue;

                            going_piece_count++;

                            // if file equal
                            if (EncodeMove.getMoveSource(move) % 8 ==
                                    source_file) {
                                equal_file = true;
                            }

                            // if rank equal
                            if (EncodeMove.getMoveSource(move) / 8 ==
                                    source_rank) {
                                equal_rank = true;
                            }
                        }
                    }

                    if (going_piece_count != 0) {
                        if (equal_file && !equal_rank) {
                            // add file number
                            sb.append(BoardSquares.square_to_coordinates[source_square].charAt(0));
                        } else if (!equal_file && equal_rank) {
                            // add rank number
                            sb.append(BoardSquares.square_to_coordinates[source_square].charAt(1));
                        } else if (equal_file) {
                            // add square number
                            sb.append(BoardSquares.square_to_coordinates[source_square]);
                        } else {
                            // add file number
                            sb.append(BoardSquares.square_to_coordinates[source_square].charAt(0));
                        }
                    }
                }

                // check if this move is capture
                if (is_capture) {
                    // move is capture
                    sb.append("x");
                }
            }
        }

        if(!castle){
            // add target square
            sb.append(BoardSquares.square_to_coordinates[target_square]);
        }

        int[] move_list = new int[255];
        int move_count = MoveGenerator.generateMoves(chessboard, move_list);

        boolean found_move = false;
        int move = -1;

        for (int count = 0; count < move_count; count++) {
            move = move_list[count];
            if (EncodeMove.getMoveSource(move) == source_square && EncodeMove.getMoveTarget(move) == target_square) {
                found_move = true;

                chessboard.copyBoard();

                MoveGenerator.makeMove(chessboard, move);

                if(ChessBoardUtils.isCheckmate(chessboard)) {
                    sb.append("#");
                } else if(ChessBoardUtils.isCheck(chessboard)) {
                    sb.append("+");
                }

                chessboard.takeBack();

                break;
            }
        }

        if(!found_move){
            throw new IllegalMoveException(lan);
        }

        return new TranslateResult(sb.toString(), move);
    }

    /**
     * Lan sequence to San sequence
     *
     * @param chessboard chessboard
     * @param lanSequence lan string
     * @return translated san sequence
     *
     * @throws ConvertMoveException - if converting move failed
     */
    public static String translateLanSequence(Chessboard chessboard, String lanSequence){
        if(lanSequence.trim().isEmpty()) return "";

        String[] lans = lanSequence.trim().split("\\s+");
        StringBuilder sanSequence = new StringBuilder();

        for (String lan : lans) {
            TranslateResult result = translateLan(chessboard, lan);

            if (result == null) {
                throw new ConvertMoveException("The result is null", lan);
            }

            sanSequence.append(result.moveString).append(" ");

            MoveGenerator.makeMove(chessboard, result.moveData);
        }

        for (int i = 0; i < lans.length; i++){
            chessboard.takeBack();
        }

        return sanSequence.toString();
    }

    /**
     * Parse LAN move to encoded move
     *
     * Example : e2e4 -> encoded int move data
     *
     * @param chessboard chessboard
     * @param lan LAN move
     * @return Parsed LAN move to encoded move
     *
     * @throws ConvertMoveException - if converting move failed
     */
    public static int parseLanToEncodedMove(Chessboard chessboard, String lan){
        // check the length
        if(lan.length() < 4 || lan.length() >= 6) {
            // the length is too short ( or too long )
            throw new ConvertMoveException("Length of the string is too short ( or too long )!", lan);
        }

        int source_square = Square.fromString(lan.substring(0,2)).getIndex();
        int target_square = Square.fromString(lan.substring(2,4)).getIndex();

        int promotion_type = 0;
        if(lan.length() == 5){
            promotion_type = ChessBoardUtils.char_to_encoded_piece.get(lan.charAt(4));
            if(chessboard.side == white) promotion_type -= 6;
        }

        // lan is not correct
        if(source_square == -1 || target_square == -1) {
            throw new ConvertMoveException("Square string is not correct!", lan);
        }

        int isLegal = MoveGenerator.isLegalMove(chessboard, source_square, target_square, promotion_type);

        if(isLegal != ILLEGAL_MOVE){
            return isLegal;
        }

        throw new IllegalMoveException(lan);
    }


    /**
     * Parse move data (source square, target square, promotion type)
     *
     * @param chessboard chessboard
     * @param source_square source square move data
     * @param target_square target square move data
     * @param promotion_type promotion type move data
     * @return Parsed encoded move
     *
     * @throws ConvertMoveException - if converting move failed
     */
    public static int parseMoveDataToEncodedMove(Chessboard chessboard, int source_square, int target_square,
                                                 int promotion_type){
        if(promotion_type == -1) promotion_type = 0;

        // source square is not correct
        if(source_square < 0 || source_square >= 64) {
            throw new ConvertMoveException("Source square string is not correct!");
        }

        // target square is not correct
        if(target_square < 0 || target_square >= 64) {
            throw new ConvertMoveException("Target square string is not correct!");
        }

        if(promotion_type < 0 || promotion_type > 11) {
            throw new ConvertMoveException("Promotion Type is not correct!");
        }

        if (promotion_type != 0) {
            if (promotion_type >= 6) {
                promotion_type -= 6;
            }
            if (chessboard.side != white) {
                promotion_type += 6;
            }
        }

        int isLegal = MoveGenerator.isLegalMove(chessboard, source_square, target_square, promotion_type);

        if(isLegal != ILLEGAL_MOVE){
            return isLegal;
        }

        throw new IllegalMoveException(BoardSquares.square_to_coordinates[source_square]
        +BoardSquares.square_to_coordinates[target_square]
        +(promotion_type!=0?ChessBoardUtils.promotion_pieces[promotion_type] : ""));
    }
}

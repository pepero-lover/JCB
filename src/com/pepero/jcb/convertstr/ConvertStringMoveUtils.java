package com.pepero.jcb.convertstr;

import com.pepero.jcb.constant.BoardSquares;
import com.pepero.jcb.constant.EncodedPieces;
import com.pepero.jcb.core.ChessBoardUtils;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.MoveGenerator;
import com.pepero.jcb.encode.EncodeMove;

import static com.pepero.jcb.constant.EncodedPieces.*;

public class ConvertStringMoveUtils {
    static class TranslateResult {
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
     */
    public static TranslateResult translateLan(Chessboard chessboard, String lan){
        // check the length
        if(lan.length() < 4 || lan.length() >= 6) {
            // the length is too short ( or too long )
            System.err.println("Length of the string is too short ( or too long )!");
            return null;
        }

        int source_square = BoardSquares.coordinates_to_square(lan.substring(0,2));
        int target_square = BoardSquares.coordinates_to_square(lan.substring(2,4));

        // lan is not correct
        if(source_square == -1 || target_square == -1) {
            System.err.println("Square string is not correct!");
            return null;
        }

        int type = ChessBoardUtils.getPieceTypeOnSquare(chessboard, source_square);

        // if piece is not found
        if(type == -1) {
            System.err.println("Piece not found!");
            return null;
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
        int target_rank = target_square / 8;

        boolean is_capture = ChessBoardUtils.getPieceTypeOnSquare(chessboard, target_square) != -1;

        if(type == p || type == P){
            // check if this move is capture
            if (is_capture){
                // add file number
                sb.append((char)(source_file - 1 + 'a'));

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
                        int move = move_list[move_count];

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
                            sb.append(source_file - 1 + 'a');
                        } else if (!equal_file && equal_rank) {
                            // add rank number
                            sb.append(source_rank + '0');
                        } else if (equal_file) {
                            // add square number
                            sb.append(BoardSquares.square_to_coordinates[source_square]);
                        } else {
                            // add file number
                            sb.append(source_file - 1 + 'a');
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

                MoveGenerator.makeMove(chessboard, move, MoveGenerator.ALL_MOVES);

                if(ChessBoardUtils.isCheck(chessboard)) {
                    sb.append("+");
                } else if(ChessBoardUtils.isCheckmate(chessboard)) {
                    sb.append("#");
                }

                chessboard.takeBack();

                break;
            }
        }

        if(!found_move){
            System.err.println("Illegal move!");
            return null;
        }

        return new TranslateResult(sb.toString(), move);
    }

    /**
     * Lan sequence to San sequence
     *
     * @param chessboard chessboard
     * @param lanSequence lan string
     * @return translated san sequence
     */
    public static String translateLanSequence(Chessboard chessboard, String lanSequence){
        if(lanSequence.trim().isEmpty()) return "";

        String[] lans = lanSequence.trim().split("\\s+");
        StringBuilder sanSequence = new StringBuilder();

        for (String lan : lans) {
            TranslateResult result = translateLan(chessboard, lan);

            if (result == null) {
                System.err.println("Failed to convert san string to lan string");
                return null;
            }

            sanSequence.append(result.moveString).append(" ");

            MoveGenerator.makeMove(chessboard, result.moveData, MoveGenerator.ALL_MOVES);
        }

        for (int i = 0; i < lans.length; i++){
            chessboard.takeBack();
        }

        return sanSequence.toString();
    }

    /**
     * Parse LAN move to encoded move
     *
     * @param chessboard chessboard
     * @param lan LAN move
     * @return Parsed LAN move to encoded move
     */
    public static int parseLanToEncodedMove(Chessboard chessboard, String lan){
        // check the length
        if(lan.length() < 4 || lan.length() >= 6) {
            // the length is too short ( or too long )
            System.err.println("Length of the string is too short ( or too long )!");
            return -1;
        }

        int source_square = BoardSquares.coordinates_to_square(lan.substring(0,2));
        int target_square = BoardSquares.coordinates_to_square(lan.substring(2,4));

        // lan is not correct
        if(source_square == -1 || target_square == -1) {
            System.err.println("Square string is not correct!");
            return -1;
        }

        int[] move_list = new int[255];
        int move_count = MoveGenerator.generateMoves(chessboard, move_list);

        int move;

        for (int count = 0; count < move_count; count++) {
            move = move_list[count];
            if (EncodeMove.getMoveSource(move) == source_square && EncodeMove.getMoveTarget(move) == target_square) {
                return move;
            }
        }

        System.err.println("Illegal move!");

        return -1;
    }
}

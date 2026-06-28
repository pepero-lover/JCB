package com.pepero.jcb.api.parse;

import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.enums.PieceType;
import com.pepero.jcb.api.enums.Square;
import com.pepero.jcb.api.exception.ConvertMoveException;
import com.pepero.jcb.api.exception.IllegalMoveException;
import com.pepero.jcb.constant.BoardSquares;
import com.pepero.jcb.constant.CastlingRights;
import com.pepero.jcb.constant.MoveCache;
import com.pepero.jcb.core.ChessboardUtils;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.GameVariants;
import com.pepero.jcb.core.MoveGenerator;
import com.pepero.jcb.encode.EncodeMove;

import static com.pepero.jcb.constant.EncodedPieces.*;
import static com.pepero.jcb.constant.SideToMove.black;
import static com.pepero.jcb.constant.SideToMove.white;
import static com.pepero.jcb.core.MoveGenerator.ILLEGAL_MOVE;

public class ConvertStringMoveUtils {
    private record TranslateResult(
            String moveString,
            int moveData
    ) {}

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
     * @throws IllegalMoveException - if move is illegal
     */
    private static TranslateResult parseLan(Chessboard chessboard, String lan){
        // if crazy house
        if (lan.contains("@")) {
            int encoded_move = parseLanToEncodedMove(chessboard, lan);

            StringBuilder sb = new StringBuilder(lan);

            boolean isSuccess = MoveGenerator.makeMove(chessboard, encoded_move);
            if (!isSuccess) throw new IllegalMoveException(lan, ChessboardUtils.getFen(chessboard));

            if(ChessboardUtils.isCheckmate(chessboard)) sb.append("#");
            else if(ChessboardUtils.isCheck(chessboard)) sb.append("+");

            MoveGenerator.unmakeMove(chessboard, encoded_move);
            return new TranslateResult(sb.toString(), encoded_move);
        }

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

        int type = ChessboardUtils.getPieceTypeOnSquare(chessboard, source_square);

        // if piece is not found
        if(type == -1) {
            throw new ConvertMoveException("Piece not found! ( FEN : " +
                    ChessboardUtils.getFen(chessboard) + " )", lan);
        }

        StringBuilder sb = new StringBuilder();

        int encoded_move = parseLanToEncodedMove(chessboard, lan);
        boolean castle = EncodeMove.getMoveCastling(encoded_move);

        if (castle) {
            int moveTgt = EncodeMove.getMoveTarget(encoded_move);
            boolean isKingSide = (chessboard.side == white) ?
                    (moveTgt == chessboard.king_side_rook_file + 56) :
                    (moveTgt == chessboard.king_side_rook_file);

            if (isKingSide) sb.append("O-O");
            else sb.append("O-O-O");
        }

        int source_file = source_square % 8;
        int source_rank = source_square / 8;

        int target_file = target_square % 8;
        int target_rank = target_square / 8;

        boolean is_capture = ChessboardUtils.getPieceTypeOnSquare(chessboard, target_square) != -1;

        String promotionStr = "";

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
            if (target_rank == 0 || target_rank == 7) {
                try {
                    promotionStr = "=" + String.valueOf(lan.charAt(4)).toUpperCase();
                } catch (IndexOutOfBoundsException e){
                    throw new ConvertMoveException("Promotion char not found!");
                }
            }
        } else {
            if(!castle) {
                // add piece type
                sb.append(encodedPieceToString(type));

                // add disambiguation
                if (!(type == k || type == K)) {
                    int[] move_list = MoveCache.CONVERT_MOVE_CACHE.get();
                    int move_count = MoveGenerator.generateMoves(chessboard, move_list);

                    int going_piece_count = 0;
                    boolean equal_file = false;
                    boolean equal_rank = false;

                    for (int count = 0; count < move_count; count++) {
                        int move = move_list[count];

                        if (EncodeMove.getMovePiece(move) == type &&
                                EncodeMove.getMoveTarget(move) == target_square) {
                            if (MoveGenerator.makeMove(chessboard, move)) {
                                going_piece_count++;
                                int other_src = EncodeMove.getMoveSource(move);

                                if (other_src != source_square) {
                                    if (other_src % 8 == source_file) equal_file = true;
                                    if (other_src / 8 == source_rank) equal_rank = true;
                                }
                                MoveGenerator.unmakeMove(chessboard, move);
                            }
                        }
                    }

                    if (going_piece_count > 1) {
                        if (!equal_file) {
                            sb.append(BoardSquares.square_to_coordinates[source_square].charAt(0));
                        } else if (!equal_rank) {
                            sb.append(BoardSquares.square_to_coordinates[source_square].charAt(1));
                        } else {
                            sb.append(BoardSquares.square_to_coordinates[source_square]);
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

        if(!castle) {
            sb.append(BoardSquares.square_to_coordinates[target_square]);
            sb.append(promotionStr);
        }

        boolean isSuccess = MoveGenerator.makeMove(chessboard, encoded_move);
        if (!isSuccess) {
            throw new IllegalMoveException(lan, ChessboardUtils.getFen(chessboard));
        }

        if(ChessboardUtils.isCheckmate(chessboard)) {
            sb.append("#");
        } else if(ChessboardUtils.isCheck(chessboard)) {
            sb.append("+");
        }

        MoveGenerator.unmakeMove(chessboard, encoded_move);

        return new TranslateResult(sb.toString(), encoded_move);
    }

    /**
     * Translate LAN string to SAN data
     *
     * @param chessboard chessboard
     * @param moveInfo move
     * @return Translated Result
     *
     * @throws IllegalMoveException - if move is illegal
     */
    private static TranslateResult parseLan(Chessboard chessboard, MoveInfo moveInfo) {
        int encoded_move = moveInfo.originEncodedData();

        // when drop move (crazy house)
        if (moveInfo.isDrop()) {
            StringBuilder sb = new StringBuilder();
            sb.append(encodedPieceToString(EncodeMove.getMovePiece(encoded_move)).toUpperCase());
            sb.append("@");
            sb.append(BoardSquares.square_to_coordinates[moveInfo.targetSquare().getIndex()]);

            boolean isSuccess = MoveGenerator.makeMove(chessboard, encoded_move);
            if (!isSuccess) throw new IllegalMoveException(moveInfo.toLanString(), ChessboardUtils.getFen(chessboard));
            if(ChessboardUtils.isCheckmate(chessboard)) sb.append("#");
            else if(ChessboardUtils.isCheck(chessboard)) sb.append("+");
            MoveGenerator.unmakeMove(chessboard, encoded_move);

            return new TranslateResult(sb.toString(), encoded_move);
        }

        int source_square = EncodeMove.getMoveSource(encoded_move);
        int target_square = EncodeMove.getMoveTarget(encoded_move);

        int type = ChessboardUtils.getPieceTypeOnSquare(chessboard, source_square);

        StringBuilder sb = new StringBuilder();

        // castling
        boolean castle = EncodeMove.getMoveCastling(encoded_move);
        if (castle) {
            boolean isKingSide = (chessboard.side == white) ?
                    (target_square == chessboard.king_side_rook_file + 56) :
                    (target_square == chessboard.king_side_rook_file);

            if (isKingSide) sb.append("O-O");
            else sb.append("O-O-O");
        }

        int source_file = source_square % 8;
        int source_rank = source_square / 8;

        int target_file = target_square % 8;

        boolean is_capture = ChessboardUtils.getPieceTypeOnSquare(chessboard, target_square) != -1 || moveInfo.enpassant();

        String promotionStr = "";

        // if pawn moved
        if (type == p || type == P) {
            boolean is_pawn_capture = source_file != target_file;

            if (is_pawn_capture) {
                sb.append(BoardSquares.square_to_coordinates[source_square].charAt(0));
                sb.append("x");
            }

            if (moveInfo.promotionPiece() != PieceType.NONE) {
                promotionStr = "=" + moveInfo.promotionPiece().name().substring(0, 1).toUpperCase();
                if (moveInfo.promotionPiece() == PieceType.KNIGHT) promotionStr = "=N";
            }
        } else {
            if (!castle) {
                sb.append(encodedPieceToString(type));

                if (!(type == k || type == K)) {
                    int[] move_list = MoveCache.CONVERT_MOVE_CACHE.get();
                    int move_count = MoveGenerator.generateMoves(chessboard, move_list);

                    int going_piece_count = 0;
                    boolean equal_file = false;
                    boolean equal_rank = false;

                    for (int count = 0; count < move_count; count++) {
                        int move = move_list[count];

                        if (EncodeMove.getMovePiece(move) == type &&
                                EncodeMove.getMoveTarget(move) == target_square) {
                            if (MoveGenerator.makeMove(chessboard, move)) {
                                going_piece_count++;
                                int other_src = EncodeMove.getMoveSource(move);

                                if (other_src != source_square) {
                                    if (other_src % 8 == source_file) equal_file = true;
                                    if (other_src / 8 == source_rank) equal_rank = true;
                                }
                                MoveGenerator.unmakeMove(chessboard, move);
                            }
                        }
                    }

                    if (going_piece_count > 1) {
                        if (!equal_file) {
                            sb.append(BoardSquares.square_to_coordinates[source_square].charAt(0));
                        } else if (!equal_rank) {
                            sb.append(BoardSquares.square_to_coordinates[source_square].charAt(1));
                        } else {
                            sb.append(BoardSquares.square_to_coordinates[source_square]);
                        }
                    }
                }

                if (is_capture) {
                    sb.append("x");
                }
            }
        }

        if (!castle) {
            sb.append(BoardSquares.square_to_coordinates[target_square]);
            sb.append(promotionStr);
        }

        boolean isSuccess = MoveGenerator.makeMove(chessboard, encoded_move);
        if (!isSuccess) {
            throw new IllegalMoveException(new MoveInfo(encoded_move).toLanString(), ChessboardUtils.getFen(chessboard));
        }

        if(ChessboardUtils.isCheckmate(chessboard)) {
            sb.append("#");
        } else if(ChessboardUtils.isCheck(chessboard)) {
            sb.append("+");
        }

        MoveGenerator.unmakeMove(chessboard, encoded_move);

        return new TranslateResult(sb.toString(), moveInfo.originEncodedData());
    }

    /**
     * Parse lan string data to san string
     *
     * @param chessboard chessboard
     * @param lan lan move string
     * @return san move string
     *
     * @throws ConvertMoveException - if converting move failed
     */
    public static String toSanString(Chessboard chessboard, String lan) {
        return ConvertStringMoveUtils.parseLan(chessboard, lan).moveString;
    }

    /**
     * Parse move data to san string
     *
     * @param chessboard chessboard
     * @param move move data
     * @return san move string
     *
     * @throws IllegalMoveException - if move is illegal
     */
    public static String toSanString(Chessboard chessboard, MoveInfo move) {
        return ConvertStringMoveUtils.parseLan(chessboard, move).moveString;
    }

    /**
     * Lan sequence to San sequence
     *
     * @param chessboard chessboard
     * @param lanSequence lan string
     * @return translated san sequence
     *
     * @throws ConvertMoveException - if converting move failed
     * @throws IllegalMoveException - if move is illegal
     */
    public static String translateLanSequence(Chessboard chessboard, String lanSequence){
        if(lanSequence.trim().isEmpty()) return "";

        String[] lans = lanSequence.trim().split("\\s+");
        StringBuilder sanSequence = new StringBuilder();

        int[] moveData = new int[lans.length];

        for (int i = 0; i < lans.length; i++) {
            String lan = lans[i];

            TranslateResult result = parseLan(chessboard, lan);

            sanSequence.append(result.moveString).append(" ");

            MoveGenerator.makeMove(chessboard, result.moveData);

            moveData[i] = result.moveData();
        }

        for (int i = lans.length - 1; i >= 0; i--){
            MoveGenerator.unmakeMove(chessboard, moveData[i]);
        }

        return sanSequence.toString();
    }

    /**
     * Parse LAN move to encoded move
     * <p>
     * Example : e2e4 -> encoded int move data
     *
     * @param chessboard chessboard
     * @param lan LAN move
     * @return Parsed LAN move to encoded move
     *
     * @throws ConvertMoveException - if converting move failed
     * @throws IllegalMoveException - if move is illegal
     */
    public static int parseLanToEncodedMove(Chessboard chessboard, String lan){
        // check crazy house
        if (lan.contains("@")) {
            String[] parts = lan.split("@");
            if (parts.length != 2) throw new ConvertMoveException("Invalid drop format!", lan);

            char pieceChar = parts[0].charAt(0);
            int target_square = Square.fromString(parts[1]).getIndex();
            if (target_square == -1) throw new ConvertMoveException("Invalid drop target square!", lan);

            int piece_type = ChessboardUtils.char_to_encoded_piece.get(pieceChar);
            if (chessboard.side == black && piece_type <= K) piece_type += 6;
            else if (chessboard.side == white && piece_type > K) piece_type -= 6;

            int isLegal = MoveGenerator.isLegalDrop(chessboard, target_square, piece_type);
            if (isLegal != ILLEGAL_MOVE) return isLegal;

            throw new IllegalMoveException(lan, ChessboardUtils.getFen(chessboard));
        }

        // check the length
        if(lan.length() < 4 || lan.length() >= 6) {
            // the length is too short ( or too long )
            throw new ConvertMoveException("Length of the string is too short ( or too long )!", lan);
        }

        int source_square = Square.fromString(lan.substring(0,2)).getIndex();
        int target_square = Square.fromString(lan.substring(2,4)).getIndex();

        int promotion_type = 0;
        if(lan.length() == 5){
            promotion_type = ChessboardUtils.char_to_encoded_piece.get(lan.charAt(4));
            if(chessboard.side == white) promotion_type -= 6;
        }

        // lan is not correct
        if(source_square == -1 || target_square == -1) {
            throw new ConvertMoveException("Square string is not correct!", lan);
        }

        int type = ChessboardUtils.getPieceTypeOnSquare(chessboard, source_square);

        if (chessboard.gameVariants == GameVariants.STANDARD) {
            if (type == K && chessboard.side == white && source_square == BoardSquares.e1) {
                if (target_square == BoardSquares.g1 && chessboard.king_side_rook_file != -1 &&
                        (chessboard.castle & CastlingRights.WK) != 0) {
                    target_square = chessboard.king_side_rook_file + 56;
                } else if (target_square == BoardSquares.c1 && chessboard.queen_side_rook_file != -1
                        && (chessboard.castle & CastlingRights.WQ) != 0) {
                    target_square = chessboard.queen_side_rook_file + 56;
                }
            } else if (type == k && chessboard.side == black && source_square == BoardSquares.e8) {
                if (target_square == BoardSquares.g8 && chessboard.king_side_rook_file != -1 &&
                        (chessboard.castle & CastlingRights.BK) != 0) {
                    target_square = chessboard.king_side_rook_file;
                } else if (target_square == BoardSquares.c8 && chessboard.queen_side_rook_file != -1 &&
                        (chessboard.castle & CastlingRights.BQ) != 0) {
                    target_square = chessboard.queen_side_rook_file;
                }
            }
        }

        int isLegal = MoveGenerator.isLegalMove(chessboard, source_square, target_square, promotion_type);

        if(isLegal != ILLEGAL_MOVE){
            return isLegal;
        }

        throw new IllegalMoveException(lan, ChessboardUtils.getFen(chessboard));
    }

    /**
     * Translate SAN string to LAN data
     *
     * @param chessboard chessboard
     * @param san SAN move
     * @return Translated Result
     *
     * @throws ConvertMoveException - if converting move failed
     */
    private static TranslateResult parseSan(Chessboard chessboard, String san) {
        int source_square = -1;
        int target_square = -1;
        int promotion_type = -1;

        int expected_file = -1;
        int expected_rank = -1;

        boolean whiteTurn = chessboard.side == white;

        boolean isCapture = san.contains("x");
        if(isCapture) san = san.replace("x", "");
        san = san.replace("+", "").replace("#", "");

        // when crazy house
        if (san.contains("@")) {
            String[] parts = san.split("@");
            char pieceChar = parts[0].charAt(0);
            target_square = BoardSquares.coordinates_to_square(parts[1]);

            int piece_type = ChessboardUtils.char_to_encoded_piece.get(pieceChar);
            if (chessboard.side == black && piece_type <= K) piece_type += 6;
            else if (chessboard.side == white && piece_type > K) piece_type -= 6;

            int move_result = MoveGenerator.isLegalDrop(chessboard, target_square, piece_type);

            if (move_result == ILLEGAL_MOVE) {
                throw new ConvertMoveException("Illegal drop move! ( FEN : " + ChessboardUtils.getFen(chessboard) + " )");
            }

            return new TranslateResult(parts[0] + "@" + parts[1], move_result);
        }

        if(san.equals("O-O") || san.equals("O-O-O") || san.equals("0-0") || san.equals("0-0-0")){
            boolean isKingSide = san.equals("O-O") || san.equals("0-0");

            int[] move_list = MoveCache.CONVERT_MOVE_CACHE.get();
            int move_count = MoveGenerator.generateMoves(chessboard, move_list);

            for (int count = 0; count < move_count; count++) {
                int move = move_list[count];

                if(!EncodeMove.getMoveCastling(move)) continue;

                int source = EncodeMove.getMoveSource(move);
                int target = EncodeMove.getMoveTarget(move);

                boolean isMoveKingSide = target > source;

                if(isKingSide == isMoveKingSide) return new TranslateResult(lanForCastling(chessboard, move), move);
            }

            throw new ConvertMoveException("There is no possible castling move! ( FEN : " +
                    ChessboardUtils.getFen(chessboard) + " )");
        }

        int piece_type = switch (san.charAt(0)) {
            case 'N' -> whiteTurn ? N : n;
            case 'B' -> whiteTurn ? B : b;
            case 'R' -> whiteTurn ? R : r;
            case 'Q' -> whiteTurn ? Q : q;
            case 'K' -> whiteTurn ? K : k;
            default -> whiteTurn ? P : p;
        };

        if(piece_type != P && piece_type != p) {
            // Qae7
            // Ne3c4

            // ae7
            // e3c4
            san = san.substring(1);

            if(san.length() == 3) {
                boolean isFile = Character.isAlphabetic(san.charAt(0));
                if(isFile) {
                    expected_file = san.charAt(0) - 'a';
                } else {
                    expected_rank = 7 - (san.charAt(0) - '1');
                }

                san = san.substring(1);
            } else if(san.length() == 4) {
                expected_file = san.charAt(0) - 'a';
                expected_rank = 7 - (san.charAt(1) - '1');

                san = san.substring(2);
            }

            target_square = BoardSquares.coordinates_to_square(san.substring(0,2));
        } else {
            // exd8=Q
            if (isCapture) {
                expected_file = san.charAt(0) - 'a';

                san = san.substring(1);
            }

            // d8=Q
            target_square = BoardSquares.coordinates_to_square(san.substring(0,2));
        }

        // promotion
        if(san.contains("=") && san.indexOf('=') + 1 < san.length())
            promotion_type = switch (san.charAt(san.indexOf('=') + 1)) {
                case 'Q', 'q' -> Q;
                case 'R', 'r' -> R;
                case 'B', 'b' -> B;
                case 'N', 'n' -> N;
                default -> throw new ConvertMoveException("Promotion piece char Not Found! ( FEN : " +
                        ChessboardUtils.getFen(chessboard) + " )");
            };

        int[] move_list = MoveCache.CONVERT_MOVE_CACHE.get();
        int move_count = MoveGenerator.generateMoves(chessboard, move_list);

        boolean result = false;
        int move_result = -1;

        for (int count = 0; count < move_count; count++) {
            int move = move_list[count];

            if (EncodeMove.getMovePiece(move) == piece_type &&
                    EncodeMove.getMoveTarget(move) == target_square) {
                int source = EncodeMove.getMoveSource(move);

                if(promotion_type != -1 && EncodeMove.getMovePromoted(move) != promotion_type) continue;
                if(expected_file != -1 && source % 8 != expected_file) continue;
                if(expected_rank != -1 && source / 8 != expected_rank) continue;

                if(result) throw new ConvertMoveException("Available move count is more than 1! ( FEN : " +
                        ChessboardUtils.getFen(chessboard) + " )");

                source_square = source;
                target_square = EncodeMove.getMoveTarget(move);

                result = true;
                move_result = move;
            }
        }

        if(move_result == -1) throw new ConvertMoveException("Available move count is zero! ( FEN : " +
                ChessboardUtils.getFen(chessboard) + " )");

        return new TranslateResult(BoardSquares.square_to_coordinates[source_square]
                + BoardSquares.square_to_coordinates[target_square]
                + (promotion_type != -1 ? ChessboardUtils.promotion_pieces[promotion_type] : ""), move_result);
    }

    /**
     * Get Lan for Castling move
     *
     * @param board chessboard
     * @param move encoded move (castling)
     * @return lan string
     */
    private static String lanForCastling(Chessboard board, int move) {
        int src = EncodeMove.getMoveSource(move);
        int tgt = EncodeMove.getMoveTarget(move);

        if (board.gameVariants == GameVariants.CHESS960) {
            return BoardSquares.square_to_coordinates[src] + BoardSquares.square_to_coordinates[tgt];
        }

        boolean isWhite = board.side == white;
        boolean isKingSide = isWhite ? (tgt == board.king_side_rook_file + 56) : (tgt == board.king_side_rook_file);

        if (isWhite) return isKingSide ? "e1g1" : "e1c1";
        else return isKingSide ? "e8g8" : "e8c8";
    }

    /**
     * Parse san to lan string
     *
     * @param chessboard chessboard
     * @param san san move
     * @return lan string
     *
     * @throws ConvertMoveException - if converting move failed
     */
    public static String toLanString(Chessboard chessboard, String san) {
        return parseSan(chessboard, san).moveString;
    }

    /**
     * Parse san to move data
     *
     * @param chessboard chessboard
     * @param san san move
     * @return move data
     *
     * @throws ConvertMoveException - if converting move failed
     */
    public static int sanToMoveData(Chessboard chessboard, String san) {
        return parseSan(chessboard, san).moveData;
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
        +(promotion_type!=0? ChessboardUtils.promotion_pieces[promotion_type] : ""), ChessboardUtils.getFen(chessboard));
    }
}

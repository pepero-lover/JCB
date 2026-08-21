package com.pepero.jcb.api.parse;

import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.exception.ConvertMoveException;
import com.pepero.jcb.api.exception.IllegalMoveException;
import com.pepero.jcb.api.exception.type.ConvertErrorType;
import com.pepero.jcb.constant.BoardSquares;
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
     * Get whether this position should show the # symbol (for san move converting)
     *
     * @param chessboard chessboard
     * @return whether this position should show the # symbol
     */
    public static boolean shouldShowCheckmateSymbol(Chessboard chessboard) {
        if (chessboard.gameVariants == GameVariants.ANTICHESS) {
            return ChessboardUtils.isAntiChessOver(chessboard);
        }
        if (chessboard.gameVariants == GameVariants.ATOMIC) {
            return ChessboardUtils.isAtomicOver(chessboard) || ChessboardUtils.isCheckmate(chessboard);
        }
        if (chessboard.gameVariants == GameVariants.HORDE) {
            return ChessboardUtils.isHordePiecesGone(chessboard) || ChessboardUtils.isCheckmate(chessboard);
        }
        if (chessboard.gameVariants == GameVariants.RACING_KINGS) {
            return ChessboardUtils.getGameResultForRacingKings(chessboard) != ChessboardUtils.ONGOING_VALUE;
        }
        return ChessboardUtils.isThreeCheck(chessboard) ||
                ChessboardUtils.isKingGoneToHill(chessboard) ||
                ChessboardUtils.isCheckmate(chessboard);
    }

    /**
     * Get this position should show the # or + symbol. <br>
     * if should append # or + symbol, add symbol.
     *
     * @param chessboard chessboard
     */
    private static void appendCheckOrMateSymbol(StringBuilder sb, Chessboard chessboard) {
        boolean isAntichessLike = chessboard.gameVariants == GameVariants.ANTICHESS
                || chessboard.gameVariants == GameVariants.RACING_KINGS;

        boolean inCheck = !isAntichessLike && ChessboardUtils.isCheck(chessboard);

        boolean isMate;
        if (chessboard.gameVariants == GameVariants.ANTICHESS) {
            isMate = ChessboardUtils.isAntiChessOver(chessboard);
        } else if (chessboard.gameVariants == GameVariants.ATOMIC) {
            isMate = ChessboardUtils.isAtomicOver(chessboard) || (inCheck && !ChessboardUtils.hasLegalMoves(chessboard));
        } else if (chessboard.gameVariants == GameVariants.HORDE) {
            isMate = ChessboardUtils.isHordePiecesGone(chessboard) || (inCheck && !ChessboardUtils.hasLegalMoves(chessboard));
        } else if (chessboard.gameVariants == GameVariants.RACING_KINGS) {
            isMate = ChessboardUtils.getGameResultForRacingKings(chessboard) != ChessboardUtils.ONGOING_VALUE;
        } else {
            isMate = ChessboardUtils.isThreeCheck(chessboard) ||
                    ChessboardUtils.isKingGoneToHill(chessboard) ||
                    (inCheck && !ChessboardUtils.hasLegalMoves(chessboard));
        }

        if (isMate) sb.append("#");
        else if (inCheck) sb.append("+");
    }

    /**
     * Convert lan move to san move
     * <p>
     * examples. <p>
     * e2e4 -> e4 (it depends, but if start position, this is right) <br>
     * g1f3 -> Nf3
     *
     * @param chessboard temp chessboard
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

            MoveGenerator.makeMove(chessboard, encoded_move);

            appendCheckOrMateSymbol(sb, chessboard);

            MoveGenerator.unmakeMove(chessboard, encoded_move);
            return new TranslateResult(sb.toString(), encoded_move);
        }

        // check the length
        if(lan.length() < 4 || lan.length() >= 6) {
            // the length is too short ( or too long )
            throw new ConvertMoveException("Length of the string is too short ( or too long )!"
                    , lan, chessboard, ConvertType.LAN, ConvertErrorType.LENGTH);
        }

        int source_square = BoardSquares.coordinates_to_square(lan.substring(0,2));
        int target_square = BoardSquares.coordinates_to_square(lan.substring(2,4));

        // lan is not correct
        if(source_square == -1 || target_square == -1) {
            throw new ConvertMoveException("Square string is not correct!", lan, chessboard,
                    ConvertType.LAN, ConvertErrorType.INCORRECT_SQUARE);
        }

        int type = ChessboardUtils.getPieceTypeOnSquare(chessboard, source_square);

        // if piece is not found
        if(type == -1) {
            throw new ConvertMoveException("Piece not found!", lan, chessboard,
                    ConvertType.LAN,
                    ConvertErrorType.PIECE_NOT_FOUND
                    );
        }

        StringBuilder sb = new StringBuilder();

        int[] move_list = MoveCache.CONVERT_MOVE_CACHE.get();
        int move_count = MoveGenerator.generateMoves(chessboard, move_list);

        int promotion_type = 0;
        if (lan.length() == 5) {
            promotion_type = char_to_encoded_piece.get(lan.charAt(4));
            if (chessboard.side == white) promotion_type -= 6;
        }

        int encoded_move = -1;
        for (int i = 0; i < move_count; i++) {
            int move = move_list[i];
            if (EncodeMove.getMoveSource(move) == source_square
                    && EncodeMove.getMoveTarget(move) == target_square
                    && EncodeMove.getMovePromoted(move) == promotion_type) {
                encoded_move = move;
                break;
            }
        }
        if (encoded_move == -1) {
            throw new IllegalMoveException(lan, ChessboardUtils.getFen(chessboard));
        }
        boolean castle = EncodeMove.getMoveCastling(encoded_move);

        if (castle) {
            boolean isKingSide = (source_square < target_square);

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
                    throw new ConvertMoveException("Promotion char not found!",
                            lan, ConvertType.LAN, ConvertErrorType.PROMOTION_CHARACTER);
                }
            }
        } else {
            if(!castle) {
                // add piece type
                sb.append(ascii_pieces[type % 6]);

                // add disambiguation
                boolean skipDisambiguation = (type == k || type == K) && chessboard.gameVariants != GameVariants.ANTICHESS;

                if (!skipDisambiguation) {
                    int going_piece_count = 0;
                    boolean equal_file = false;
                    boolean equal_rank = false;

                    for (int count = 0; count < move_count; count++) {
                        int move = move_list[count];

                        if (EncodeMove.getMovePiece(move) == type &&
                                EncodeMove.getMoveTarget(move) == target_square) {
                            going_piece_count++;
                            int other_src = EncodeMove.getMoveSource(move);

                            if (other_src != source_square) {
                                if (other_src % 8 == source_file) equal_file = true;
                                if (other_src / 8 == source_rank) equal_rank = true;
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

        MoveGenerator.makeMove(chessboard, encoded_move);

        appendCheckOrMateSymbol(sb, chessboard);

        MoveGenerator.unmakeMove(chessboard, encoded_move);

        return new TranslateResult(sb.toString(), encoded_move);
    }

    /**
     * Translate LAN string to SAN data
     *
     * @param chessboard temp chessboard
     * @param moveInfo move
     * @return Translated Result
     *
     * @throws IllegalMoveException - if move is illegal
     */
    private static TranslateResult parseLan(Chessboard chessboard, MoveInfo moveInfo) {
        int encoded_move = moveInfo.originEncodedData();
        return parseLan(chessboard, encoded_move);
    }

    /**
     * Translate LAN string to SAN data
     *
     * @param chessboard temp chessboard
     * @param encoded_move encoded move
     * @return Translated Result
     *
     * @throws IllegalMoveException - if move is illegal
     */
    private static TranslateResult parseLan(Chessboard chessboard, int encoded_move) {
        // when drop move (crazy house)
        if (EncodeMove.getMoveDrop(encoded_move)) {
            StringBuilder sb = new StringBuilder();
            sb.append(ascii_pieces[EncodeMove.getMovePiece(encoded_move) % 6]);
            sb.append("@");
            sb.append(BoardSquares.square_to_coordinates[EncodeMove.getMoveTarget(encoded_move)]);

            MoveGenerator.makeMove(chessboard, encoded_move);
            appendCheckOrMateSymbol(sb, chessboard);
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
            boolean isKingSide = (source_square < target_square);

            if (isKingSide) sb.append("O-O");
            else sb.append("O-O-O");
        }

        int source_file = source_square % 8;
        int source_rank = source_square / 8;

        int target_file = target_square % 8;

        boolean is_capture = ChessboardUtils.getPieceTypeOnSquare(chessboard, target_square) != -1
                || EncodeMove.getMoveEnpassant(encoded_move);

        String promotionStr = "";

        // if pawn moved
        if (type == p || type == P) {
            boolean is_pawn_capture = source_file != target_file;

            if (is_pawn_capture) {
                sb.append(BoardSquares.square_to_coordinates[source_square].charAt(0));
                sb.append("x");
            }

            if (EncodeMove.getMovePromoted(encoded_move) != 0) {
                promotionStr = "=" + ascii_pieces[
                                EncodeMove.getMovePromoted(encoded_move) % 6
                        ];
            }
        } else {
            if (!castle) {
                sb.append(ascii_pieces[type % 6]);

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
                            going_piece_count++;
                            int other_src = EncodeMove.getMoveSource(move);

                            if (other_src != source_square) {
                                if (other_src % 8 == source_file) equal_file = true;
                                if (other_src / 8 == source_rank) equal_rank = true;
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

        MoveGenerator.makeMove(chessboard, encoded_move);

        appendCheckOrMateSymbol(sb, chessboard);

        MoveGenerator.unmakeMove(chessboard, encoded_move);

        return new TranslateResult(sb.toString(), encoded_move);
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
     * @param chessboard temp chessboard
     * @param move move data
     * @return san move string
     *
     * @throws IllegalMoveException - if move is illegal
     */
    public static String toSanString(Chessboard chessboard, MoveInfo move) {
        return ConvertStringMoveUtils.parseLan(chessboard, move).moveString;
    }

    /**
     * Translate encoded move to san move string
     *
     * @param chessboard chess board
     * @param encodedMoves encoded moves
     * @return san move string
     *
     * @throws ConvertMoveException - if converting move failed
     * @throws IllegalMoveException - if move is illegal
     */
    public static String translateEncodedMoveToSan(Chessboard chessboard, int[] encodedMoves) {
        chessboard = new Chessboard(chessboard);

        StringBuilder sanSequence = new StringBuilder();

        for (int encoded_move : encodedMoves) {
            TranslateResult result = parseLan(chessboard, encoded_move);

            sanSequence.append(result.moveString).append(" ");

            MoveGenerator.makeMove(chessboard, result.moveData);
        }

        return sanSequence.toString();
    }

    /**
     * Lan sequence to San sequence
     *
     * @param chessboard temp chessboard
     * @param lanSequence lan string
     * @return translated san sequence
     *
     * @throws ConvertMoveException - if converting move failed
     * @throws IllegalMoveException - if move is illegal
     */
    public static String translateLanSequence(Chessboard chessboard, String lanSequence){
        chessboard = new Chessboard(chessboard);

        if(lanSequence.trim().isEmpty()) return "";

        String[] lans = lanSequence.trim().split("\\s+");
        StringBuilder sanSequence = new StringBuilder();

        for (String lan : lans) {
            TranslateResult result = parseLan(chessboard, lan);

            sanSequence.append(result.moveString).append(" ");

            MoveGenerator.makeMove(chessboard, result.moveData);
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
            if (parts.length != 2) throw new ConvertMoveException("Invalid drop format!", lan,
                    ConvertType.LAN, ConvertErrorType.DROP_MOVE);

            char pieceChar = parts[0].charAt(0);
            int target_square = BoardSquares.coordinates_to_square(parts[1]);
            if (target_square == -1) throw new ConvertMoveException("Invalid drop target square!", lan,
                    ConvertType.LAN, ConvertErrorType.INCORRECT_SQUARE);

            int piece_type = char_to_encoded_piece.get(pieceChar);
            if (chessboard.side == black && piece_type <= K) piece_type += 6;
            else if (chessboard.side == white && piece_type > K) piece_type -= 6;

            int isLegal = MoveGenerator.isLegalDrop(chessboard, target_square, piece_type);
            if (isLegal != ILLEGAL_MOVE) return isLegal;

            throw new IllegalMoveException(lan, ChessboardUtils.getFen(chessboard));
        }

        // check the length
        if(lan.length() < 4 || lan.length() >= 6) {
            // the length is too short ( or too long )
            throw new ConvertMoveException("Length of the string is too short ( or too long )!",
                    lan, chessboard, ConvertType.LAN, ConvertErrorType.LENGTH);
        }

        int source_square = BoardSquares.coordinates_to_square(lan.substring(0,2));
        int target_square = BoardSquares.coordinates_to_square(lan.substring(2,4));

        int promotion_type = 0;
        if(lan.length() == 5){
            promotion_type = char_to_encoded_piece.get(lan.charAt(4));
            if(chessboard.side == white) promotion_type -= 6;
        }

        if(!chessboard.isChess960) {
            int pieceType = ChessboardUtils.getPieceTypeOnSquare(chessboard, source_square);
            int targetType = ChessboardUtils.getPieceTypeOnSquare(chessboard, target_square);
            if(pieceType == K && targetType == R) {
                target_square = source_square < target_square ? BoardSquares.g1 : BoardSquares.c1;
            }
            if(pieceType == k && targetType == r) {
                target_square = source_square < target_square ? BoardSquares.g8 : BoardSquares.c8;
            }
        }

        // lan is not correct
        if(source_square == -1 || target_square == -1) {
            throw new ConvertMoveException("Square string is not correct!", lan,
                    ConvertType.LAN, ConvertErrorType.INCORRECT_SQUARE);
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
     * @throws IllegalMoveException - if illegal move
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

            int piece_type = char_to_encoded_piece.get(pieceChar);
            if (chessboard.side == black && piece_type <= K) piece_type += 6;
            else if (chessboard.side == white && piece_type > K) piece_type -= 6;

            int move_result = MoveGenerator.isLegalDrop(chessboard, target_square, piece_type);

            if (move_result == ILLEGAL_MOVE) {
                throw new IllegalMoveException(san, ChessboardUtils.getFen(chessboard));
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

                if(isKingSide == isMoveKingSide) return new TranslateResult(
                        BoardSquares.square_to_coordinates[source]
                                + BoardSquares.square_to_coordinates[target]
                        , move);
            }

            throw new IllegalMoveException(san, ChessboardUtils.getFen(chessboard));
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
                    expected_rank = san.charAt(0) - '1';
                }

                san = san.substring(1);
            } else if(san.length() == 4) {
                expected_file = san.charAt(0) - 'a';
                expected_rank = san.charAt(1) - '1';

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
                        ChessboardUtils.getFen(chessboard) + " )",
                        ConvertType.SAN,
                        ConvertErrorType.PROMOTION_CHARACTER);
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
                int promotion = EncodeMove.getMovePromoted(move);
                promotion -= whiteTurn ? 0 : 6;

                if(promotion_type != -1 && promotion != promotion_type) continue;
                if(expected_file != -1 && source % 8 != expected_file) continue;
                if(expected_rank != -1 && source / 8 != expected_rank) continue;

                if(result) throw new ConvertMoveException("Available move count is more than 1! ( FEN : " +
                        ChessboardUtils.getFen(chessboard) + " )",
                        ConvertType.SAN,
                        ConvertErrorType.AMBIGUITY_COULD_NOT_BE_RESOLVED);

                source_square = source;
                target_square = EncodeMove.getMoveTarget(move);

                result = true;
                move_result = move;
            }
        }

        if(move_result == -1) throw new IllegalMoveException(san, ChessboardUtils.getFen(chessboard));

        return new TranslateResult(BoardSquares.square_to_coordinates[source_square]
                + BoardSquares.square_to_coordinates[target_square]
                + (promotion_type != -1 ? promotion_pieces[promotion_type] : ""), move_result);
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
     * Translate san sequence to lan sequence
     *
     * @param chessboard chess board
     * @param sanSequence san string (like e4 e5 Nf3)
     * @return translated lan sequence
     *
     * @throws ConvertMoveException - if converting move failed
     * @throws IllegalMoveException - if move is illegal
     */
    public static String translateSanSequence(Chessboard chessboard, String sanSequence) {
        chessboard = new Chessboard(chessboard);

        if(sanSequence.trim().isEmpty()) return "";

        String[] sans = sanSequence.trim().split("\\s+");
        StringBuilder lanSequence = new StringBuilder();

        for (String lan : sans) {
            TranslateResult result = parseSan(chessboard, lan);

            lanSequence.append(result.moveString).append(" ");

            MoveGenerator.makeMove(chessboard, result.moveData);
        }

        return lanSequence.toString();
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
     * @throws IllegalMoveException - if illegal move
     */
    public static int parseMoveDataToEncodedMove(Chessboard chessboard, int source_square, int target_square,
                                                 int promotion_type){
        if(promotion_type == -1) promotion_type = 0;

        // source square is not correct
        if(source_square < 0 || source_square >= 64) {
            throw new ConvertMoveException("Source square is not correct!",
                    ConvertType.MANUAL,
                    ConvertErrorType.INCORRECT_SQUARE);
        }

        // target square is not correct
        if(target_square < 0 || target_square >= 64) {
            throw new ConvertMoveException("Target square is not correct!",
                    ConvertType.MANUAL,
                    ConvertErrorType.INCORRECT_SQUARE);
        }

        if(promotion_type < 0 || promotion_type > 11) {
            throw new ConvertMoveException("Promotion Type is not correct!",
                    ConvertType.MANUAL,
                    ConvertErrorType.PROMOTION_CHARACTER);
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
        +(promotion_type!=0? promotion_pieces[promotion_type] : ""), ChessboardUtils.getFen(chessboard));
    }
}

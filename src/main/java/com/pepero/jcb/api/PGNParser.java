package com.pepero.jcb.api;

import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.api.enums.GameResult;
import com.pepero.jcb.api.exception.game.IllegalMoveException;
import com.pepero.jcb.api.exception.pgn.NodesOverflowException;
import com.pepero.jcb.api.exception.pgn.PGNConvertException;
import com.pepero.jcb.api.exception.convert.ConvertMoveException;
import com.pepero.jcb.api.exception.type.PGNErrorType;
import com.pepero.jcb.api.parse.ConvertStringMoveUtils;
import com.pepero.jcb.api.util.LongObjectOpenHashMap;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.ChessboardUtils;
import com.pepero.jcb.core.GameVariant;
import com.pepero.jcb.core.MoveGenerator;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Parse PGN string data and return {@link PGNParsedData} for initializing {@link ChessGame} class.
 */
class PGNParser {

    /**
     * Parse "Variant" section on PGN header to GameVariant enum
     *
     * @param variantValue "Variant" section on PGN header
     * @return GameVariant enum
     */
    public static GameVariant parseVariantHeader(String variantValue) {
        if (variantValue == null) return GameVariant.STANDARD;

        return switch (variantValue.trim().toLowerCase()) {
            case "crazyhouse" -> GameVariant.CRAZY_HOUSE;
            case "three-check", "threecheck", "3-check", "3check" -> GameVariant.THREE_CHECK;
            case "king of the hill", "kingofthehill", "koth" -> GameVariant.KING_OF_THE_HILL;
            case "horde", "hord", "hd" -> GameVariant.HORDE;
            case "racing kings", "racing king", "racingkings", "racingking"
            ,"king race", "kingrace", "kr" -> GameVariant.RACING_KINGS;
            case "antichess", "anti chess", "ac", "anti", "giveaway", "losing chess",
                 "losingchess" -> GameVariant.GIVEAWAY;
            case "suicide chess", "suicidechess", "suicide" -> GameVariant.SUICIDE;
            case "atomic", "atomic chess", "atom", "at", "nuclear", "nuclear chess",
                 "explosion chess", "bomb chess" -> GameVariant.ATOMIC;
            default -> GameVariant.STANDARD;
        };
    }

    /**
     * Parse pgn string to {@link PGNParsedData} DTO
     *
     * @param pgnString pgn string
     * @param maxNodesCount max nodes calculating count
     * @return {@link PGNParsedData} DTO (for initializing ChessGame)
     *
     * @throws PGNConvertException when pgn converting failed
     */
    public static PGNParsedData parse(String pgnString, int maxNodesCount, AtomicLong nodeCounter) {
        if (pgnString == null || pgnString.isEmpty()) {
            throw new PGNConvertException("PGN string is empty", PGNErrorType.PGN_EMPTY);
        }
        pgnString = pgnString.replace("\uFEFF", "");

        Map<String, String> parsedHeaders = new HashMap<>();

        int len = pgnString.length();
        int pos = 0;
        int moveTextStart = len;

        while (pos < len) {
            int lineStart = pos;
            int lineEnd = pos;

            while (lineEnd < len) {
                char c = pgnString.charAt(lineEnd);
                if (c == '\n' || c == '\r') break;
                lineEnd++;
            }

            int nextPos = lineEnd;
            if (nextPos < len) {
                char c = pgnString.charAt(nextPos);
                nextPos++;
                if (c == '\r' && nextPos < len && pgnString.charAt(nextPos) == '\n') {
                    nextPos++;
                }
            }

            int trimStart = lineStart;
            int trimEnd = lineEnd;
            while (trimStart < trimEnd && Character.isWhitespace(pgnString.charAt(trimStart))) trimStart++;
            while (trimEnd > trimStart && Character.isWhitespace(pgnString.charAt(trimEnd - 1))) trimEnd--;

            if (trimStart == trimEnd) {
                pos = nextPos;
                continue;
            }

            if (pgnString.charAt(trimStart) == '[') {
                if (trimEnd - trimStart < 2 || pgnString.charAt(trimEnd - 1) != ']') {
                    throw new PGNConvertException("Malformed PGN header line: " + pgnString.substring(trimStart, trimEnd),
                            PGNErrorType.WRONG_HEADER, pgnString.substring(trimStart, trimEnd));
                }
                String headerInner = pgnString.substring(trimStart + 1, trimEnd - 1);
                int spaceIdx = headerInner.indexOf(' ');
                if (spaceIdx == -1) {
                    throw new PGNConvertException("Malformed PGN header line (missing space between key and value): "
                            + pgnString.substring(trimStart, trimEnd), PGNErrorType.WRONG_HEADER,
                            pgnString.substring(trimStart, trimEnd));
                }
                String type = headerInner.substring(0, spaceIdx);
                String rawValue = headerInner.substring(spaceIdx + 1);
                if (rawValue.length() < 2 || rawValue.charAt(0) != '"' || rawValue.charAt(rawValue.length() - 1) != '"') {
                    throw new PGNConvertException("Malformed PGN header value (must be quoted): "
                            + pgnString.substring(trimStart, trimEnd), PGNErrorType.WRONG_HEADER,
                            pgnString.substring(trimStart, trimEnd));
                }
                String what = rawValue.substring(1, rawValue.length() - 1);
                parsedHeaders.put(type, what);
                pos = nextPos;
            } else {
                moveTextStart = lineStart;
                break;
            }
        }

        String movePGNString = (moveTextStart < len) ? pgnString.substring(moveTextStart) : "";

        record VariationState(MoveNode node, Chessboard snapshotBoard) {}
        Stack<VariationState> variationStack = new Stack<>();

        Chessboard pgnChessboard;
        GameVariant parsedVariant = parseVariantHeader(parsedHeaders.get("Variant"));
        boolean isChess960 = false;
        if(parsedHeaders.containsKey("Variant")) {
            switch (parsedHeaders.get("Variant").trim().toLowerCase()) {
                case "chess960", "fischerandom", "fischerrandom" -> isChess960 = true;
            }
        }

        String parsedFen;

        if ("1".equals(parsedHeaders.get("SetUp")) && parsedHeaders.containsKey("FEN")) {
            parsedFen = parsedHeaders.get("FEN");
        } else {
            parsedFen = ChessboardUtils.getDefaultStartPosition(parsedVariant);
        }

        pgnChessboard = new Chessboard(parsedFen, isChess960, parsedVariant);

        MoveNode rootNode = new MoveNode(nodeCounter.getAndIncrement(), pgnChessboard.full_move);
        MoveNode currentParsedNode = rootNode;
        LongObjectOpenHashMap<MoveNode> tempNodeCache = new LongObjectOpenHashMap<>();
        tempNodeCache.put(rootNode.id, rootNode);

        GameResult parsedGameResult = GameResult.UNKNOWN;

        PGNLexer lexer = new PGNLexer(movePGNString);
        PGNToken currentToken;

        while ((currentToken = lexer.nextToken()).type() != TokenType.EOF) {
            switch (currentToken.type()) {
                case COMMENT:
                    String rawComment = currentToken.value().trim();
                    String remainingComment = extractAnnotations(rawComment, currentParsedNode.getAnnotation());

                    if (!remainingComment.isEmpty()) {
                        currentParsedNode.getAnnotation().comment = (currentParsedNode.getAnnotation().comment == null)
                                ? remainingComment : currentParsedNode.getAnnotation().comment + " " + remainingComment;
                    }
                    break;

                case NAG:
                    String tokenNag = currentToken.value();
                    currentParsedNode.getAnnotation().nag =
                            (currentParsedNode.getAnnotation().nag == null || currentParsedNode.getAnnotation().nag.isEmpty())
                                    ? tokenNag : currentParsedNode.getAnnotation().nag + " " + tokenNag;
                    break;

                case VARIATION_START:
                    variationStack.push(new VariationState(currentParsedNode, new Chessboard(pgnChessboard)));
                    if (currentParsedNode.moveData != null) {
                        MoveGenerator.unmakeMove(pgnChessboard, currentParsedNode.moveData.originEncodedData());
                        currentParsedNode = currentParsedNode.parent;
                    }
                    break;

                case VARIATION_END:
                    if (!variationStack.isEmpty()) {
                        VariationState state = variationStack.pop();
                        currentParsedNode = state.node;
                        pgnChessboard = state.snapshotBoard;
                    } else {
                        throw new PGNConvertException("There is no move node's parent existing, but the variation ended!",
                                PGNErrorType.VARIATION_STACK_EMPTY_POP);
                    }
                    break;

                case RESULT:
                    if (currentToken.value().equals("1-0")) parsedGameResult = GameResult.WHITE_WON;
                    if (currentToken.value().equals("0-1")) parsedGameResult = GameResult.BLACK_WON;
                    if (currentToken.value().equals("1/2-1/2")) parsedGameResult = GameResult.DRAW;
                    break;

                case MOVE:
                    String rawSan = currentToken.value();
                    int cleanEnd = rawSan.length();
                    while (cleanEnd > 0) {
                        char lastChar = rawSan.charAt(cleanEnd - 1);
                        if (lastChar == '!' || lastChar == '?') cleanEnd--;
                        else break;
                    }

                    String pureSan = rawSan.substring(0, cleanEnd);
                    String annotation = rawSan.substring(cleanEnd);

                    int moveData;
                    try {
                        moveData = ConvertStringMoveUtils.sanToMoveData(pgnChessboard, pureSan);
                    } catch (IllegalMoveException e) {
                        throw e.withPgnParsePosition(
                                variationStack.isEmpty() ? List.of() : variationStack.peek().node().pathFromRoot(),
                                pgnChessboard.full_move, pgnChessboard.ply);
                    } catch (ConvertMoveException e) {
                        throw e.withPgnParsePosition(
                                variationStack.isEmpty() ? List.of() : variationStack.peek().node().pathFromRoot(),
                                pgnChessboard.full_move, pgnChessboard.ply);
                    }
                    MoveGenerator.makeMove(pgnChessboard, moveData);

                    MoveInfo moveInfo = new MoveInfo(moveData);
                    MoveNode newNode = new MoveNode(moveInfo, currentParsedNode, nodeCounter.getAndIncrement(),
                            pgnChessboard.ply, pgnChessboard.full_move);

                    if (!annotation.isEmpty()) {
                        String parsedNag = switch (annotation) {
                            case "!" -> "$1"; case "?" -> "$2"; case "!!" -> "$3";
                            case "??" -> "$4"; case "!?" -> "$5"; case "?!" -> "$6";
                            default -> "";
                        };
                        if (!parsedNag.isEmpty()) {
                            newNode.getAnnotation().nag =
                                    (newNode.getAnnotation().nag == null || newNode.getAnnotation().nag.isEmpty())
                                            ? parsedNag : newNode.getAnnotation().nag + " " + parsedNag;
                        }
                    }

                    currentParsedNode.children.add(newNode);
                    currentParsedNode = newNode;
                    tempNodeCache.put(newNode.id, newNode);
                    if(tempNodeCache.size() >= maxNodesCount) {
                        throw new NodesOverflowException(maxNodesCount);
                    }
                    break;
            }
        }

        if (!variationStack.isEmpty()) {
            throw new PGNConvertException(
                    "PGN ended with " + variationStack.size() + " unclosed variation(s)",
                    PGNErrorType.VARIATION_STACK_NOT_EMPTY
            );
        }

        if (parsedGameResult == GameResult.UNKNOWN) {
            String headerResult = parsedHeaders.get("Result");
            if ("1-0".equals(headerResult)) parsedGameResult = GameResult.WHITE_WON;
            else if ("0-1".equals(headerResult)) parsedGameResult = GameResult.BLACK_WON;
            else if ("1/2-1/2".equals(headerResult)) parsedGameResult = GameResult.DRAW;
        }

        GameOverReason parsedGameOverReason = GameOverReason.NOTGAMEOVER;

        if (parsedGameResult != GameResult.UNKNOWN) {
            MoveNode lastNode = rootNode.getLastMainlineNode();
            lastNode.terminalResult = parsedGameResult;

            if (ChessboardUtils.isCheckmate(pgnChessboard)) {
                parsedGameOverReason = GameOverReason.CHECKMATE;
            } else if (ChessboardUtils.isStaleMate(pgnChessboard)) {
                parsedGameOverReason = GameOverReason.STALEMATE;
            } else {
                parsedGameOverReason = (parsedGameResult == GameResult.DRAW) ?
                        GameOverReason.AGREEMENT_DRAW : GameOverReason.RESIGNATION;
            }

            lastNode.terminalReason = parsedGameOverReason;
        }

        return new PGNParsedData(
                parsedFen, parsedVariant, isChess960,
                rootNode, tempNodeCache, parsedHeaders, parsedGameResult,parsedGameOverReason
        );
    }

    /**
     * Extracts {@code [%tag value]} style annotations (clk, timestamp, eval, csl, cal) and stores into given
     * {@code annotation}, and return the annotation removed comment
     */
    private static String extractAnnotations(String comment, MoveAnnotation annotation) {
        int len = comment.length();
        StringBuilder remaining = new StringBuilder(len);
        int i = 0;

        while (i < len) {
            if (comment.charAt(i) == '[' && i + 1 < len && comment.charAt(i + 1) == '%') {
                int tagStart = i + 2;
                int tagEnd = tagStart;
                while (tagEnd < len && comment.charAt(tagEnd) != ' ' && comment.charAt(tagEnd) != ']') tagEnd++;

                int valStart = tagEnd;
                while (valStart < len && comment.charAt(valStart) == ' ') valStart++;

                int valEnd = comment.indexOf(']', valStart);
                if (valEnd != -1) {
                    String tag = comment.substring(tagStart, tagEnd);
                    String value = comment.substring(valStart, valEnd);

                    switch (tag) {
                        case "clk" -> annotation.clk = value;
                        case "timestamp" -> annotation.timeStamp = value;
                        case "eval" -> annotation.eval = value;
                        case "csl" -> annotation.csl = value;
                        case "cal" -> annotation.cal = value;
                        default -> remaining.append(comment, i, valEnd + 1);
                    }

                    i = valEnd + 1;
                    continue;
                }
            }
            remaining.append(comment.charAt(i));
            i++;
        }

        return remaining.toString().trim();
    }
}
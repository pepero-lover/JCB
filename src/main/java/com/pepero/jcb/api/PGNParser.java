package com.pepero.jcb.api;

import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.dto.PGNToken;
import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.api.enums.GameResult;
import com.pepero.jcb.api.exception.NodesOverflowException;
import com.pepero.jcb.api.exception.PGNConvertException;
import com.pepero.jcb.api.parse.ConvertStringMoveUtils;
import com.pepero.jcb.api.parse.pgn.PGNLexer;
import com.pepero.jcb.api.parse.pgn.TokenType;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.ChessboardUtils;
import com.pepero.jcb.core.GameVariant;
import com.pepero.jcb.core.MoveGenerator;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class PGNParser {

    // for pgn parsing pattern
    private static final Pattern CLK_PATTERN = Pattern.compile("\\[%clk\\s+([^\\]]+)\\]");
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("\\[%timestamp\\s+([^\\]]+)\\]");
    private static final Pattern EVAL_PATTERN = Pattern.compile("\\[%eval\\s+([^\\]]+)\\]");
    private static final Pattern CSL_PATTERN = Pattern.compile("\\[%csl\\s+([^\\]]+)\\]");
    private static final Pattern CAL_PATTERN = Pattern.compile("\\[%cal\\s+([^\\]]+)\\]");

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
                 "losingchess", "suicide chess", "suicidechess" -> GameVariant.ANTICHESS;
            case "atomic", "atomic chess", "atom", "at", "nuclear", "nuclear chess",
                 "explosion chess", "bomb chess" -> GameVariant.ATOMIC;
            default -> GameVariant.STANDARD;
        };
    }

    /**
     * Get default start position
     *
     * @param gameVariant game variant
     * @return default start position
     */
    public static String getDefaultStartPosition(GameVariant gameVariant) {
        return switch (gameVariant) {
            case HORDE -> Chessboard.horde_start_position;
            case RACING_KINGS -> Chessboard.racing_kings_start_position;
            case ANTICHESS -> Chessboard.antichess_start_position;
            default -> Chessboard.start_position;
        };
    }

    /**
     * Parse pgn string to PGNParsedData DTO
     *
     * @param pgnString pgn string
     * @param maxNodesCount max nodes calculating count
     * @return PGNParsedData DTO (for initializing ChessGame
     */
    public static PGNParsedData parse(String pgnString, int maxNodesCount) {
        long nodeCounter = 0;

        if (pgnString == null || pgnString.isEmpty()) {
            throw new IllegalArgumentException("PGN string is empty");
        }
        pgnString = pgnString.replace("\uFEFF", "");

        Map<String, String> parsedHeaders = new HashMap<>();
        String[] lines = pgnString.split("\\R");
        int line_stopped = -1;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("[")) {
                line = line.substring(1, line.length() - 1);
                String[] parts = line.split(" ", 2);
                if (parts.length == 2) {
                    String type = parts[0];
                    String what = parts[1].replace("\"", "");
                    parsedHeaders.put(type, what);
                }
            } else {
                line_stopped = i;
                break;
            }
        }

        String movePGNString = "";
        if (line_stopped != -1) {
            StringBuilder moveBuilder = new StringBuilder();
            for (int i = line_stopped; i < lines.length; i++) {
                moveBuilder.append(lines[i]).append("\n");
            }
            movePGNString = moveBuilder.toString();
        }

        MoveNode rootNode = new MoveNode(nodeCounter++);
        MoveNode currentParsedNode = rootNode;
        Map<Long, MoveNode> tempNodeCache = new HashMap<>();
        tempNodeCache.put(rootNode.id, rootNode);

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
            parsedFen = getDefaultStartPosition(parsedVariant);
        }

        pgnChessboard = new Chessboard(parsedFen, isChess960, parsedVariant);

        GameResult parsedGameResult = GameResult.UNKNOWN;

        PGNLexer lexer = new PGNLexer(movePGNString);
        PGNToken currentToken;

        while ((currentToken = lexer.nextToken()).type() != TokenType.EOF) {
            switch (currentToken.type()) {
                case COMMENT:
                    String rawComment = currentToken.value().trim();

                    Matcher clkMatcher = CLK_PATTERN.matcher(rawComment);
                    if (clkMatcher.find()) {
                        currentParsedNode.getAnnotation().clk = clkMatcher.group(1);
                        rawComment = clkMatcher.replaceAll("").trim();
                    }

                    Matcher timestampMatcher = TIMESTAMP_PATTERN.matcher(rawComment);
                    if (timestampMatcher.find()) {
                        currentParsedNode.getAnnotation().timeStamp = timestampMatcher.group(1);
                        rawComment = timestampMatcher.replaceAll("").trim();
                    }

                    Matcher evalMatcher = EVAL_PATTERN.matcher(rawComment);
                    if (evalMatcher.find()) {
                        currentParsedNode.getAnnotation().eval = evalMatcher.group(1);
                        rawComment = evalMatcher.replaceAll("").trim();
                    }

                    Matcher cslMatcher = CSL_PATTERN.matcher(rawComment);
                    if (cslMatcher.find()) {
                        currentParsedNode.getAnnotation().csl = cslMatcher.group(1);
                        rawComment = cslMatcher.replaceAll("").trim();
                    }

                    Matcher calMatcher = CAL_PATTERN.matcher(rawComment);
                    if (calMatcher.find()) {
                        currentParsedNode.getAnnotation().cal = calMatcher.group(1);
                        rawComment = calMatcher.replaceAll("").trim();
                    }

                    if (!rawComment.isEmpty()) {
                        currentParsedNode.getAnnotation().comment = (currentParsedNode.getAnnotation().comment == null)
                                ? rawComment : currentParsedNode.getAnnotation().comment + " " + rawComment;
                    }
                    break;

                case NAG:
                    currentParsedNode.getAnnotation().nag = currentToken.value();
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
                        throw new PGNConvertException("Variation stack is empty!");
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

                    int moveData = ConvertStringMoveUtils.sanToMoveData(pgnChessboard, pureSan);
                    MoveGenerator.makeMove(pgnChessboard, moveData);

                    MoveInfo moveInfo = new MoveInfo(moveData);
                    MoveNode newNode = new MoveNode(moveInfo, currentParsedNode, nodeCounter++);
                    newNode.san = pureSan;

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
                        throw new NodesOverflowException(
                                "This pgn's node (move) count is more than max nodes count! (Max node count : " + maxNodesCount + ")"
                        );
                    }
                    break;
            }
        }

        if (parsedGameResult != GameResult.UNKNOWN) {
            MoveNode lastNode = rootNode.getLastMainlineNode();
            lastNode.terminalResult = parsedGameResult;

            if (ChessboardUtils.isCheckmate(pgnChessboard)) {
                lastNode.terminalReason = GameOverReason.CHECKMATE;
            } else if (ChessboardUtils.isStaleMate(pgnChessboard)) {
                lastNode.terminalReason = GameOverReason.STALEMATE;
            } else {
                lastNode.terminalReason = (parsedGameResult == GameResult.DRAW) ?
                        GameOverReason.AGREEMENTDRAW : GameOverReason.RESIGNATION;
            }
        }

        return new PGNParsedData(
                parsedFen, parsedVariant, isChess960,
                rootNode, tempNodeCache, parsedHeaders, parsedGameResult
        );
    }
}
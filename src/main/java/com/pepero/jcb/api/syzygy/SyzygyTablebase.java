package com.pepero.jcb.api.syzygy;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.SyzygyAnalyzer;
import com.pepero.jcb.api.exception.SyzygyUnsupportedMaterialException;
import com.pepero.jcb.core.bitboard.BitBoardUtils;
import com.pepero.jcb.core.constant.MoveCache;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.ChessboardUtils;
import com.pepero.jcb.core.GameVariant;
import com.pepero.jcb.core.MoveGenerator;
import com.pepero.jcb.core.encode.EncodeMove;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.pepero.jcb.core.constant.SideToMove.*;
import static com.pepero.jcb.core.constant.BoardSquares.*;
import static com.pepero.jcb.core.constant.EncodedPieces.*;

/**
 * Probe and get the DTZ (Distance to zero) / WDL (Win Draw Loss) data.
 * This uses {@link Chessboard} to probe the data, if you want to probe the data with {@link ChessGame},
 * go to {@link SyzygyAnalyzer}.
 */
public class SyzygyTablebase {

    private final Path syzygyDir;

    // one entry per distinct material string ever probed (e.g. "KBNvK", "KQvKR")
    private final Map<String, WdlTable> wdlCache = new ConcurrentHashMap<>();
    private final Map<String, DtzTable> dtzCache = new ConcurrentHashMap<>();

    private static final int DEFAULT_MAX_PIECES = 7;

    private final int maxPieces;

    private final GameVariant variant;
    private final boolean connectedKingsEnc;

    public SyzygyTablebase(Path syzygyDir) {
        this(syzygyDir, DEFAULT_MAX_PIECES, GameVariant.STANDARD);
    }

    public SyzygyTablebase(Path syzygyDir, int maxPieces) {
        this(syzygyDir, maxPieces, GameVariant.STANDARD);
    }

    public SyzygyTablebase(Path syzygyDir, GameVariant variant) {
        this(syzygyDir, DEFAULT_MAX_PIECES, variant);
    }

    public SyzygyTablebase(Path syzygyDir, int maxPieces, GameVariant variant) {
        this.syzygyDir = syzygyDir;
        this.maxPieces = maxPieces;
        this.variant = variant;
        this.connectedKingsEnc = (
                variant == GameVariant.ATOMIC ||
                        variant == GameVariant.GIVEAWAY ||
                        variant == GameVariant.SUICIDE
        );
    }

    private String wdlExtFor(String materialName) {
        boolean hasPawns = materialName.indexOf('P') >= 0;
        return switch (variant) {
            case ATOMIC -> ".atbw";
            case GIVEAWAY -> hasPawns ? ".gtbw" : ".stbw";
            case SUICIDE -> ".stbw";
            default -> ".rtbw";
        };
    }

    private String dtzExtFor(String materialName) {
        boolean hasPawns = materialName.indexOf('P') >= 0;
        return switch (variant) {
            case ATOMIC -> ".atbz";
            case GIVEAWAY -> hasPawns ? ".gtbz" : ".stbz";
            case SUICIDE -> ".stbz";
            default -> ".rtbz";
        };
    }

    private record WdlTable(
            SyzygyMaterial material,
            MappedByteBuffer header,
            SyzygySubTable[] subTables,
            SyzygyPairsHeader[][] pairsHeaders,
            SyzygyBlockLayout layout,
            SyzygyEncType encType,
            int sides,
            boolean colorFlipped
    ) {}

    private record DtzTable(
            SyzygyMaterial material,
            MappedByteBuffer header,
            SyzygySubTable[] subTables,
            SyzygyPairsHeader[][] pairsHeaders,
            SyzygyBlockLayout layout,
            SyzygyDtzMapEntry[] dtzMapPerTable,
            SyzygyEncType encType,
            boolean colorFlipped,
            // true for materials whose piece composition is identical for both
            // colors (e.g. KRvKR, KNNvKNN) — Fathom's `be->symmetric` (key == key2).
            // For these, the DTZ file's stored side never needs to match the
            // board's actual side to move; see tryDirectDtz().
            boolean symmetric
    ) {}

    private int probeWdl(Chessboard board) throws IOException {
        int[] moveArray = new int[MoveCache.MAX_MOVE_SIZE];
        int moveCount = MoveGenerator.generateMoves(board, moveArray);

        if (moveCount == 0) {
            if (variant == GameVariant.GIVEAWAY) {
                return 4;
            }
            if (variant == GameVariant.SUICIDE) {
                int myPieces = BitBoardUtils.countBits(board.occupancies[board.side]);
                int oppPieces = BitBoardUtils.countBits(board.occupancies[board.side ^ 1]);
                if (myPieces < oppPieces) return 4;
                if (myPieces == oppPieces) return 2;
                return 0;
            }

            boolean inCheck = ChessboardUtils.isCheck(board);
            return inCheck ? 0 : 2;
        }

        int bestWdl = 0;
        boolean hasCapture = false;

        for (int i = 0; i < moveCount; i++) {
            int move = moveArray[i];

            boolean isCapture = EncodeMove.getMoveCapture(move);
            if (isCapture) {
                hasCapture = true;
            }

            int turn = (EncodeMove.getMovePiece(move) == P) ? white : black;
            int piece = EncodeMove.getMovePiece(move);
            boolean isPromotion = (piece == P || piece == p) &&
                    ((turn == white && EncodeMove.getMoveTarget(move) >= a8)
                            || (turn == black && EncodeMove.getMoveTarget(move) <= h1));

            if (isCapture || isPromotion) {
                Chessboard child = new Chessboard(board);
                MoveGenerator.makeMove(child, move);

                int childWdl = probeWdl(child);
                int ourWdl = 4 - childWdl;

                if (ourWdl > bestWdl) {
                    bestWdl = ourWdl;
                    if (bestWdl == 4) {
                        return 4;
                    }
                }
            }
        }

        if (hasCapture && (variant == GameVariant.SUICIDE || variant == GameVariant.GIVEAWAY)) {
            return bestWdl;
        }

        int tableWdl = probeWdlTable(board);

        return Math.max(bestWdl, tableWdl);
    }

    private int probeWdlTable(Chessboard board) throws IOException {
        int boardPiece = BitBoardUtils.countBits(board.occupancies[both]);
        if (variant != GameVariant.GIVEAWAY && variant != GameVariant.SUICIDE && boardPiece == 2) return 2;
        if(boardPiece <= 1) return 2;

        if(boardPiece > maxPieces)
            throw new SyzygyUnsupportedMaterialException(
                    "This Syzygy tablebase's supporting piece count is less than this board's piece count! " +
                            "(supporting : " + maxPieces +
                            ", chess board : " + boardPiece + ")"
            );

        String materialName = buildMaterialString(board);
        WdlTable table = wdlCache.computeIfAbsent(materialName, this::loadWdlTable);

        SyzygyMaterial material = table.material();

        boolean actualWtm = (board.side == white);
        boolean mirrorFlip = table.colorFlipped();
        boolean isWtm = mirrorFlip != actualWtm;

        boolean noSplitReuse = (table.sides() == 1) && !isWtm;
        if (noSplitReuse) {
            isWtm = true;
        }
        boolean effectiveColorFlip = mirrorFlip ^ noSplitReuse;

        FileClassResult fc = determineFileClass(board, material, effectiveColorFlip);
        int t = fc.fileClass();

        int side = isWtm ? 0 : 1;
        SyzygyPairsHeader ph = table.pairsHeaders()[t][side];
        if (ph.isConstant()) {
            return ph.constValue();
        }

        SyzygyEncInfo encInfo = SyzygyEncInfo.build(table.subTables()[t], isWtm, material, t, table.encType());
        int[] p = SyzygyFillSquares.fillSquares(board, table.subTables()[t], isWtm, effectiveColorFlip, fc.anchorSquare());
        long idx = SyzygyEncoder.encode(p, encInfo, material, table.encType());

        // NOTE: naive t*sides+side arithmetic breaks the moment ANY earlier (t,s) entry
        // was constant (it shifts every later entries[] index down), so this MUST go
        // through getEntryIndex() rather than being computed directly.
        int flatIndex = table.layout().getEntryIndex(t, side);
        SyzygyHuffmanTable huffman = ph.huffmanTable();
        SyzygyBlockLayout.Entry entry = table.layout().getEntries()[flatIndex];

        return SyzygyDecompressor.decompressPairs(table.header(), entry, huffman, idx);
    }

    /**
     * Probe the DTZ result on this board
     *
     * @param board chess board
     * @return dtz result
     * */
    private int probeDtz(Chessboard board) throws IOException {
        int boardPiece = BitBoardUtils.countBits(board.occupancies[both]);
        if (variant != GameVariant.GIVEAWAY && variant != GameVariant.SUICIDE && boardPiece == 2) return 0;
        if(boardPiece <= 1) return 0;

        if (variant == GameVariant.GIVEAWAY || variant == GameVariant.SUICIDE) {
            if(!ChessboardUtils.hasLegalMoves(board)) return 0;
        }

        int wdlResult = probeWdl(board);
        if (wdlResult == 2) return 0;

        if (wdlResult > 2) {
            int requiredChildWdl = 4 - wdlResult;
            int[] moveArray = new int[MoveCache.MAX_MOVE_SIZE];
            int moveCount = MoveGenerator.generateMoves(board, moveArray);

            for (int i = 0; i < moveCount; i++) {
                int move = moveArray[i];
                boolean zeroing = EncodeMove.getMoveCapture(move)
                        || EncodeMove.getMovePiece(move) == P
                        || EncodeMove.getMovePiece(move) == p;

                Chessboard child = new Chessboard(board);
                MoveGenerator.makeMove(child, move);
                boolean opponentStuck = (variant == GameVariant.SUICIDE || variant == GameVariant.GIVEAWAY)
                        && !ChessboardUtils.hasLegalMoves(child);

                if (zeroing || opponentStuck) {
                    int childWdl = probeWdl(child);
                    if (childWdl == requiredChildWdl) {
                        return (wdlResult == 3) ? 101 : 1;
                    }
                }
                else if (variant == GameVariant.SUICIDE || variant == GameVariant.GIVEAWAY) {
                    int[] responseMoves = new int[MoveCache.MAX_MOVE_SIZE];
                    int responseCount = MoveGenerator.generateMoves(child, responseMoves);

                    boolean opponentHasCapture = false;
                    boolean allResponsesLoseForOpponent = true;

                    for (int j = 0; j < responseCount; j++) {
                        int rMove = responseMoves[j];
                        if (EncodeMove.getMoveCapture(rMove)) {
                            opponentHasCapture = true;

                            Chessboard grandchild = new Chessboard(child);
                            MoveGenerator.makeMove(grandchild, rMove);

                            int gcWdl = probeWdl(grandchild);
                            if (gcWdl < wdlResult) {
                                allResponsesLoseForOpponent = false;
                                break;
                            }
                        }
                    }

                    if (opponentHasCapture && allResponsesLoseForOpponent) {
                        return (wdlResult == 3) ? 102 : 2;
                    }
                }
            }
        }

        Integer direct = tryDirectDtz(board, wdlResult);
        if (direct != null) return direct;

        int viaSearch = probeDtzViaSearch(board, wdlResult);
        return viaSearch;
    }

    private Integer tryDirectDtz(Chessboard board, int wdlResult) throws IOException {
        String materialName = buildMaterialString(board);
        DtzTable table = dtzCache.computeIfAbsent(materialName, this::loadDtzTable);
        SyzygyMaterial material = table.material();

        boolean actualBoardSideIsBlack = (board.side == black);

        boolean fillColorFlip;
        boolean storedSideIsBlack;
        int flags;

        if (table.symmetric()) {
            fillColorFlip = table.colorFlipped() != actualBoardSideIsBlack;
        } else {
            boolean effectiveBoardSideIsBlack = table.colorFlipped() != actualBoardSideIsBlack;
            FileClassResult fcTmp = determineFileClass(board, material, table.colorFlipped());
            flags = table.pairsHeaders()[fcTmp.fileClass()][0].flags();
            storedSideIsBlack = (flags & 1) != 0;
            if (storedSideIsBlack != effectiveBoardSideIsBlack) {
                return null;
            }
            fillColorFlip = table.colorFlipped();
        }

        FileClassResult fc = determineFileClass(board, material, fillColorFlip);
        int t = fc.fileClass();
        SyzygyPairsHeader ph = table.pairsHeaders()[t][0];
        flags = ph.flags();

        boolean isWtm = true;

        int[] raw;
        if (ph.isConstant()) {
            raw = new int[]{0, 0};
        } else {
            SyzygyEncInfo encInfo = SyzygyEncInfo.build(table.subTables()[t], isWtm, material, t, table.encType());
            int[] p = SyzygyFillSquares.fillSquares(board, table.subTables()[t], isWtm, fillColorFlip, fc.anchorSquare());
            long idx = SyzygyEncoder.encode(p, encInfo, material, table.encType());

            int flatIndex = table.layout().getEntryIndex(t, 0);
            SyzygyHuffmanTable huffman = ph.huffmanTable();
            SyzygyBlockLayout.Entry entry = table.layout().getEntries()[flatIndex];

            raw = SyzygyDecompressor.decompressPairsRaw(table.header(), entry, huffman, idx);
        }

        SyzygyDtzMapEntry mapEntry = table.dtzMapPerTable()[t];
        return SyzygyDtzPostProcess.postProcess(table.header(), raw[0], raw[1], wdlResult, flags, mapEntry);
    }

    private int probeDtzViaSearch(Chessboard board, int wdlResult) throws IOException {
        // wdlResult on the 0~4 scale: 0=Loss,1=BlessedLoss,2=Draw,3=CursedWin,4=Win
        if (wdlResult == 2) {
            return 0; // drawn positions report DTZ 0
        }

        int[] moveArray = new int[MoveCache.MAX_MOVE_SIZE];
        int moveCount = MoveGenerator.generateMoves(board, moveArray);

        if (moveCount == 0) {
            return 0;
        }

        int requiredChildWdl = 4 - wdlResult;
        boolean weAreWinning = wdlResult > 2;

        Integer best = null;

        for (int i = 0; i < moveCount; i++) {
            int move = moveArray[i];
            boolean zeroing = EncodeMove.getMoveCapture(move)
                    || EncodeMove.getMovePiece(move) == P
                    || EncodeMove.getMovePiece(move) == p;

            Chessboard child = new Chessboard(board);
            MoveGenerator.makeMove(child, move);

            int childWdl = probeWdl(child);
            boolean consistent = weAreWinning
                    ? childWdl <= requiredChildWdl
                    : childWdl >= requiredChildWdl;
            if (!consistent) {
                continue;
            }

            int childDistance = zeroing ? 0 : Math.abs(probeDtz(child));
            int candidate = 1 + childDistance;

            if (zeroing && wdlResult == 1) {
                candidate += 100;
            }

            if (best == null
                    || (weAreWinning && candidate < best)
                    || (!weAreWinning && candidate > best)) {
                best = candidate;
            }
        }

        if (best == null) {
            throw new IllegalStateException(
                    "DTZ fallback search found no move consistent with wdlResult=" + wdlResult
                            + " — position may be illegal or a bug elsewhere in the probe");
        }

        return weAreWinning ? best : -best;
    }

    // ---- per-material table loading (cached, called at most once per material) ----

    private WdlTable loadWdlTable(String naturalMaterialName) {
        try {
            String wdlExt = wdlExtFor(naturalMaterialName);
            Path path = syzygyDir.resolve(naturalMaterialName + wdlExt);
            String materialName = naturalMaterialName;
            boolean colorFlipped = false;

            if (!Files.exists(path)) {
                String mirrored = mirrorMaterialString(naturalMaterialName);
                // pawn count/presence is unchanged by mirroring (just swaps which
                // side owns which pieces), so the same extension applies to both.
                Path mirroredPath = syzygyDir.resolve(mirrored + wdlExt);
                if (!Files.exists(mirroredPath)) {
                    throw new IOException(
                            "No WDL tablebase file for " + naturalMaterialName + " (" + path + ") "
                                    + "or its mirror " + mirrored + " (" + mirroredPath + ")");
                }
                materialName = mirrored;
                path = mirroredPath;
                colorFlipped = true;
            }

            SyzygyFile file = SyzygyFile.open(path);
            SyzygyMaterial material = SyzygyMaterial.parse(materialName, connectedKingsEnc);
            MappedByteBuffer header = SyzygyFile.mapFile(path);

            SyzygySubTable[] subTables = material.parseSubTables(header);

            int pairsStartOffset = material.computePairsHeaderStartOffset();
            SyzygyPairsHeadersResult pairsResult =
                    material.parsePairsHeaders(header, pairsStartOffset, file.isSplit(), file.getType());
            SyzygyPairsHeader[][] pairsHeaders = pairsResult.headers();

            SyzygyEncType encType = material.isHasPawns() ? SyzygyEncType.FILE_ENC : SyzygyEncType.PIECE_ENC;

            int sides = file.isSplit() ? 2 : 1;
            long[][] tbSizes = new long[subTables.length][sides];
            for (int t = 0; t < subTables.length; t++) {
                for (int s = 0; s < sides; s++) {
                    tbSizes[t][s] = SyzygyEncInfo.build(subTables[t], s == 0, material, t, encType).getTbSize();
                }
            }

            SyzygyBlockLayout layout = SyzygyBlockLayout.compute(pairsResult.nextOffset(), tbSizes, pairsHeaders);

            return new WdlTable(material, header, subTables, pairsHeaders, layout, encType, sides, colorFlipped);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load WDL table for material " + naturalMaterialName, e);
        }
    }

    private DtzTable loadDtzTable(String naturalMaterialName) {
        try {
            String dtzExt = dtzExtFor(naturalMaterialName);
            Path path = syzygyDir.resolve(naturalMaterialName + dtzExt);
            String materialName = naturalMaterialName;
            boolean colorFlipped = false;

            // Fathom's be->symmetric (key == key2): true when the material's piece
            // composition is identical for both colors, e.g. KRvKR mirrors to itself.
            // Purely a property of the material string, independent of which file
            // (natural or mirrored) ends up being loaded below.
            boolean symmetric = naturalMaterialName.equals(mirrorMaterialString(naturalMaterialName));

            if (!Files.exists(path)) {
                String mirrored = mirrorMaterialString(naturalMaterialName);
                Path mirroredPath = syzygyDir.resolve(mirrored + dtzExt);
                if (!Files.exists(mirroredPath)) {
                    throw new IOException(
                            "No DTZ tablebase file for " + naturalMaterialName + " (" + path + ") "
                                    + "or its mirror " + mirrored + " (" + mirroredPath + ")");
                }
                materialName = mirrored;
                path = mirroredPath;
                colorFlipped = true;
            }

            SyzygyFile file = SyzygyFile.open(path); // split is always false for DTZ
            SyzygyMaterial material = SyzygyMaterial.parse(materialName, connectedKingsEnc);
            MappedByteBuffer header = SyzygyFile.mapFile(path);

            SyzygySubTable[] subTables = material.parseSubTables(header);

            int pairsStartOffset = material.computePairsHeaderStartOffset();
            SyzygyPairsHeadersResult pairsResult =
                    material.parsePairsHeaders(header, pairsStartOffset, file.isSplit(), file.getType());
            SyzygyPairsHeader[][] pairsHeaders = pairsResult.headers();

            SyzygyPairsHeader[] flatDtzHeaders = new SyzygyPairsHeader[subTables.length];
            for (int t = 0; t < subTables.length; t++) {
                flatDtzHeaders[t] = pairsHeaders[t][0]; // DTZ has no side dimension
            }
            SyzygyDtzMapParser.Result dtzMapResult =
                    SyzygyDtzMapParser.parse(header, pairsResult.nextOffset(), flatDtzHeaders);

            SyzygyEncType encType = material.isHasPawns() ? SyzygyEncType.FILE_ENC : SyzygyEncType.PIECE_ENC;

            int sides = 1; // DTZ is always single-sided
            long[][] tbSizes = new long[subTables.length][sides];
            for (int t = 0; t < subTables.length; t++) {
                tbSizes[t][0] = SyzygyEncInfo.build(subTables[t], true, material, t, encType).getTbSize();
            }

            SyzygyBlockLayout layout =
                    SyzygyBlockLayout.compute(dtzMapResult.nextOffset(), tbSizes, pairsHeaders);

            return new DtzTable(material, header, subTables, pairsHeaders, layout,
                    dtzMapResult.perTable(), encType, colorFlipped, symmetric);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load DTZ table for material " + naturalMaterialName, e);
        }
    }

    private record FileClassResult(int fileClass, int anchorSquare) {}

    private static FileClassResult determineFileClass(Chessboard board, SyzygyMaterial material, boolean effectiveColorFlip) {
        if (!material.isHasPawns()) {
            return new FileClassResult(0, -1);
        }

        boolean group0IsBoardWhite = material.isPawnGroup0White() ^ effectiveColorFlip;
        long pawns = group0IsBoardWhite ? board.bitboards[P] : board.bitboards[p];

        int bestSquare = -1;
        int bestFlap = 9999;

        long bb = pawns;
        while (bb != 0) {
            int sq = BitBoardUtils.getLS1BIndex(bb);
            int mirroredSq = effectiveColorFlip ? (sq ^ 0x38) : sq;

            int flap = SyzygyEncodeTables.FLAP[0][mirroredSq];
            if (flap < bestFlap) {
                bestFlap = flap;
                bestSquare = mirroredSq;
            }
            bb = BitBoardUtils.popBit(bb, sq);
        }

        int fileIdx = bestSquare % 8;
        int fileClass = (fileIdx >= 4) ? (7 - fileIdx) : fileIdx;
        return new FileClassResult(fileClass, bestSquare);
    }

    private static String buildMaterialString(Chessboard board) {
        return countedPieces(board, true) + "v" + countedPieces(board, false);
    }

    private static String countedPieces(Chessboard board, boolean isWhite) {
        int[] codes = isWhite ? new int[]{K, Q, R, B, N, P} : new int[]{k, q, r, b, n, p};
        char[] letters = {'K', 'Q', 'R', 'B', 'N', 'P'};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < codes.length; i++) {
            int count = BitBoardUtils.countBits(board.bitboards[codes[i]]);
            for (int c = 0; c < count; c++) {
                sb.append(letters[i]);
            }
        }
        return sb.toString();
    }

    private static String mirrorMaterialString(String materialName) {
        int vIdx = materialName.indexOf('v');
        String whiteSide = materialName.substring(0, vIdx);
        String blackSide = materialName.substring(vIdx + 1);
        return blackSide + "v" + whiteSide;
    }


    /**
     * Get WDL data based on this chess board
     * (-2~2)
     *
     * @param board chess board
     * @return WDL data
     */
    public int getWdlData(Chessboard board) throws IOException {
        int wdlRaw = probeWdl(board);
        int wdl = wdlRaw - 2;

        if (wdl == 0) {
            return 0;
        }

        int dtz = probeDtz(board);

        if (board.half_ply + Math.abs(dtz) > 100) {
            return wdl < 0 ? -1 : 1;
        }

        return wdl;
    }

    /**
     * Get DTZ data based on this chess board
     *
     * @param board chess board
     * @return DTZ data
     */
    public int getDtzData(Chessboard board) throws IOException {
        return probeDtz(board);
    }
}
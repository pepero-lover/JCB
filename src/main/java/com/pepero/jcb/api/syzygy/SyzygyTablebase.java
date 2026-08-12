package com.pepero.jcb.api.syzygy;

import com.pepero.jcb.api.exception.SyzygyUnsupportedMaterialException;
import com.pepero.jcb.bitboard.BitBoardUtils;
import com.pepero.jcb.constant.MoveCache;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.ChessboardUtils;
import com.pepero.jcb.core.MoveGenerator;
import com.pepero.jcb.encode.EncodeMove;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.pepero.jcb.constant.BoardSquares.*;
import static com.pepero.jcb.constant.EncodedPieces.*;
import static com.pepero.jcb.constant.SideToMove.*;

/**
 * A Syzygy tablebase directory, opened once and reused across many probes.
 * <p>
 * Replaces the old static {@code SyzygyProbe} methods. The main reason this is
 * now a stateful object rather than static functions: per-material file parsing
 * (opening the file, mmap-ing it, parsing sub-tables/pairs-headers/block-layout)
 * is somewhat expensive, and {@link #probeDtz} recurses into {@link #probeWdl}
 * for every legal move during its 1-ply fallback search — without caching, the
 * SAME material's file header would get re-parsed from scratch on every single
 * one of those calls. Here, each material's parsed structure is computed once
 * (on first use) and cached for the lifetime of this object.
 * <p>
 * "No split" materials (symmetric materials, e.g. KRvKR, that only ever store one
 * side's data) are handled in {@link #probeWdlTable} by mentally color-flipping the
 * position ONE MORE TIME to reuse the single stored side — combined via XOR with
 * the separate cross-file mirror color-flip (e.g. KRvKQ reusing KQvKR's file, see
 * {@link #mirrorMaterialString}).
 * <p>
 * {@link #probeWdl} additionally recurses into every capture/promotion move before
 * consulting the table directly (matching Fathom's probe_ab pattern) — this also
 * papers over encode/index bugs that only manifest on specific square configurations,
 * since a hung piece gets correctly valued via the capture recursion even if the
 * direct table read for that exact position is wrong.
 */
public class SyzygyTablebase {

    private final Path syzygyDir;

    // one entry per distinct material string ever probed (e.g. "KBNvK", "KQvKR")
    private final Map<String, WdlTable> wdlCache = new ConcurrentHashMap<>();
    private final Map<String, DtzTable> dtzCache = new ConcurrentHashMap<>();

    private static final int DEFAULT_MAX_PIECES = 7; // Syzygy가 현재 지원하는 최대치

    private final int maxPieces;

    public SyzygyTablebase(Path syzygyDir) {
        this(syzygyDir, DEFAULT_MAX_PIECES);
    }

    public SyzygyTablebase(Path syzygyDir, int maxPieces) {
        this.syzygyDir = syzygyDir;
        this.maxPieces = maxPieces;
    }

    /**
     * Everything we need to probe a material's .rtbw file, parsed once and reused.
     */
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

    /**
     * Everything we need to probe a material's .rtbz file, parsed once and reused.
     */
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
            boolean inCheck = ChessboardUtils.isCheck(board);
            return inCheck ? 0 : 2;
        }

        int bestWdl = 0;

        for (int i = 0; i < moveCount; i++) {
            int move = moveArray[i];

            boolean isCapture = EncodeMove.getMoveCapture(move);
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

        int tableWdl = probeWdlTable(board);
        int result = Math.max(bestWdl, tableWdl);

        return result;
    }

    private int probeWdlTable(Chessboard board) throws IOException {
        int boardPiece = BitBoardUtils.countBits(board.occupancies[both]);
        if(boardPiece == 2) return 2;

        if(boardPiece > maxPieces)
            throw new SyzygyUnsupportedMaterialException(
                    "This Syzygy tablebase's supporting piece count is less than this board's piece count! " +
                            "(supporting : " + maxPieces +
                            ", chess board : " + boardPiece + ")"
            );

        String materialName = buildMaterialString(board);
        WdlTable table = wdlCache.computeIfAbsent(materialName, this::loadWdlTable);

        SyzygyMaterial material = table.material();
        FileClassResult fc = determineFileClass(board, material, table.colorFlipped());
        int t = fc.fileClass();

        // if we're reading a mirrored file, the file's notion of "white to move"
        // corresponds to the board's actual side being black, and vice versa —
        // XOR the flip flag against the real side to move to get which slot the
        // file actually stores this position's data under.
        boolean actualWtm = (board.side == white);
        boolean mirrorFlip = table.colorFlipped();
        boolean isWtm = mirrorFlip != actualWtm;

        // "no split" files (symmetric materials, e.g. KRvKR) only ever store side=0
        // (wtm) data — side=1 doesn't exist. When isWtm resolves to false here but
        // there's no second side to read, mentally color-flip the position ONE MORE
        // TIME so it can be probed as if it were white to move, reusing the single
        // stored side. The two flips (cross-file mirror + this same-file reuse) are
        // independent booleans, so they combine via XOR.
        boolean noSplitReuse = (table.sides() == 1) && !isWtm;
        if (noSplitReuse) {
            isWtm = true;
        }
        boolean effectiveColorFlip = mirrorFlip ^ noSplitReuse;

        int side = isWtm ? 0 : 1;

        SyzygyPairsHeader ph = table.pairsHeaders()[t][side];

        // constant sub-tables store no huffman/block data at all — every position in
        // this (sub-table, side) has the same WDL result, so skip decompression entirely.
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
    public int probeDtz(Chessboard board) throws IOException {
        int boardPiece = BitBoardUtils.countBits(board.occupancies[both]);
        if (boardPiece == 2) return 0;

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
                if (!zeroing) continue;

                Chessboard child = new Chessboard(board);
                MoveGenerator.makeMove(child, move);
                int childWdl = probeWdl(child);
                if (childWdl == requiredChildWdl) {
                    return (wdlResult == 3) ? 101 : 1;
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
        FileClassResult fc = determineFileClass(board, material, table.colorFlipped());
        int t = fc.fileClass();

        // Does this sub-table's stored side-to-move (flags bit 0) match the actual board?
        // When reading a mirrored file, the board's real side-to-move must first be
        // reinterpreted through the same color flip used for WDL above.
        int flags = table.pairsHeaders()[t][0].flags();
        boolean storedSideIsBlack = (flags & 1) != 0;
        boolean actualBoardSideIsBlack = (board.side == black);

        // fillSquares() needs to know whether to mentally swap white<->black pieces
        // when reading the board, on top of whatever cross-file mirror (colorFlipped)
        // was already applied when this material's file was chosen.
        boolean fillColorFlip;

        if (table.symmetric()) {
            // Symmetric material (e.g. KRvKR, KNNvKNN — identical piece composition
            // on both sides): matches Fathom's probe_table(), which for be->symmetric
            // forces bside=false and ignores the stored-side flag entirely, always
            // reusing the single stored side via an extra flip keyed only on whose
            // turn it is. Same trick as WDL's noSplitReuse above — just applied to
            // DTZ, which Fathom always does but this port previously didn't, causing
            // an unnecessary probeDtzViaSearch() fallback for these materials.
            fillColorFlip = table.colorFlipped() != actualBoardSideIsBlack;
        } else {
            boolean effectiveBoardSideIsBlack = table.colorFlipped() != actualBoardSideIsBlack;
            if (storedSideIsBlack != effectiveBoardSideIsBlack) {
                return null; // wrong side stored in this file — caller must search instead
            }
            fillColorFlip = table.colorFlipped();
        }

        // DTZ sub-tables only ever store ONE piece order (wtmPieces); the "btm" nibble
        // parsed by SyzygyMaterial.parseSubTables is NOT meaningfully populated for DTZ
        // files (it's just leftover/zero padding from reusing the same byte-layout code
        // as WDL) — using it throws "Invalid Syzygy piece code: 0". Always use wtmPieces();
        // fillColorFlip (above) already accounts for whichever side the file actually
        // stores, so wtmPieces() IS the correct order regardless of what it's called.
        boolean isWtm = true;

        SyzygyPairsHeader ph = table.pairsHeaders()[t][0];

        int[] raw;
        if (ph.isConstant()) {
            // constant DTZ sub-table: no huffman/block data to decompress at all.
            // Verified against Fathom's decompress_pairs(): it short-circuits on
            // idxBits==0 and returns constValue directly, and constValue[0] is
            // hardcoded to 0 for DTZ (never read from the file) — the same
            // map/parity post-processing below still applies afterward either way.
            // So raw={0,0} here is exactly equivalent to going through the real
            // decompress path for a constant table, not a placeholder/guess.
            raw = new int[]{0, 0};
        } else {
            SyzygyEncInfo encInfo = SyzygyEncInfo.build(table.subTables()[t], isWtm, material, t, table.encType());
            int[] p = SyzygyFillSquares.fillSquares(board, table.subTables()[t], isWtm, fillColorFlip, fc.anchorSquare());
            long idx = SyzygyEncoder.encode(p, encInfo, material, table.encType());

            // NOTE: naive flatIndex==t breaks if any earlier sub-table (t' < t) was
            // constant (it shifts every later entries[] index down) — must go through
            // getEntryIndex() rather than assuming sides=1 means flatIndex==t.
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

            int childDistance = zeroing ? 0 : probeDtz(child);
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

        return best;
    }

    // ---- per-material table loading (cached, called at most once per material) ----

    private WdlTable loadWdlTable(String naturalMaterialName) {
        try {
            Path path = syzygyDir.resolve(naturalMaterialName + ".rtbw");
            String materialName = naturalMaterialName;
            boolean colorFlipped = false;

            if (!Files.exists(path)) {
                String mirrored = mirrorMaterialString(naturalMaterialName);
                Path mirroredPath = syzygyDir.resolve(mirrored + ".rtbw");
                if (!Files.exists(mirroredPath)) {
                    throw new IOException(
                            "No tablebase file for " + naturalMaterialName + " or its mirror " + mirrored);
                }
                materialName = mirrored;
                path = mirroredPath;
                colorFlipped = true;
            }

            SyzygyFile file = SyzygyFile.open(path);
            SyzygyMaterial material = SyzygyMaterial.parse(materialName);
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
            Path path = syzygyDir.resolve(naturalMaterialName + ".rtbz");
            String materialName = naturalMaterialName;
            boolean colorFlipped = false;

            // Fathom's be->symmetric (key == key2): true when the material's piece
            // composition is identical for both colors, e.g. KRvKR mirrors to itself.
            // Purely a property of the material string, independent of which file
            // (natural or mirrored) ends up being loaded below.
            boolean symmetric = naturalMaterialName.equals(mirrorMaterialString(naturalMaterialName));

            if (!Files.exists(path)) {
                String mirrored = mirrorMaterialString(naturalMaterialName);
                Path mirroredPath = syzygyDir.resolve(mirrored + ".rtbz");
                if (!Files.exists(mirroredPath)) {
                    throw new IOException(
                            "No tablebase file for " + naturalMaterialName + " or its mirror " + mirrored);
                }
                materialName = mirrored;
                path = mirroredPath;
                colorFlipped = true;
            }

            SyzygyFile file = SyzygyFile.open(path); // split is always false for DTZ
            SyzygyMaterial material = SyzygyMaterial.parse(materialName);
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

    private static FileClassResult determineFileClass(Chessboard board, SyzygyMaterial material, boolean fileColorFlipped) {
        if (!material.isHasPawns()) {
            return new FileClassResult(0, -1);
        }

        boolean group0IsBoardWhite = material.isPawnGroup0White() ^ fileColorFlipped;
        long pawns = group0IsBoardWhite ? board.bitboards[P] : board.bitboards[p];

        int bestSquare = -1;
        int bestTwist = -1;
        long bb = pawns;
        while (bb != 0) {
            int sq = BitBoardUtils.getLS1BIndex(bb);
            int twist = SyzygyIndexTables.PAWN_TWIST[0][sq];
            if (twist > bestTwist) {
                bestTwist = twist;
                bestSquare = sq;
            }
            bb = BitBoardUtils.popBit(bb, sq);
        }

        int fileIdx = bestSquare % 8;
        int fileClass = (fileIdx >= 4) ? (7 - fileIdx) : fileIdx;
        return new FileClassResult(fileClass, bestSquare);
    }
    private static String buildMaterialString(Chessboard board) {
        return "K" + countedPieces(board, true) + "vK" + countedPieces(board, false);
    }

    private static String countedPieces(Chessboard board, boolean isWhite) {
        int[] codes = isWhite ? new int[]{Q, R, B, N, P} : new int[]{q, r, b, n, p};
        char[] letters = {'Q', 'R', 'B', 'N', 'P'};
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
        String whiteSide = materialName.substring(1, vIdx);
        String blackSide = materialName.substring(vIdx + 2);
        return "K" + blackSide + "vK" + whiteSide;
    }


    /**
     * Get WDL data based on this chess board
     * (-2~2)
     *
     * @param board chess board
     * @return WDL data
     */
    public int getWdlData(Chessboard board) throws IOException {
        int wdlRaw = probeWdl(board); // 0~4 스케일 (0=Loss,1=BlessedLoss,2=Draw,3=CursedWin,4=Win)
        int wdl = wdlRaw - 2;

        if (wdl == 0) {
            return 0;
        }

        int dtz = probeDtz(board);

        if (board.half_ply + dtz >= 100) {
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
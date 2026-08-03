package com.pepero.jcb.api.syzygy;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.exception.SyzygyUnsupportedMaterialException;
import com.pepero.jcb.api.syzygy.logics.*;
import com.pepero.jcb.api.syzygy.logics.dtz.SyzygyDtzMapEntry;
import com.pepero.jcb.api.syzygy.logics.dtz.SyzygyDtzMapParser;
import com.pepero.jcb.api.syzygy.logics.dtz.SyzygyDtzPostProcess;
import com.pepero.jcb.bitboard.BitBoardUtils;
import com.pepero.jcb.constant.MoveCache;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.MoveGenerator;
import com.pepero.jcb.encode.EncodeMove;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
 * KNOWN LIMITATIONS (not yet handled, flagged for later):
 * - Doesn't yet handle "no split" materials that require mentally color-flipping
 *   the position to reuse a single stored side's data for the other side to move
 *   WITHIN a single (non-mirrored) file. (Mirrored-material color-flip, e.g.
 *   KRvKQ reusing KQvKR's file, IS handled — see {@link #mirrorMaterialString}.)
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
            boolean colorFlipped
    ) {}

    /**
     * Probe the WDL result on this board
     *
     * @param board chess board
     * @return wdl result
     */
    public int probeWdl(Chessboard board) throws IOException {
        if(BitBoardUtils.countBits(board.occupancies[both]) > maxPieces)
            throw new SyzygyUnsupportedMaterialException(
                    "This Syzygy tablebase's supporting piece count is less than this board's piece count! " +
                            "(supporting : " + maxPieces +
                            ", chess board : " + BitBoardUtils.countBits(board.occupancies[both]) + ")"
            );

        String materialName = buildMaterialString(board);
        WdlTable table = wdlCache.computeIfAbsent(materialName, this::loadWdlTable);

        SyzygyMaterial material = table.material();
        int t = determineFileClass(board, material);

        // if we're reading a mirrored file, the file's notion of "white to move"
        // corresponds to the board's actual side being black, and vice versa —
        // XOR the flip flag against the real side to move to get which slot the
        // file actually stores this position's data under.
        boolean actualWtm = (board.side == white);
        boolean isWtm = table.colorFlipped() != actualWtm;
        int side = isWtm ? 0 : 1;
        int flatIndex = t * table.sides() + side;

        SyzygyEncInfo encInfo = SyzygyEncInfo.build(table.subTables()[t], isWtm, material, t, table.encType());
        int[] p = SyzygyFillSquares.fillSquares(board, table.subTables()[t], isWtm, table.colorFlipped());
        long idx = SyzygyEncoder.encode(p, encInfo, material, table.encType());

        SyzygyHuffmanTable huffman = table.pairsHeaders()[t][side].huffmanTable();
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
        int wdlResult = probeWdl(board);

        Integer direct = tryDirectDtz(board, wdlResult);
        if (direct != null) {
            return direct;
        }

        return probeDtzViaSearch(board, wdlResult);
    }

    private Integer tryDirectDtz(Chessboard board, int wdlResult) throws IOException {
        String materialName = buildMaterialString(board);
        DtzTable table = dtzCache.computeIfAbsent(materialName, this::loadDtzTable);

        SyzygyMaterial material = table.material();
        int t = determineFileClass(board, material);

        // DTZ sub-tables only ever store ONE piece order (wtmPieces); btmPieces isn't a
        // real second array for DTZ, it's just unused padding — always false here.
        boolean isWtm = true;

        // Does this sub-table's stored side-to-move (flags bit 0) match the actual board?
        // When reading a mirrored file, the board's real side-to-move must first be
        // reinterpreted through the same color flip used for WDL above.
        int flags = table.pairsHeaders()[t][0].flags();
        boolean storedSideIsBlack = (flags & 1) != 0;
        boolean actualBoardSideIsBlack = (board.side == black);
        boolean effectiveBoardSideIsBlack = table.colorFlipped() != actualBoardSideIsBlack;
        if (storedSideIsBlack != effectiveBoardSideIsBlack) {
            return null; // wrong side stored in this file — caller must search instead
        }

        SyzygyEncInfo encInfo = SyzygyEncInfo.build(table.subTables()[t], isWtm, material, t, table.encType());
        int[] p = SyzygyFillSquares.fillSquares(board, table.subTables()[t], isWtm, table.colorFlipped());
        long idx = SyzygyEncoder.encode(p, encInfo, material, table.encType());

        SyzygyHuffmanTable huffman = table.pairsHeaders()[t][0].huffmanTable();
        SyzygyBlockLayout.Entry entry = table.layout().getEntries()[t]; // sides=1, so flatIndex == t

        int[] raw = SyzygyDecompressor.decompressPairsRaw(table.header(), entry, huffman, idx);
        SyzygyDtzMapEntry mapEntry = table.dtzMapPerTable()[t];

        return SyzygyDtzPostProcess.postProcess(table.header(), raw[0], raw[1], wdlResult, flags, mapEntry);
    }

    private int probeDtzViaSearch(Chessboard board, int wdlResult) throws IOException {
        // wdlResult on the 0~4 scale: 0=Loss,1=BlessedLoss,2=Draw,3=CursedWin,4=Win
        if (wdlResult == 2) {
            return 0; // drawn positions report DTZ 0
        }

        int requiredChildWdl = 4 - wdlResult;
        boolean weAreWinning = wdlResult > 2;

        int[] moveArray = new int[MoveCache.MAX_MOVE_SIZE];
        int moveCount = MoveGenerator.generateMoves(board, moveArray);

        Integer best = null;

        for (int i = 0; i < moveCount; i++) {
            int move = moveArray[i];
            boolean zeroing = EncodeMove.getMoveCapture(move)
                    || EncodeMove.getMovePiece(move) == P
                    || EncodeMove.getMovePiece(move) == p;

            Chessboard child = new Chessboard(board);
            MoveGenerator.makeMove(child, move);

            int childWdl = probeWdl(child);
            if (childWdl != requiredChildWdl) {
                continue; // this move doesn't preserve optimal play
            }

            int childDistance = zeroing ? 0 : probeDtz(child);
            int candidate = 1 + childDistance;

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
                    dtzMapResult.perTable(), encType, colorFlipped);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load DTZ table for material " + naturalMaterialName, e);
        }
    }

    private static int determineFileClass(Chessboard board, SyzygyMaterial material) {
        if (!material.isHasPawns()) {
            return 0;
        }
        long pawns = board.getBitboardPiece(P) | board.getBitboardPiece(p);
        int square = BitBoardUtils.getLS1BIndex(pawns);
        int fileIdx = square % 8;
        return (fileIdx >= 4) ? (7 - fileIdx) : fileIdx;
    }

    private static String buildMaterialString(Chessboard board) {
        return "K" + countedPieces(board, true) + "vK" + countedPieces(board, false);
    }

    private static String countedPieces(Chessboard board, boolean isWhite) {
        int[] codes = isWhite ? new int[]{Q, R, B, N, P} : new int[]{q, r, b, n, p};
        char[] letters = {'Q', 'R', 'B', 'N', 'P'};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < codes.length; i++) {
            int count = BitBoardUtils.countBits(board.getBitboardPiece(codes[i]));
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
}
package com.pepero.jcb.api.syzygy;

import com.pepero.jcb.api.syzygy.logics.*;
import com.pepero.jcb.api.syzygy.logics.dtz.SyzygyDtzMapEntry;
import com.pepero.jcb.api.syzygy.logics.dtz.SyzygyDtzMapParser;
import com.pepero.jcb.api.syzygy.logics.dtz.SyzygyDtzPostProcess;
import com.pepero.jcb.bitboard.BitBoardUtils;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.ChessboardUtils;
import com.pepero.jcb.core.MoveGenerator;
import com.pepero.jcb.encode.EncodeMove;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.pepero.jcb.constant.EncodedPieces.*;
import static com.pepero.jcb.constant.SideToMove.*;

/**
 * Public entry point: probe a Syzygy WDL/DTZ tablebase for the given board position.
 * <p>
 * KNOWN LIMITATIONS (not yet handled, flagged for later):
 * - Doesn't yet handle "no split" materials that require mentally color-flipping
 *   the position to reuse a single stored side's data for the other side to move.
 *   (This refers to the split=false / single-side-stored-per-file case inside a
 *   normally-named file, which is different from the mirrored-material case below
 *   and is still unhandled.)
 */
public class SyzygyProbe {

    /**
     * Probe the WDL result for the given position.
     * <p>
     * Some materials are only generated under one of the two mirrored orderings
     * (e.g. "KQvKR" exists but "KRvKQ" does not). When the position's natural
     * material string has no matching file, we look up the mirrored name instead
     * and mentally color-flip the position while reading it: pieces the file calls
     * "white" are actually the board's black pieces and vice versa. See
     * {@link #mirrorMaterialString(String)} and the {@code colorFlipped} handling
     * below.
     *
     * @param board      current position
     * @param syzygyDir  directory containing the .rtbw files
     * @return WDL result code: 0=Loss, 1=BlessedLoss, 2=Draw, 3=CursedWin, 4=Win
     *         (from the perspective of the side to move on {@code board})
     */
    public static int probeWdl(Chessboard board, Path syzygyDir) throws IOException {
        String materialName = buildMaterialString(board);
        Path path = syzygyDir.resolve(materialName + ".rtbw");
        boolean colorFlipped = false;

        if (!Files.exists(path)) {
            String mirrored = mirrorMaterialString(materialName);
            Path mirroredPath = syzygyDir.resolve(mirrored + ".rtbw");
            if (!Files.exists(mirroredPath)) {
                throw new IOException(
                        "No tablebase file for " + materialName + " or its mirror " + mirrored);
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

        int t = determineFileClass(board, material);

        // If we're reading a mirrored file, the file's notion of "white to move"
        // corresponds to the board's actual side being black, and vice versa —
        // XOR the flip flag against the real side to move to get which slot the
        // file actually stores this position's data under.
        boolean actualWtm = (board.side == white);
        boolean isWtm = colorFlipped != actualWtm;
        int side = isWtm ? 0 : 1;
        int flatIndex = t * sides + side;

        SyzygyEncInfo encInfo = SyzygyEncInfo.build(subTables[t], isWtm, material, t, encType);
        int[] p = SyzygyFillSquares.fillSquares(board, subTables[t], isWtm, colorFlipped);
        long idx = SyzygyEncoder.encode(p, encInfo, material, encType);

        SyzygyHuffmanTable huffman = pairsHeaders[t][side].huffmanTable();
        SyzygyBlockLayout.Entry entry = layout.getEntries()[flatIndex];

        return SyzygyDecompressor.decompressPairs(header, entry, huffman, idx);
    }

    /**
     * Maximum number of pseudo-legal/legal moves a position can have; used to size the
     * per-call scratch move array for the 1-ply DTZ fallback search below.
     */
    private static final int MAX_MOVES = 256;

    /**
     * Probe the DTZ (distance-to-zeroing-move) result for the given position.
     * Internally probes WDL first, since the DTZ remap table selection depends on it.
     * <p>
     * .rtbz files are "single-sided": each material's DTZ file only stores data for ONE
     * side to move (see the {@code flags & 1} bit on each sub-table's pairs header). If
     * the actual position's side to move doesn't match what's stored, the table can't be
     * read directly — instead we fall back to a 1-ply search: try every legal move, probe
     * the resulting (opponent-to-move) position, and reconstruct DTZ as
     * {@code 1 + child's distance} from whichever move preserves the WDL evaluation.
     * This mirrors (a simplified version of) Fathom's top-level {@code probe_dtz()}.
     *
     * @param board      current position
     * @param syzygyDir  directory containing both .rtbw and .rtbz files
     * @return the DTZ distance
     */
    public static int probeDtz(Chessboard board, Path syzygyDir) throws IOException {
        int wdlResult = probeWdl(board, syzygyDir);

        Integer direct = tryDirectDtz(board, syzygyDir, wdlResult);
        if (direct != null) {
            return direct;
        }

        return probeDtzViaSearch(board, syzygyDir, wdlResult);
    }

    /**
     * Attempt to read the DTZ value straight from this material's .rtbz file.
     * <p>
     * Same mirrored-material handling as {@link #probeWdl}: if the natural material
     * name has no .rtbz file, try the mirrored name and color-flip the position while
     * reading it.
     *
     * @return the DTZ distance, or {@code null} if this DTZ sub-table doesn't store data
     *         for the current side to move (caller must fall back to search)
     */
    private static Integer tryDirectDtz(Chessboard board, Path syzygyDir, int wdlResult) throws IOException {
        String materialName = buildMaterialString(board);
        Path path = syzygyDir.resolve(materialName + ".rtbz");
        boolean colorFlipped = false;

        if (!Files.exists(path)) {
            String mirrored = mirrorMaterialString(materialName);
            Path mirroredPath = syzygyDir.resolve(mirrored + ".rtbz");
            if (!Files.exists(mirroredPath)) {
                throw new IOException(
                        "No tablebase file for " + materialName + " or its mirror " + mirrored);
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

        // DTZ-only: parse the extra remap-table section that sits between the
        // pairs headers and the indexTable/sizeTable/block-data region
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

        int t = determineFileClass(board, material);

        // DTZ sub-tables only ever store ONE piece order (wtmPieces); btmPieces isn't a
        // real second array for DTZ, it's just unused padding — always false here.
        boolean isWtm = true;

        // Does this sub-table's stored side-to-move (flags bit 0) match the actual board?
        // When reading a mirrored file, the board's real side-to-move must first be
        // reinterpreted through the same color flip used for WDL above.
        int flags = pairsHeaders[t][0].flags();
        boolean storedSideIsBlack = (flags & 1) != 0;
        boolean actualBoardSideIsBlack = (board.side == black);
        boolean effectiveBoardSideIsBlack = colorFlipped != actualBoardSideIsBlack;
        if (storedSideIsBlack != effectiveBoardSideIsBlack) {
            return null; // wrong side stored in this file — caller must search instead
        }

        SyzygyEncInfo encInfo = SyzygyEncInfo.build(subTables[t], isWtm, material, t, encType);
        int[] p = SyzygyFillSquares.fillSquares(board, subTables[t], isWtm, colorFlipped);
        System.out.println("[ENCODE-DEBUG] fen=" + ChessboardUtils.getFen(board)
                + " p(fillSquares결과)=" + java.util.Arrays.toString(p)
                + " norm=" + java.util.Arrays.toString(encInfo.getNorm())
                + " factor=" + java.util.Arrays.toString(encInfo.getFactor())
                + " subTable=" + subTables[t]);
        long idx = SyzygyEncoder.encode(p, encInfo, material, encType);
        System.out.println("[ENCODE-DEBUG] 계산된 idx=" + idx);

        SyzygyHuffmanTable huffman = pairsHeaders[t][0].huffmanTable();
        SyzygyBlockLayout.Entry entry = layout.getEntries()[t]; // sides=1, so flatIndex == t

        int[] raw = SyzygyDecompressor.decompressPairsRaw(header, entry, huffman, idx);
        SyzygyDtzMapEntry mapEntry = dtzMapResult.perTable()[t];

        return SyzygyDtzPostProcess.postProcess(header, raw[0], raw[1], wdlResult, flags, mapEntry);
    }

    /**
     * 1-ply fallback used when the .rtbz file doesn't store data for the current side to
     * move: try every legal move, and among the ones that preserve the position's WDL
     * evaluation (i.e. the "correct" moves under optimal play), reconstruct DTZ as
     * {@code 1 + child distance} (or just {@code 1} if the move itself zeroes the counter,
     * i.e. a capture or pawn move). If we're winning, the shortest such continuation is
     * chosen; if we're losing, the longest (best defense) is chosen, matching how the
     * distance is defined under optimal play by both sides.
     */
    private static int probeDtzViaSearch(Chessboard board, Path syzygyDir, int wdlResult) throws IOException {
        // wdlResult on the 0~4 scale: 0=Loss,1=BlessedLoss,2=Draw,3=CursedWin,4=Win
        if (wdlResult == 2) {
            return 0; // drawn positions report DTZ 0
        }

        // A move that preserves the (negated) evaluation for the opponent must land on
        // exactly this WDL code from the opponent's point of view.
        int requiredChildWdl = 4 - wdlResult;
        boolean weAreWinning = wdlResult > 2;

        int[] moveArray = new int[MAX_MOVES];
        int moveCount = MoveGenerator.generateMoves(board, moveArray);

        Integer best = null;

        for (int i = 0; i < moveCount; i++) {
            int move = moveArray[i];
            boolean zeroing = EncodeMove.getMoveCapture(move)
                    || EncodeMove.getMovePiece(move) == P
                    || EncodeMove.getMovePiece(move) == p;

            Chessboard child = new Chessboard(board);
            MoveGenerator.makeMove(child, move);

            int childWdl = probeWdl(child, syzygyDir);
            if (childWdl != requiredChildWdl) {
                continue; // this move doesn't preserve optimal play
            }

            int childDistance = zeroing ? 0 : probeDtz(child, syzygyDir);
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

    /**
     * Which sub-table (pawn file-class 0~3) this position belongs to.
     * No pawns -> always 0. With pawns -> mirror e~h onto a~d, same rule encode() uses internally.
     * <p>
     * Unaffected by color-flipping: this only depends on which square a pawn physically
     * sits on, not on which side "owns" it in the file's naming.
     */
    private static int determineFileClass(Chessboard board, SyzygyMaterial material) {
        if (!material.isHasPawns()) {
            return 0;
        }

        long pawns = board.getBitboardPiece(P) | board.getBitboardPiece(p);
        int square = BitBoardUtils.getLS1BIndex(pawns);
        int fileIdx = square % 8;
        return (fileIdx >= 4) ? (7 - fileIdx) : fileIdx;
    }

    /**
     * Builds the "KPvK"-style material name from the board's actual pieces:
     * King first, then Q/R/B/N/P by count (descending value order), for white then black.
     */
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

    /**
     * Swap the white-side and black-side piece strings of a "K...vK..." material name.
     * Used when the natural material has no tablebase file but its mirror does
     * (e.g. "KRvKQ" not generated, but "KQvKR" was) — Syzygy only stores one of each
     * such mirrored pair.
     */
    private static String mirrorMaterialString(String materialName) {
        int vIdx = materialName.indexOf('v');
        String whiteSide = materialName.substring(1, vIdx);   // drop leading 'K'
        String blackSide = materialName.substring(vIdx + 2);  // drop "vK"
        return "K" + blackSide + "vK" + whiteSide;
    }
}
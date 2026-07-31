package com.pepero.jcb.api.syzygy;

import com.pepero.jcb.api.syzygy.logics.*;
import com.pepero.jcb.bitboard.BitBoardUtils;
import com.pepero.jcb.core.Chessboard;

import java.io.IOException;
import java.nio.file.Path;

import static com.pepero.jcb.constant.EncodedPieces.*;
import static com.pepero.jcb.constant.SideToMove.*;

/**
 * Public entry point: probe a Syzygy WDL tablebase for the given board position.
 * <p>
 * KNOWN LIMITATIONS (not yet handled, flagged for later):
 * - Only WDL probing (no DTZ yet).
 * - Doesn't yet handle "no split" materials that require mentally color-flipping
 *   the position to reuse a single stored side's data for the other side to move.
 * - Assumes the material string built from the board (K + descending-value pieces,
 *   per side) exactly matches the tablebase file's name — some materials may only
 *   be stored under the mirrored name (e.g. "KRvKQ" file might not exist if only
 *   "KQvKR" was generated); that swap+reinterpretation isn't handled here yet.
 */
public class SyzygyProbe {

    /**
     * Probe the WDL result for the given position.
     *
     * @param board      current position
     * @param syzygyDir  directory containing the .rtbw files
     * @return WDL result code: 0=Loss, 1=BlessedLoss, 2=Draw, 3=CursedWin, 4=Win
     *         (from the perspective of the side to move on {@code board})
     */
    public static int probeWdl(Chessboard board, Path syzygyDir) throws IOException {
        String materialName = buildMaterialString(board);
        Path path = syzygyDir.resolve(materialName + ".rtbw");

        SyzygyFile file = SyzygyFile.open(path);
        SyzygyMaterial material = SyzygyMaterial.parse(materialName);
        byte[] header = SyzygyFile.readHeader(path);

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
        boolean isWtm = (board.side == white);
        int side = isWtm ? 0 : 1;
        int flatIndex = t * sides + side;

        SyzygyEncInfo encInfo = SyzygyEncInfo.build(subTables[t], isWtm, material, t, encType);
        int[] p = SyzygyFillSquares.fillSquares(board, subTables[t], isWtm);
        long idx = SyzygyEncoder.encode(p, encInfo, material, encType);

        SyzygyHuffmanTable huffman = pairsHeaders[t][side].huffmanTable();
        SyzygyBlockLayout.Entry entry = layout.getEntries()[flatIndex];

        return SyzygyDecompressor.decompressPairs(header, entry, huffman, idx);
    }

    /**
     * Which sub-table (pawn file-class 0~3) this position belongs to.
     * No pawns -> always 0. With pawns -> mirror e~h onto a~d, same rule encode() uses internally.
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
}
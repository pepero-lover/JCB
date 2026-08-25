package com.pepero.jcb.api.gaviota;

import com.pepero.jcb.core.constant.EncodedPieces;
import com.pepero.jcb.core.constant.MoveCache;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.ChessboardUtils;
import com.pepero.jcb.core.MoveGenerator;
import com.pepero.jcb.core.bitboard.BitBoardUtils;
import com.pepero.jcb.core.encode.EncodeMove;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ported from gaviota.py's PythonTablebase class. This class owns the
 * material-key resolution ({@code _setup_tablebase}), file/mmap management
 * ({@code _open_tablebase}), the block-fetch + LRU cache pipeline
 * ({@code _tb_probe}), and the Chessboard-facing probe entry points
 * ({@link #probeDtm}/{@link #probeWdl}, ported from {@code probe_dtm}/
 * {@code probe_wdl} including the en passant resolution loop).
 * <p>
 * Usage structure mirrors {@link com.pepero.jcb.api.syzygy.SyzygyTablebase}:
 * the table directory is handed to the constructor (no separate
 * {@code addDirectory()} step), and each distinct material key is resolved,
 * mapped, and cached lazily the first time it's probed, via
 * {@code tableCache.computeIfAbsent(...)} — same as Syzygy's
 * {@code wdlCache}/{@code dtzCache}. Natural-vs-mirrored file resolution
 * (white-then-black material name, falling back to black-then-white) also
 * follows Syzygy's natural/mirrored-path fallback in {@code loadWdlTable}/
 * {@code loadDtzTable}. There is no explicit {@code close()}; like Syzygy,
 * mapped byte buffers are simply left for the GC (no unmap in plain
 * {@code java.nio}).
 */
public final class GaviotaTablebase {

    private static final int MAX_CACHED_BLOCKS = 128;

    // result codes, same scale as GaviotaBlockDecoder's I_DRAW/I_WMATE/I_BMATE/I_FORBID
    public static final int I_DRAW = GaviotaBlockDecoder.I_DRAW;
    public static final int I_WMATE = GaviotaBlockDecoder.I_WMATE;
    public static final int I_BMATE = GaviotaBlockDecoder.I_BMATE;
    public static final int I_FORBID = GaviotaBlockDecoder.I_FORBID;

    /** Thrown when no table file is available for a requested material combination. */
    public static final class MissingTableException extends RuntimeException {
        public MissingTableException(String message) {
            super(message);
        }
    }

    private final Path gaviotaDir;

    // one entry per distinct egKey ever probed (e.g. "KPK", "KQKR"), keyed by
    // the *natural* (white-material + black-material) key — same convention
    // as Syzygy's wdlCache/dtzCache being keyed by the board's natural material string.
    private final Map<String, GaviotaTable> tableCache = new ConcurrentHashMap<>();

    private final Map<String, TableBlock> blockCache = new ConcurrentHashMap<>();
    private final AtomicLong blockAge = new AtomicLong();

    public GaviotaTablebase(Path gaviotaDir) {
        this.gaviotaDir = gaviotaDir;
    }

    /**
     * One resolved *.gtb.cp4 file: its mapped bytes, block index, and whether
     * it was loaded via the mirrored (black-then-white) file name instead of
     * the natural (white-then-black) one — the Gaviota analogue of Syzygy's
     * WdlTable/DtzTable {@code colorFlipped} flag.
     */
    private record GaviotaTable(
            String egKey,
            MappedByteBuffer header,
            GaviotaZipInfo zipInfo,
            boolean reversed
    ) {}

    private static final class TableBlock {
        final String egKey;
        final int side;
        final long blockOffset; // idx / ENTRIES_PER_BLOCK, i.e. split_index()'s first element
        volatile long age;
        int[] pcache; // unpacked distance codes (still prefix|plies<<3 form)

        TableBlock(String egKey, int side, long blockOffset, long age) {
            this.egKey = egKey;
            this.side = side;
            this.blockOffset = blockOffset;
            this.age = age;
        }
    }

    private static String cacheKey(String egKey, long blockOffset, int side) {
        return egKey + '|' + blockOffset + '|' + side;
    }

    // ============================================================
    // Material-key resolution + file loading — ported from
    // _setup_tablebase()/_open_tablebase(), restructured around a single
    // lazy per-material cache the way Syzygy's loadWdlTable()/loadDtzTable() are.
    // ============================================================

    /**
     * Resolves req.egKey (trying white-then-black material name, then the
     * reversed black-then-white name), sets req.whitePieceSquares/Types and
     * req.blackPieceSquares/Types (flipping+swapping colors if the reversed
     * table had to be used), and returns the resolved, mapped table.
     *
     * @throws MissingTableException if neither ordering has a table file available
     */
    private GaviotaTable setupTablebase(GaviotaRequest req) {
        String whiteLetters = pieceLetters(req.whiteTypes);
        String blackLetters = pieceLetters(req.blackTypes);
        String naturalKey = whiteLetters + blackLetters;
        String mirroredKey = blackLetters + whiteLetters;

        GaviotaTable table = tableCache.computeIfAbsent(naturalKey, k -> loadTable(naturalKey, mirroredKey));

        req.isReversed = table.reversed();
        req.egKey = table.egKey();

        if (!req.isReversed) {
            req.whitePieceSquares = req.whiteSquares;
            req.whitePieceTypes = req.whiteTypes;
            req.blackPieceSquares = req.blackSquares;
            req.blackPieceTypes = req.blackTypes;
        } else {
            req.whitePieceSquares = flipNsAll(req.blackSquares);
            req.whitePieceTypes = req.blackTypes;
            req.blackPieceSquares = flipNsAll(req.whiteSquares);
            req.blackPieceTypes = req.whiteTypes;
            req.side = opp(req.side);
        }

        return table;
    }

    /**
     * Loads and maps the *.gtb.cp4 file for {@code naturalKey}, falling back
     * to {@code mirroredKey} if the natural one doesn't exist on disk — same
     * natural/mirrored fallback shape as Syzygy's loadWdlTable()/loadDtzTable().
     */
    private GaviotaTable loadTable(String naturalKey, String mirroredKey) {
        try {
            Path naturalPath = gaviotaDir.resolve(naturalKey + ".gtb.cp4");
            String egKey = naturalKey;
            Path path = naturalPath;
            boolean reversed = false;

            if (!Files.exists(path)) {
                Path mirroredPath = gaviotaDir.resolve(mirroredKey + ".gtb.cp4");
                if (!Files.exists(mirroredPath)) {
                    throw new IOException(
                            "No gaviota table file for " + naturalKey + " (" + naturalPath + ") "
                                    + "or its mirror " + mirroredKey + " (" + mirroredPath + ")");
                }
                egKey = mirroredKey;
                path = mirroredPath;
                reversed = true;
            }

            MappedByteBuffer header;
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
                header = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            }

            GaviotaZipInfo zipInfo = GaviotaBlockIndex.loadIndexes(header);

            return new GaviotaTable(egKey, header, zipInfo, reversed);
        } catch (IOException e) {
            throw new MissingTableException(
                    "Failed to load gaviota table for material " + naturalKey + ": " + e.getMessage());
        }
    }

    private static String pieceLetters(int[] types) {
        StringBuilder sb = new StringBuilder(types.length);
        for (int t : types) sb.append(GaviotaRequest.pieceSymbol(t));
        return sb.toString();
    }

    private static int[] flipNsAll(int[] squares) {
        int[] r = new int[squares.length];
        for (int i = 0; i < squares.length; i++) r[i] = GaviotaConstants.flipNs(squares[i]);
        return r;
    }

    private static int opp(int side) {
        return side == 0 ? 1 : 0;
    }

    // ============================================================
    // Block fetch + cache — ported from _tb_probe()
    // ============================================================

    /**
     * Resolves req's material key, computes its probe index, fetches (or
     * reuses from cache) the block containing that index, and returns the
     * single distance code for this exact position — still in packed
     * "prefix | (plies&lt;&lt;3)" form (pass through GaviotaBlockDecoder.unpackDist()).
     */
    private int tbProbe(GaviotaRequest req) {
        GaviotaTable table = setupTablebase(req);

        GaviotaEndgameKey key = GaviotaMaterialRegistry.EGKEY.get(req.egKey);
        if (key == null) {
            throw new MissingTableException("unsupported gaviota material: " + req.egKey);
        }

        long idx = key.pctoi().apply(req);
        long[] split = GaviotaBlockIndex.splitIndex(idx);
        long blockOffset = split[0];
        int remainder = (int) split[1];

        String cacheKey = cacheKey(req.egKey, blockOffset, req.side);
        long age = blockAge.incrementAndGet();

        TableBlock t = blockCache.compute(cacheKey, (ck, existing) -> {
            if (existing != null) {
                existing.age = age;
                return existing;
            }
            return loadBlock(req, table, key, idx, blockOffset, age);
        });

        if (blockCache.size() > MAX_CACHED_BLOCKS) {
            evictLeastRecentlyUsed();
        }

        return t.pcache[remainder];
    }

    private TableBlock loadBlock(GaviotaRequest req, GaviotaTable table, GaviotaEndgameKey key,
                                 long idx, long blockOffset, long age) {
        int block = GaviotaBlockIndex.getBlockNumber(key, req.side, idx);
        int n = GaviotaBlockIndex.getBlockSize(key, idx);

        long zippedSize = GaviotaBlockIndex.getSizeZipped(table.zipInfo(), block);
        long fileOffset = GaviotaBlockIndex.park(table.zipInfo(), block);

        byte[] zippedBuffer = new byte[(int) zippedSize];
        MappedByteBuffer dup = table.header().duplicate();
        dup.position((int) fileOffset);
        dup.get(zippedBuffer);

        TableBlock t = new TableBlock(req.egKey, req.side, blockOffset, age);
        t.pcache = GaviotaBlockDecoder.decodeBlock(zippedBuffer, req.side, n);
        return t;
    }

    private void evictLeastRecentlyUsed() {
        String lruKey = null;
        long oldestAge = Long.MAX_VALUE;
        for (Map.Entry<String, TableBlock> e : blockCache.entrySet()) {
            if (e.getValue().age < oldestAge) {
                oldestAge = e.getValue().age;
                lruKey = e.getKey();
            }
        }
        if (lruKey != null) {
            blockCache.remove(lruKey);
        }
    }

    // ============================================================
    // DTM probing (no en passant) — ported from _probe_dtm_no_ep()
    // ============================================================

    /**
     * Ported from gaviota.py's {@code _probe_dtm_no_ep}. Does not itself
     * handle en passant (see class doc) — the caller is responsible for
     * that loop, matching python's {@code probe_dtm}.
     *
     * @param whiteSquares squares occupied by white pieces (any order)
     * @param whiteTypes   piece types at those squares, python-chess convention
     *                     (PAWN=1, KNIGHT=2, BISHOP=3, ROOK=4, QUEEN=5, KING=6) —
     *                     see {@link GaviotaRequest}
     * @param blackSquares squares occupied by black pieces (any order)
     * @param blackTypes   piece types at those squares
     * @param side         0 = white to move, 1 = black to move
     * @return signed DTM in half-moves: positive if the side to move is
     *         winning, negative if losing, 0 if drawn
     */
    public int probeDtmNoEp(int[] whiteSquares, int[] whiteTypes,
                            int[] blackSquares, int[] blackTypes, int side) {
        GaviotaRequest req = new GaviotaRequest(whiteSquares, whiteTypes, blackSquares, blackTypes, side);

        int dtm = tbProbe(req);
        int[] unpacked = GaviotaBlockDecoder.unpackDist(dtm);
        int ply = unpacked[0];
        int res = unpacked[1];

        if (res == I_WMATE) {
            // White mates in the stored position.
            if (req.realSide == 1) {
                return req.isReversed ? ply : -ply;
            } else {
                return req.isReversed ? -ply : ply;
            }
        } else if (res == I_BMATE) {
            // Black mates in the stored position.
            if (req.realSide == 0) {
                return req.isReversed ? ply : -ply;
            } else {
                return req.isReversed ? -ply : ply;
            }
        } else {
            // Draw (or forbidden, which shouldn't occur for a legal position).
            return 0;
        }
    }

    // ============================================================
    // Chessboard adapter — ported from probe_dtm(), including the
    // en-passant resolution loop.
    // ============================================================

    /**
     * Probes DTM for the given board position. Ported 1:1 from gaviota.py's
     * {@code probe_dtm}: computes the no-en-passant DTM first, then — for
     * every legal en passant capture available in this exact position —
     * plays it, recursively probes the resulting position, and folds the
     * result in (mirroring python's {@code min}/{@code max} merge, which
     * picks whichever candidate is better for the side to move).
     * <p>
     * Mutates {@code board} via {@link MoveGenerator#makeMove}/
     * {@link MoveGenerator#unmakeMove} while probing en passant children,
     * but always restores it to its original state before returning
     * (including on exception, via try/finally — same as python's
     * {@code board.push(move)} / {@code finally: board.pop()}).
     *
     * @throws IllegalArgumentException if the position has castling rights
     *         or more than 5 pieces
     * @throws MissingTableException if no table file covers this material
     *         (for the original position, or for a position reached via
     *         one of its en passant children)
     */
    public int probeDtm(Chessboard board) {
        if (board.castle != 0) {
            throw new IllegalArgumentException(
                    "gaviota tables do not contain positions with castling rights");
        }

        long occupied = 0L;
        int pieceCount = 0;
        for (long bb : board.bitboards) {
            occupied |= bb;
            pieceCount += Long.bitCount(bb);
        }

        if (pieceCount > 5) {
            throw new IllegalArgumentException(
                    "gaviota tables support up to 5 pieces, not " + pieceCount);
        }

        long kingsOnly = board.bitboards[EncodedPieces.K]
                | board.bitboards[EncodedPieces.k];
        if (occupied == kingsOnly) {
            return 0; // KvK is always a draw
        }

        int dtm = probeDtmNoEpFromBoard(board);

        int[] moveList = new int[MoveCache.MAX_MOVE_SIZE];
        int moveCount = MoveGenerator.generateMoves(board, moveList);

        for (int i = 0; i < moveCount; i++) {
            int move = moveList[i];
            if (!EncodeMove.getMoveEnpassant(move)) {
                continue;
            }

            MoveGenerator.makeMove(board, move);
            try {
                int childDtm;
                if (ChessboardUtils.isCheckmate(board)) {
                    childDtm = 1;
                } else {
                    childDtm = -probeDtmNoEpFromBoard(board);
                    if (childDtm > 0) {
                        childDtm += 1;
                    } else if (childDtm < 0) {
                        childDtm -= 1;
                    }
                }
                // same sign (both winning or both losing for the mover): take
                // the closer one (min); different sign: take the better one (max)
                dtm = (dtm * childDtm > 0) ? Math.min(dtm, childDtm) : Math.max(dtm, childDtm);
            } finally {
                MoveGenerator.unmakeMove(board, move);
            }
        }

        return dtm;
    }

    /** Ported from gaviota.py's {@code _probe_dtm_no_ep}, taking its inputs off {@code board} directly. */
    private int probeDtmNoEpFromBoard(Chessboard board) {
        int[][] white = extractSide(board, true);
        int[][] black = extractSide(board, false);
        return probeDtmNoEp(white[0], white[1], black[0], black[1], board.side);
    }

    // ============================================================
    // WDL — ported from probe_wdl(). Gaviota tables store DRAW==0 for both
    // genuine draws AND a mated position, so a dtm==0 result alone can't
    // tell them apart — checkmate is checked separately via ChessboardUtils.
    // ============================================================

    /**
     * Ported from gaviota.py's {@code probe_wdl}.
     *
     * @return 1 if the side to move is winning, 0 if drawn, -1 if losing
     */
    public int probeWdl(Chessboard board) {
        int dtm = probeDtm(board);
        if (dtm == 0) {
            return ChessboardUtils.isCheckmate(board) ? -1 : 0;
        }
        return dtm > 0 ? 1 : -1;
    }

    // ============================================================
    // get_dtm / get_wdl — default-returning wrappers, ported from
    // gaviota.py's get_dtm()/get_wdl() (which catch KeyError; python raises
    // plain KeyError for castling-rights/>5-piece rejection too, not just
    // missing tables, so both exception types are caught here).
    // ============================================================

    /**
     * Ported from gaviota.py's get_dtm(): like {@link #probeDtm}, but returns
     * {@code defaultValue} instead of throwing when no table is available or
     * the position is otherwise unprobeable (castling rights, &gt;5 pieces).
     */
    public int getDtm(Chessboard board, int defaultValue) {
        try {
            return probeDtm(board);
        } catch (MissingTableException | IllegalArgumentException e) {
            return defaultValue;
        }
    }

    /** Ported from gaviota.py's get_wdl(). See {@link #getDtm} for the exception-handling caveat. */
    public int getWdl(Chessboard board, int defaultValue) {
        try {
            return probeWdl(board);
        } catch (MissingTableException | IllegalArgumentException e) {
            return defaultValue;
        }
    }

    /**
     * Pulls (square, gaviota-piece-type) pairs for one color off the board's
     * bitboards, using the same P/N/B/R/Q/K (white) and p/n/b/r/q/k (black)
     * index convention SyzygyFillSquares already relies on.
     *
     * @return {squares, types} — unsorted; GaviotaRequest sorts them itself
     */
    private static int[][] extractSide(Chessboard board, boolean isWhite) {
        int[] pieceIndices = isWhite
                ? new int[]{
                EncodedPieces.P, EncodedPieces.N,
                EncodedPieces.B, EncodedPieces.R,
                EncodedPieces.Q, EncodedPieces.K}
                : new int[]{
                EncodedPieces.p, EncodedPieces.n,
                EncodedPieces.b, EncodedPieces.r,
                EncodedPieces.q, EncodedPieces.k};
        int[] gaviotaTypes = {
                GaviotaRequest.PAWN, GaviotaRequest.KNIGHT, GaviotaRequest.BISHOP,
                GaviotaRequest.ROOK, GaviotaRequest.QUEEN, GaviotaRequest.KING
        };

        int total = 0;
        for (int code : pieceIndices) total += Long.bitCount(board.bitboards[code]);

        int[] squares = new int[total];
        int[] types = new int[total];
        int idx = 0;
        for (int i = 0; i < pieceIndices.length; i++) {
            long bb = board.bitboards[pieceIndices[i]];
            while (bb != 0L) {
                int sq = BitBoardUtils.getLS1BIndex(bb);
                bb = BitBoardUtils.popBit(bb, sq);
                squares[idx] = sq;
                types[idx] = gaviotaTypes[i];
                idx++;
            }
        }
        return new int[][]{squares, types};
    }
}
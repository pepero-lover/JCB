package com.pepero.jcb.api.gaviota;

import com.pepero.jcb.api.exception.tablebase.TablebaseMissingFileException;
import com.pepero.jcb.api.exception.tablebase.TablebaseUnsupportedMaterialException;
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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns material-key resolution, file/mmap management, the block-fetch + LRU
 * cache pipeline, and the {@link Chessboard}-facing probe entry points
 * ({@link #probeDtm}/{@link #probeWdl}).
 * <p>
 * The material-key resolution ({@link #setupTablebase}) and block-fetch
 * pipeline ({@link #tbProbe}) correspond to {@code gtb-probe.c}'s
 * {@code tb_probe_()}/{@code preload_cache()} (Gaviota Tablebases probing
 * code, Copyright (c) 2010 Miguel A. Ballicora, X11/MIT,
 * https://github.com/michiguel/Gaviota-Tablebases) — restructured around
 * JCB's own lazy per-material {@code ConcurrentHashMap} cache rather than
 * C's static global tables, the same way
 * {@link com.pepero.jcb.api.syzygy.SyzygyTablebase}'s
 * {@code wdlCache}/{@code dtzCache} restructure Fathom's C globals.
 * <p>
 * {@link #probeDtm}'s en passant handling is conceptually the same idea as
 * {@code tb_probe_()}'s {@code epsq} handling (try the available en passant
 * capture, recursively probe the resulting position, and keep whichever
 * outcome the side to move prefers via a decisive-result-first merge — the
 * C's {@code bestx()} table-driven merge on packed {@code dtm_t} codes,
 * verified equivalent to this method's simpler same-sign-min/different-sign-max
 * merge on plain signed ply counts) — but the implementation is JCB's own:
 * it uses JCB's real {@link MoveGenerator} to enumerate legal en passant
 * moves and JCB's own make/unmake on a {@link Chessboard}, rather than C's
 * manual square-array simulation (which exists because bare index-based
 * probing code has no move generator of its own to call).
 * <p>
 * There is no explicit {@code close()} — mapped byte buffers are simply
 * left for the GC (no unmap in plain {@code java.nio}).
 */
public final class GaviotaTablebase {

    private static final int MAX_CACHED_BLOCKS = 128;

    // result codes, same scale as GaviotaBlockDecoder's I_DRAW/I_WMATE/I_BMATE/I_FORBID
    public static final int I_DRAW = GaviotaBlockDecoder.I_DRAW;
    public static final int I_WMATE = GaviotaBlockDecoder.I_WMATE;
    public static final int I_BMATE = GaviotaBlockDecoder.I_BMATE;
    public static final int I_FORBID = GaviotaBlockDecoder.I_FORBID;

    private final Path gaviotaDir;

    // one entry per distinct egKey ever probed (e.g. "KPK", "KQKR"), keyed by
    // the *natural* (white-material + black-material) key — same convention
    // as Syzygy's wdlCache/dtzCache being keyed by the board's natural material string.
    private final Map<String, GaviotaTable> tableCache = new ConcurrentHashMap<>();

    private final Map<String, TableBlock> blockCache = new ConcurrentHashMap<>();
    private final AtomicLong blockAge = new AtomicLong();

    /**
     * Validate the given gaviota directory when initializing {@link GaviotaTablebase}
     */
    private void validateTablebaseDir(Path dir) {
        if (!Files.isDirectory(dir)) {
            throw new TablebaseMissingFileException("Tablebase directory does not exist: " + dir);
        }
        if (!Files.isReadable(dir)) {
            throw new TablebaseMissingFileException("Tablebase directory is not readable: " + dir);
        }

        boolean hasAnyTablebaseFile = false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.gtb.cp4")) {
            if (stream.iterator().hasNext()) {
                hasAnyTablebaseFile = true;
            }
        } catch (IOException e) {
            throw new TablebaseMissingFileException("Failed to scan tablebase directory: " + dir, e);
        }

        if (!hasAnyTablebaseFile) {
            throw new TablebaseMissingFileException(
                    "No .rtbw/.rtbz files found in tablebase directory: " + dir);
        }
    }

    /**
     * @throws TablebaseMissingFileException if given gaviota directory doesn't exist or can't read
     */
    public GaviotaTablebase(Path gaviotaDir) {
        validateTablebaseDir(gaviotaDir);
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
    // Material-key resolution + file loading — corresponds to gtb-probe.c's
    // egtb_get_id()/list_sq_flipNS() straight-vs-reversed branch inside
    // tb_probe_(), restructured around a single lazy per-material cache the
    // way Syzygy's loadWdlTable()/loadDtzTable() are.
    // ============================================================

    /**
     * Resolves req.egKey (trying white-then-black material name, then the
     * reversed black-then-white name), sets req.whitePieceSquares/Types and
     * req.blackPieceSquares/Types (flipping+swapping colors if the reversed
     * table had to be used), and returns the resolved, mapped table.
     *
     * @throws TablebaseMissingFileException if neither ordering has a table file available
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
            throw new TablebaseMissingFileException(
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
    // Block fetch + cache — corresponds to gtb-probe.c's preload_cache()/
    // dtm_cache_pointblock(), restructured around a ConcurrentHashMap +
    // atomic age counter instead of C's fixed-size array + linear LRU scan.
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
            throw new TablebaseMissingFileException("Unsupported gaviota material: " + req.egKey);
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
    // DTM probing (no en passant) — corresponds to the non-ep body of
    // gtb-probe.c's tb_probe_() (egtb_get_id() + egtb_get_dtm()), taking
    // JCB-native square/type arrays instead of C's null-terminated SQUARE*/
    // SQ_CONTENT* lists.
    // ============================================================

    /**
     * Does not itself handle en passant (see class doc) — the caller is
     * responsible for that (see {@link #probeDtm}), matching the way
     * {@code tb_probe_()}'s own en passant branch wraps its core
     * {@code egtb_get_id}/{@code egtb_get_dtm} logic.
     *
     * @param whiteSquares squares occupied by white pieces (any order)
     * @param whiteTypes   piece types at those squares — see {@link GaviotaRequest}
     *                     for the PAWN=1..KING=6 numbering (asserted by
     *                     gtb-probe.c itself, not a convention this port invented)
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
     * Probes DTM for the given board position: computes the no-en-passant
     * DTM first, then — for every legal en passant capture available in
     * this exact position — plays it, recursively probes the resulting
     * position, and folds the result in via a decisive-result-first merge
     * (see class doc's note on {@code bestx()} equivalence).
     * <p>
     * Mutates {@code board} via {@link MoveGenerator#makeMove}/
     * {@link MoveGenerator#unmakeMove} while probing en passant children,
     * but always restores it to its original state before returning
     * (including on exception, via try/finally).
     *
     * @throws TablebaseUnsupportedMaterialException if the position has castling rights
     *         or more than 5 pieces
     * @throws TablebaseMissingFileException if no table file covers this material
     *         (for the original position, or for a position reached via
     *         one of its en passant children)
     */
    public int probeDtm(Chessboard board) {
        if (board.castle != 0) {
            throw new TablebaseUnsupportedMaterialException(
                    "Gaviota tables do not contain positions with castling rights");
        }

        long occupied = 0L;
        int pieceCount = 0;
        for (long bb : board.bitboards) {
            occupied |= bb;
            pieceCount += Long.bitCount(bb);
        }

        if (pieceCount > 5) {
            throw new TablebaseUnsupportedMaterialException(
                    "Gaviota tables support up to 5 pieces, not " + pieceCount);
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

    /**
     * Pulls this exact board position's square/type lists and probes
     * {@link #probeDtmNoEp} directly off {@code board} — the no-en-passant
     * core that {@link #probeDtm} wraps with its en passant loop.
     */
    private int probeDtmNoEpFromBoard(Chessboard board) {
        int[][] white = extractSide(board, true);
        int[][] black = extractSide(board, false);
        return probeDtmNoEp(white[0], white[1], black[0], black[1], board.side);
    }

    // ============================================================
    // WDL — derived from DTM (gtb-probe.c has no separate WDL-only probe
    // path for the DTM tables; WDL is just DTM's sign). Gaviota tables store
    // DRAW==0 for both genuine draws AND a mated position, so a dtm==0
    // result alone can't tell them apart — checkmate is checked separately
    // via ChessboardUtils.
    // ============================================================

    /**
     * @return 1 if the side to move is winning, 0 if drawn, -1 if losing
     */
    public int probeWdl(Chessboard board) {
        int dtm = probeDtm(board);
        if (dtm == 0) {
            return ChessboardUtils.isCheckmate(board) ? -1 : 0;
        }
        return dtm > 0 ? 1 : -1;
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
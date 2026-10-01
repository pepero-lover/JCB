package com.pepero.jcb.api.syzygy;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.SyzygyAnalyzer;
import com.pepero.jcb.api.exception.tablebase.TablebaseMissingFileException;
import com.pepero.jcb.api.exception.tablebase.TablebaseUnsupportedMaterialException;
import com.pepero.jcb.core.bitboard.BitBoardUtils;
import com.pepero.jcb.core.constant.MoveCache;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.ChessboardUtils;
import com.pepero.jcb.core.GameVariant;
import com.pepero.jcb.core.MoveGenerator;
import com.pepero.jcb.core.encode.EncodeMove;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static com.pepero.jcb.api.syzygy.SyzygyMaterial.keyFromPieces;
import static com.pepero.jcb.core.constant.SideToMove.*;
import static com.pepero.jcb.core.constant.BoardSquares.*;
import static com.pepero.jcb.core.constant.EncodedPieces.*;

/**
 * Probe and get the DTZ (Distance to zero) / WDL (Win Draw Loss) data.
 * This uses {@link Chessboard} to probe the data, if you want to probe the data with {@link ChessGame},
 * go to {@link SyzygyAnalyzer}.
 * <p>
 * The probing logic here is derived from Fathom, Ronald de Man and Jon
 * Dart's C reference implementation for probing Syzygy tablebases
 * (MIT license, https://github.com/jdart1/Fathom).
 */
public class SyzygyTablebase {

    private final Path syzygyDir;

    // one entry per distinct material string ever probed (e.g. "KBNvK", "KQvKR")
    private final Map<String, WdlTable> wdlCache = new ConcurrentHashMap<>();
    private final Map<String, DtzTable> dtzCache = new ConcurrentHashMap<>();

    // pass as maxPieces to detect the limit from the files in the directory.
    private static final int AUTO_DETECT = -1;

    private static final Pattern MATERIAL_NAME = Pattern.compile("[KQRBNP]+v[KQRBNP]+");

    // extra (non-king) piece letters in the order used inside a side's name, strongest first
    private static final String EXTRA_PIECES = "QRBNP";

    private final int maxPieces;

    private final GameVariant variant;
    private final boolean connectedKingsEnc;

    private static final String[] SYZYGY_EXTENSIONS = {
            "*.rtbw", "*.rtbz", // standard
            "*.atbw", "*.atbz", // atomic
            "*.gtbw", "*.gtbz", // giveaway
            "*.stbw", "*.stbz", // suicide
    };

    /**
     * Validate the given syzygy directory when initializing {@link SyzygyTablebase}
     */
    private void validateTablebaseDir(Path dir) {
        if (!Files.isDirectory(dir)) {
            throw new TablebaseMissingFileException("Tablebase directory does not exist: " + dir);
        }
        if (!Files.isReadable(dir)) {
            throw new TablebaseMissingFileException("Tablebase directory is not readable: " + dir);
        }

        boolean hasAnyTablebaseFile = false;
        for (String pattern : SYZYGY_EXTENSIONS) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, pattern)) {
                if (stream.iterator().hasNext()) {
                    hasAnyTablebaseFile = true;
                    break;
                }
            } catch (IOException e) {
                throw new TablebaseMissingFileException("Failed to scan tablebase directory: " + dir, e);
            }
        }

        if (!hasAnyTablebaseFile) {
            throw new TablebaseMissingFileException(
                    "No syzygy files found in tablebase directory: " + dir);
        }
    }

    /**
     * Open the tablebase in the given directory. The maximum piece count is detected from the
     * files in the directory, see {@link #getMaxPieces()}.
     * @throws TablebaseMissingFileException if the directory is invalid, or (when detecting)
     *         the directory has no usable WDL+DTZ files for the variant
     */
    public SyzygyTablebase(Path syzygyDir) {
        this(syzygyDir, AUTO_DETECT, GameVariant.STANDARD);
    }

    /**
     * Open the tablebase in the given directory.
     * @param maxPieces maximum piece count to probe.
     *                  If it is {@code -1}, it is detected from the files in the directory.
     * @throws TablebaseMissingFileException if the directory is invalid, or (when detecting)
     *         the directory has no usable WDL+DTZ files for the variant
     */
    public SyzygyTablebase(Path syzygyDir, int maxPieces) {
        this(syzygyDir, maxPieces, GameVariant.STANDARD);
    }

    /**
     * Open the tablebase in the given directory.
     * @param variant game variant (supported : <b>standard, atomic, giveaway, suicide</b>)
     * @throws TablebaseMissingFileException if the directory is invalid, or (when detecting)
     *         the directory has no usable WDL+DTZ files for the variant
     */
    public SyzygyTablebase(Path syzygyDir, GameVariant variant) {
        this(syzygyDir, AUTO_DETECT, variant);
    }

    /**
     * @param maxPieces maximum piece count to probe.
     *                  If it is {@code -1}, it is detected from the files in the directory.
     * @throws TablebaseMissingFileException if the directory is invalid, or (when detecting)
     *         the directory has no usable WDL+DTZ files for the variant
     */
    public SyzygyTablebase(Path syzygyDir, int maxPieces, GameVariant variant) {
        validateTablebaseDir(syzygyDir);
        this.syzygyDir = syzygyDir;
        this.variant = variant;
        this.connectedKingsEnc = (
                variant == GameVariant.ATOMIC ||
                        variant == GameVariant.GIVEAWAY ||
                        variant == GameVariant.SUICIDE
        );
        this.maxPieces = (maxPieces == AUTO_DETECT) ? detectMaxPieces(syzygyDir, variant) : maxPieces;
    }

    /**
     * Get the maximum piece count (kings included) this tablebase will try to probe.
     * <p>
     * This is the value given to the constructor or, if none was given, the largest piece count
     * for which both WDL and DTZ files exist in the directory.
     */
    public int getMaxPieces() {
        return maxPieces;
    }

    // ---- max piece count detection ----

    private static String[] wdlGlobs(GameVariant v) {
        return switch (v) {
            case ATOMIC -> new String[]{"*.atbw"};
            case SUICIDE -> new String[]{"*.stbw"};
            case GIVEAWAY -> new String[]{"*.gtbw", "*.stbw"};
            default -> new String[]{"*.rtbw"};
        };
    }

    private static String[] dtzGlobs(GameVariant v) {
        return switch (v) {
            case ATOMIC -> new String[]{"*.atbz"};
            case SUICIDE -> new String[]{"*.stbz"};
            case GIVEAWAY -> new String[]{"*.gtbz", "*.stbz"};
            default -> new String[]{"*.rtbz"};
        };
    }

    /**
     * Largest piece count among files named like KQvKR., or 0 if there is none.
     */
    private static int scanMaxPieces(Path dir, String[] globs) {
        int max = 0;
        for (String glob : globs) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, glob)) {
                for (Path p : stream) {
                    String file = p.getFileName().toString();
                    String name = file.substring(0, file.lastIndexOf('.'));
                    if (MATERIAL_NAME.matcher(name).matches()) {
                        max = Math.max(max, name.length() - 1); // minus the 'v'
                    }
                }
            } catch (IOException e) {
                throw new TablebaseMissingFileException("Failed to scan tablebase directory: " + dir, e);
            }
        }
        return max;
    }

    private static int detectMaxPieces(Path dir, GameVariant variant) {
        int wdl = scanMaxPieces(dir, wdlGlobs(variant));
        int dtz = scanMaxPieces(dir, dtzGlobs(variant));
        // getWdlData() calls probeDtz() for every non-drawn position, so both kinds are required
        int max = Math.min(wdl, dtz);
        if (max == 0) {
            throw new TablebaseMissingFileException(
                    "No usable WDL+DTZ syzygy files for variant " + variant + " in " + dir
                            + " (WDL max pieces=" + wdl + ", DTZ max pieces=" + dtz + ")");
        }
        return max;
    }

    // ---- required / missing tables (STANDARD, ATOMIC, GIVEAWAY, SUICIDE) ----

    // piece letters in the order used inside a side's name, strongest first (king included)
    private static final String ALL_PIECES = "KQRBNP";

    private static boolean isAntichessLike(GameVariant v) {
        return v == GameVariant.GIVEAWAY || v == GameVariant.SUICIDE;
    }

    /**
     * Smallest piece count (kings included) that has a table file.
     * STANDARD / ATOMIC: 3 (KvK needs no table). GIVEAWAY / SUICIDE: 2 (e.g. "KvK" or "BvN").
     */
    private static int minPieces(GameVariant v) {
        return isAntichessLike(v) ? 2 : 3;
    }

    /**
     * Get every material name (e.g. "KQvKR") needed to probe any position of the given variant
     * with {@link #minPieces} to {@code upToPieces} pieces, since probing recurses into captures
     * and promotions. Names follow the Syzygy file naming (mirrored materials appear once).
     */
    public static SortedSet<String> requiredMaterials(int upToPieces, GameVariant variant) {
        SortedSet<String> out = newMaterialSet();
        for (int n = minPieces(variant); n <= upToPieces; n++) out.addAll(expectedMaterials(n, variant));
        return out;
    }

    /** Same as {@link #requiredMaterials(int, GameVariant)} for {@link GameVariant#STANDARD}. */
    public static SortedSet<String> requiredMaterials(int upToPieces) {
        return requiredMaterials(upToPieces, GameVariant.STANDARD);
    }

    /**
     * All materials with exactly {@code pieces} pieces (kings included), mirrors counted once.
     */
    private static List<String> expectedMaterials(int pieces, GameVariant v) {
        List<String> out = new ArrayList<>();
        if (!isAntichessLike(v)) {
            // STANDARD / ATOMIC: exactly one king per side
            int extras = pieces - 2;
            for (int a = extras; a * 2 >= extras; a--) { // white extras >= black extras
                int b = extras - a;
                for (String w : extraCombos(a, EXTRA_PIECES)) {
                    for (String bl : extraCombos(b, EXTRA_PIECES)) {
                        if (a == b && compareByRank(w, bl) > 0) continue; // drop the mirror duplicate
                        out.add("K" + w + "vK" + bl);
                    }
                }
            }
        } else {
            // GIVEAWAY / SUICIDE: the king is an ordinary piece (0, 1, 2... of them), each side has >= 1 piece
            for (int a = pieces - 1; a >= 1 && a * 2 >= pieces; a--) {
                int b = pieces - a;
                for (String w : extraCombos(a, ALL_PIECES)) {
                    for (String bl : extraCombos(b, ALL_PIECES)) {
                        if (a == b && compareByRank(w, bl) > 0) continue;
                        out.add(w + "v" + bl);
                    }
                }
            }
        }
        return out;
    }

    private static List<String> extraCombos(int k, String alphabet) {
        List<String> out = new ArrayList<>();
        buildCombos("", k, 0, alphabet, out);
        return out;
    }

    private static void buildCombos(String prefix, int left, int from, String alphabet, List<String> out) {
        if (left == 0) {
            out.add(prefix);
            return;
        }
        for (int i = from; i < alphabet.length(); i++) {
            buildCombos(prefix + alphabet.charAt(i), left - 1, i, alphabet, out);
        }
    }

    /**
     * Every material reachable from white-side / black-side strings (king included, e.g. "KRP", "KB")
     * by captures (by either side) and promotions, restricted to {@link #minPieces} or more pieces.
     * An upper bound: some listed materials may not be reachable from the actual position.
     */
    private static SortedSet<String> reachableMaterials(String white, String black, GameVariant v) {
        SortedSet<String> out = newMaterialSet();
        Set<String> seen = new HashSet<>();
        Deque<String[]> stack = new ArrayDeque<>();
        stack.push(new String[]{white, black});

        while (!stack.isEmpty()) {
            String[] s = stack.pop();
            if (s[0].isEmpty() || s[1].isEmpty()) continue; // one side wiped out: game over, no table
            if (!seen.add(s[0] + "v" + s[1])) continue;
            if (s[0].length() + s[1].length() >= minPieces(v)) out.add(canonicalName(s[0], s[1]));
            for (String[] next : successors(s, v)) stack.push(next);
        }
        return out;
    }

    /**
     * Materials one move can lead to, as {white, black} side strings (each sorted by rank).
     */
    private static List<String[]> successors(String[] s, GameVariant v) {
        List<String[]> out = new ArrayList<>();
        for (int side = 0; side < 2; side++) { // side = the side that moves
            String mine = s[side];
            String theirs = s[side ^ 1];

            // promotion (a capturing promotion is a promotion followed by a capture in this DFS)
            if (mine.indexOf('P') >= 0) {
                String promos = isAntichessLike(v) ? "KQRBN" : "QRBN";
                for (char promo : promos.toCharArray()) {
                    out.add(ordered(side, sortByRank(mine.replaceFirst("P", String.valueOf(promo))), theirs));
                }
            }

            switch (v) {
                case ATOMIC -> {
                    // capture = the capturer and the captured piece vanish, plus every non-pawn,
                    // non-king piece next to the captured square (both colors). Geometry is ignored,
                    // so any subset of those pieces is allowed. A king can neither capture nor be
                    // blown up on a continuing line (that ends the game), so kings are never removed.
                    for (int c : distinctIdx(mine, false)) {
                        String mineAfter = removeAt(mine, c);
                        for (int t : distinctIdx(theirs, false)) {
                            String theirsAfter = removeAt(theirs, t);
                            for (String m : explosionResults(mineAfter)) {
                                for (String th : explosionResults(theirsAfter)) {
                                    out.add(ordered(side, m, th));
                                }
                            }
                        }
                    }
                }
                case GIVEAWAY, SUICIDE -> {
                    // the king is an ordinary piece and can be captured
                    for (int t : distinctIdx(theirs, true)) {
                        out.add(ordered(side, mine, removeAt(theirs, t)));
                    }
                }
                default -> {
                    // STANDARD: the opponent loses one non-king piece
                    for (int t : distinctIdx(theirs, false)) {
                        out.add(ordered(side, mine, removeAt(theirs, t)));
                    }
                }
            }
        }
        return out;
    }

    // rebuild {white, black} from (moving side, its string, the other side's string)
    private static String[] ordered(int side, String mine, String theirs) {
        String[] next = new String[2];
        next[side] = mine;
        next[side ^ 1] = theirs;
        return next;
    }

    // indices of one representative per distinct letter (side strings are sorted, so equal letters are adjacent)
    private static List<Integer> distinctIdx(String x, boolean includeKing) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < x.length(); i++) {
            if (!includeKing && x.charAt(i) == 'K') continue;
            if (i > 0 && x.charAt(i) == x.charAt(i - 1)) continue;
            out.add(i);
        }
        return out;
    }

    private static String removeAt(String x, int i) {
        return x.substring(0, i) + x.substring(i + 1);
    }

    // x with any subset of its non-pawn, non-king pieces removed (the empty subset included)
    private static List<String> explosionResults(String x) {
        List<Integer> removable = new ArrayList<>();
        for (int i = 0; i < x.length(); i++) {
            if (x.charAt(i) != 'K' && x.charAt(i) != 'P') removable.add(i);
        }
        List<String> out = new ArrayList<>();
        for (int mask = 0; mask < (1 << removable.size()); mask++) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < x.length(); i++) {
                int bit = removable.indexOf(i);
                if (bit < 0 || (mask & (1 << bit)) == 0) sb.append(x.charAt(i));
            }
            out.add(sb.toString());
        }
        return out;
    }

    // compares piece strings by piece value order (K Q R B N P), not by ASCII order
    private static int compareByRank(String a, String b) {
        for (int i = 0; i < Math.min(a.length(), b.length()); i++) {
            int d = ALL_PIECES.indexOf(a.charAt(i)) - ALL_PIECES.indexOf(b.charAt(i));
            if (d != 0) return d;
        }
        return a.length() - b.length();
    }

    // file naming: the side with more pieces first; if equal, the stronger pieces first.
    // w and b are full side strings (king included), e.g. "KRP", "KB".
    private static String canonicalName(String w, String b) {
        boolean swap = b.length() > w.length()
                || (b.length() == w.length() && compareByRank(w, b) > 0);
        return swap ? b + "v" + w : w + "v" + b;
    }

    private static String sortByRank(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : ALL_PIECES.toCharArray()) {
            for (int i = 0; i < s.length(); i++) {
                if (s.charAt(i) == c) sb.append(c);
            }
        }
        return sb.toString();
    }

    // sorted by piece count, then by name
    private static SortedSet<String> newMaterialSet() {
        return new TreeSet<>(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()));
    }

    /**
     * Get every material name needed to probe this position: its own material plus every material
     * reachable by captures (by either side) and promotions, with {@link #minPieces} or more pieces.
     * This is an upper bound: a capture that is impossible in this position may still be listed.
     */
    public static SortedSet<String> requiredMaterials(Chessboard chessboard) {
        return reachableMaterials(countedPieces(chessboard, true), countedPieces(chessboard, false),
                chessboard.gameVariant);
    }

    /**
     * Get the files (WDL and DTZ) missing from the directory for a complete set of
     * tablebases from the variant's minimum piece count (3, or 2 for giveaway / suicide)
     * to {@code upToPieces} pieces. File extensions follow this tablebase's variant.
     */
    public List<String> findMissingTables(int upToPieces) {
        List<String> missing = new ArrayList<>();
        for (int n = minPieces(variant); n <= upToPieces; n++) missing.addAll(missingForPieces(n));
        return missing;
    }

    /**
     * Get whether the directory has a complete WDL+DTZ set up to {@code upToPieces} pieces.
     */
    public boolean isComplete(int upToPieces) {
        return findMissingTables(upToPieces).isEmpty();
    }

    /**
     * Get the largest N (up to {@code limit}) for which the directory has a complete set of
     * tables up to N pieces. Returns {@code minPieces - 1} (2 for standard / atomic, 1 for
     * giveaway / suicide) if even the smallest set is not complete.
     */
    public int getMaxCompletePieces(int limit) {
        int n = minPieces(variant) - 1;
        while (n < limit && missingForPieces(n + 1).isEmpty()) n++;
        return n;
    }

    /**
     * Get the files missing from the directory to probe this position: WDL files for every
     * required material, and the DTZ file for the position's own material only (probing a DTZ
     * recurses only through quiet moves, which keep the material, and captures/pawn moves
     * only need WDL).
     * <p>
     * The result is an upper bound (see {@link #requiredMaterials(Chessboard)}): in giveaway / suicide,
     * for example, a position with a legal capture never consults its own table.
     */
    public List<String> findMissingTables(Chessboard board) {
        String white = countedPieces(board, true);
        String black = countedPieces(board, false);

        SortedSet<String> required = reachableMaterials(white, black, variant);
        List<String> missing = new ArrayList<>();
        for (String name : required) {
            String ext = wdlExtFor(name);
            if (!tableFileExists(name, ext)) missing.add(name + ext);
        }
        String root = canonicalName(white, black);
        if (required.contains(root)) {
            String ext = dtzExtFor(root);
            if (!tableFileExists(root, ext)) missing.add(root + ext);
        }
        return missing;
    }

    private List<String> missingForPieces(int pieces) {
        List<String> missing = new ArrayList<>();
        for (String name : expectedMaterials(pieces, variant)) {
            String wdlExt = wdlExtFor(name);
            String dtzExt = dtzExtFor(name);
            if (!tableFileExists(name, wdlExt)) missing.add(name + wdlExt);
            if (!tableFileExists(name, dtzExt)) missing.add(name + dtzExt);
        }
        return missing;
    }

    // either the natural or the mirrored name is enough (same rule as loadWdlTable/loadDtzTable)
    private boolean tableFileExists(String materialName, String ext) {
        return Files.exists(syzygyDir.resolve(materialName + ext))
                || Files.exists(syzygyDir.resolve(mirrorMaterialString(materialName) + ext));
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
            SyzygyMappedFile header,
            SyzygySubTable[] subTables,
            SyzygyPairsHeader[][] pairsHeaders,
            SyzygyBlockLayout layout,
            SyzygyEncType encType,
            int sides,
            boolean colorFlipped,
            boolean symmetric
    ) {}

    private record DtzTable(
            SyzygyMaterial material,
            SyzygyMappedFile header,
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
                MoveGenerator.makeMove(board, move);
                int childWdl;
                try {
                    childWdl = probeWdl(board);
                } finally {
                    MoveGenerator.unmakeMove(board, move);
                }
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
            throw new TablebaseUnsupportedMaterialException(
                    "This Syzygy tablebase's supporting piece count is less than this board's piece count! " +
                            "(supporting : " + maxPieces +
                            ", chess board : " + boardPiece + ")"
            );

        String materialName = buildMaterialString(board);
        WdlTable table = wdlCache.computeIfAbsent(materialName, this::loadWdlTable);

        SyzygyMaterial material = table.material();

        boolean actualWtm = (board.side == white);
        boolean symmetric = table.symmetric();
        boolean mirrorFlip = table.colorFlipped();

        boolean isWtm;
        boolean effectiveColorFlip;
        if (!symmetric) {
            isWtm = mirrorFlip != actualWtm;
            effectiveColorFlip = mirrorFlip;
        } else {
            isWtm = true;
            effectiveColorFlip = !actualWtm;
        }

        FileClassResult fc = determineFileClass(board, material, effectiveColorFlip);
        int t = fc.fileClass();

        int side = isWtm ? 0 : 1;
        SyzygyPairsHeader ph = table.pairsHeaders()[t][side];

        if (ph.isConstant()) {
            return ph.constValue();
        }

        SyzygyEncInfo encInfo = SyzygyEncInfo.build(table.subTables()[t], isWtm, material, t, table.encType());
        int[] p = SyzygyFillSquares.fillSquares(board, table.subTables()[t], isWtm, effectiveColorFlip, material.isHasPawns(), fc.anchorSquare());
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

        if (!ChessboardUtils.hasLegalMoves(board)) {
            return 0;
        }

        int wdlResult = probeWdl(board);
        if (wdlResult == 2) return 0;

        // Capture-compulsory shortcut, derived directly from the game rule +
        // standard Syzygy DTZ encoding (not from any probing-code source):
        // in Antichess/Giveaway, a legal capture makes every other move
        // illegal, so if THIS position has one, EVERY legal move here is a
        // capture -- i.e. a zeroing move. A zeroing move immediately resets
        // the 50-move counter, so DTZ can only be the standard Syzygy
        // "one zeroing move away" encoding: +-1 for an unconditional
        // win/loss, or +-101 for a cursed win / blessed loss (the +-100
        // 50-move-rule offset applied to a zeroing move, per Syzygy's own
        // WdlToDtz convention). No table probe or recursive search is
        // needed (or correct) here.
        if (variant == GameVariant.SUICIDE || variant == GameVariant.GIVEAWAY) {
            int[] rootMoveArray = new int[MoveCache.MAX_MOVE_SIZE];
            int rootMoveCount = MoveGenerator.generateMoves(board, rootMoveArray);
            boolean hasCaptureAtRoot = false;
            for (int i = 0; i < rootMoveCount; i++) {
                if (EncodeMove.getMoveCapture(rootMoveArray[i])) {
                    hasCaptureAtRoot = true;
                    break;
                }
            }
            if (hasCaptureAtRoot) {
                int s = wdlResult - 2;
                int sign = Integer.signum(s);
                int magnitude = (Math.abs(s) == 2) ? 1 : 101;
                return sign * magnitude;
            }
        }

        if (wdlResult > 2) {
            int requiredChildWdl = 4 - wdlResult;
            int[] moveArray = new int[MoveCache.MAX_MOVE_SIZE];
            int moveCount = MoveGenerator.generateMoves(board, moveArray);

            boolean mandatoryCaptureVariant = (variant == GameVariant.SUICIDE || variant == GameVariant.GIVEAWAY);
            boolean hasCaptureHere = false;
            if (mandatoryCaptureVariant) {
                for (int i = 0; i < moveCount; i++) {
                    if (EncodeMove.getMoveCapture(moveArray[i])) {
                        hasCaptureHere = true;
                        break;
                    }
                }
            }

            for (int i = 0; i < moveCount; i++) {
                int move = moveArray[i];
                boolean isCapture = EncodeMove.getMoveCapture(move);
                if (hasCaptureHere && !isCapture) {
                    continue; // illegal quiet move under the forced-capture rule
                }

                boolean zeroing = isCapture
                        || EncodeMove.getMovePiece(move) == P
                        || EncodeMove.getMovePiece(move) == p;

                MoveGenerator.makeMove(board, move);
                try {
                    boolean opponentStuck = (variant == GameVariant.SUICIDE || variant == GameVariant.GIVEAWAY)
                            && !ChessboardUtils.hasLegalMoves(board);

                    if (zeroing || opponentStuck) {
                        int childWdl = probeWdl(board);
                        if (childWdl == requiredChildWdl) {
                            return (wdlResult == 3) ? 101 : 1;
                        }
                    }
                    else if (variant == GameVariant.SUICIDE || variant == GameVariant.GIVEAWAY) {
                        int[] responseMoves = new int[MoveCache.MAX_MOVE_SIZE];
                        int responseCount = MoveGenerator.generateMoves(board, responseMoves);

                        boolean opponentHasCapture = false;
                        boolean allResponsesLoseForOpponent = true;

                        for (int j = 0; j < responseCount; j++) {
                            int rMove = responseMoves[j];
                            if (EncodeMove.getMoveCapture(rMove)) {
                                opponentHasCapture = true;

                                MoveGenerator.makeMove(board, rMove);
                                int gcWdl;
                                try {
                                    gcWdl = probeWdl(board);
                                } finally {
                                    MoveGenerator.unmakeMove(board, rMove);
                                }
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
                } finally {
                    MoveGenerator.unmakeMove(board, move);
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
            raw = new int[]{ph.constValue(), 0};
        } else {
            SyzygyEncInfo encInfo = SyzygyEncInfo.build(table.subTables()[t], isWtm, material, t, table.encType());
            int[] p = SyzygyFillSquares.fillSquares(board, table.subTables()[t], isWtm, fillColorFlip, material.isHasPawns(), fc.anchorSquare());
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
            return -1;
        }

        // Antichess/Giveaway: captures are mandatory. If ANY capture is legal here,
        // every non-capturing move is actually illegal and must be excluded —
        // otherwise the "losing side delays as long as possible" logic below can
        // pick a phantom quiet-move line that was never legal to begin with.
        boolean mandatoryCaptureVariant = (variant == GameVariant.SUICIDE || variant == GameVariant.GIVEAWAY);
        boolean hasCaptureHere = false;
        if (mandatoryCaptureVariant) {
            for (int i = 0; i < moveCount; i++) {
                if (EncodeMove.getMoveCapture(moveArray[i])) {
                    hasCaptureHere = true;
                    break;
                }
            }
        }

        int requiredChildWdl = 4 - wdlResult;
        boolean weAreWinning = wdlResult > 2;

        Integer best = null;

        for (int i = 0; i < moveCount; i++) {
            int move = moveArray[i];
            boolean isCapture = EncodeMove.getMoveCapture(move);

            if (hasCaptureHere && !isCapture) {
                continue; // illegal quiet move under the forced-capture rule
            }

            boolean zeroing = isCapture
                    || EncodeMove.getMovePiece(move) == P
                    || EncodeMove.getMovePiece(move) == p;

            MoveGenerator.makeMove(board, move);
            try {
                int childWdl = probeWdl(board);
                boolean consistent = weAreWinning
                        ? childWdl <= requiredChildWdl
                        : childWdl >= requiredChildWdl;
                if (!consistent) {
                    continue;
                }

                int childDistance = zeroing ? 0 : Math.abs(probeDtz(board));
                int candidate = 1 + childDistance;

                // wdlResult 1 (BlessedLoss) and 3 (CursedWin) both carry the ±100
                // 50-move-rule offset, matching Fathom's WdlToDtz[wdl±1] = ±101.
                if (zeroing && (wdlResult == 1 || wdlResult == 3)) {
                    candidate += 100;
                }

                if (best == null
                        || (weAreWinning && candidate < best)
                        || (!weAreWinning && candidate > best)) {
                    best = candidate;
                }
            } finally {
                MoveGenerator.unmakeMove(board, move);
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

            boolean symmetric = naturalMaterialName.equals(mirrorMaterialString(naturalMaterialName));

            if (!Files.exists(path)) {
                String mirrored = mirrorMaterialString(naturalMaterialName);
                // pawn count/presence is unchanged by mirroring (just swaps which
                // side owns which pieces), so the same extension applies to both.
                Path mirroredPath = syzygyDir.resolve(mirrored + wdlExt);
                if (!Files.exists(mirroredPath)) {
                    throw new TablebaseMissingFileException(
                            "No WDL tablebase file for " + naturalMaterialName + " (" + path + ") "
                                    + "or its mirror " + mirrored + " (" + mirroredPath + ")");
                }
                materialName = mirrored;
                path = mirroredPath;
                colorFlipped = true;
            }

            SyzygyFile file = SyzygyFile.open(path);
            SyzygyMaterial material = SyzygyMaterial.parse(materialName, connectedKingsEnc);
            SyzygyMappedFile header = SyzygyFile.mapFile(path);

            SyzygySubTable[] subTables = material.parseSubTables(header);

            boolean probeColorFlipped = colorFlipped;
            if (!material.isHasPawns()) {
                String fileKey = keyFromPieces(subTables[0].wtmPieces());
                probeColorFlipped = !naturalMaterialName.equals(fileKey);
            }

            int pairsStartOffset = material.computePairsHeaderStartOffset();
            boolean capturesCompulsory = (variant == GameVariant.SUICIDE || variant == GameVariant.GIVEAWAY);
            SyzygyPairsHeadersResult pairsResult =
                    material.parsePairsHeaders(header, pairsStartOffset, file.isSplit(), file.getType(), capturesCompulsory);
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

            return new WdlTable(material, header, subTables, pairsHeaders, layout, encType, sides, probeColorFlipped, symmetric);
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
                    throw new TablebaseMissingFileException(
                            "No DTZ tablebase file for " + naturalMaterialName + " (" + path + ") "
                                    + "or its mirror " + mirrored + " (" + mirroredPath + ")");
                }
                materialName = mirrored;
                path = mirroredPath;
                colorFlipped = true;
            }

            SyzygyFile file = SyzygyFile.open(path); // split is always false for DTZ
            SyzygyMaterial material = SyzygyMaterial.parse(materialName, connectedKingsEnc);
            SyzygyMappedFile header = SyzygyFile.mapFile(path);

            SyzygySubTable[] subTables = material.parseSubTables(header);

            int pairsStartOffset = material.computePairsHeaderStartOffset();
            boolean capturesCompulsory = (variant == GameVariant.SUICIDE || variant == GameVariant.GIVEAWAY);
            SyzygyPairsHeadersResult pairsResult =
                    material.parsePairsHeaders(header, pairsStartOffset, file.isSplit(), file.getType(), capturesCompulsory);
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
     *
     * @throws TablebaseUnsupportedMaterialException if the position has castling rights
     *         or this position's piece count is more than this class's supporting piece count
     * @throws TablebaseMissingFileException if no table file covers this material
     *         (for the original position, or for a position reached by playing
     *         a capture or promotion from it during probing)
     */
    public int getWdlData(Chessboard board) throws IOException {
        try {
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
        } catch (TablebaseMissingFileException e) {
            throw withMissingFileList(board, e);
        }
    }

    /**
     * Get DTZ data based on this chess board
     *
     * @param board chess board
     * @return DTZ data
     *
     * @throws TablebaseUnsupportedMaterialException if the position has castling rights
     *         or this position's piece count is more than this class's supporting piece count
     * @throws TablebaseMissingFileException if no table file covers this material
     *         (for the original position, or for a position reached by playing
     *         a capture or promotion from it during probing)
     */
    public int getDtzData(Chessboard board) throws IOException {
        try {
            return probeDtz(board);
        } catch (TablebaseMissingFileException e) {
            throw withMissingFileList(board, e);
        }
    }

    // how many missing file names are spelled out in an exception message
    private static final int MAX_LISTED_MISSING = 20;

    /**
     * Rebuild a {@link TablebaseMissingFileException} with showing required piece set(s) which are missing
     */
    private TablebaseMissingFileException withMissingFileList(Chessboard board, TablebaseMissingFileException cause) {
        List<String> missing;
        try {
            missing = findMissingTables(board);
        } catch (RuntimeException ignored) {
            return cause;
        }
        if (missing.isEmpty()) {
            return cause;
        }

        StringBuilder sb = new StringBuilder(String.valueOf(cause.getMessage()));
        sb.append("\nFiles needed to probe this position but missing from ").append(syzygyDir)
                .append(" (").append(missing.size()).append("): ");
        int shown = Math.min(missing.size(), MAX_LISTED_MISSING);
        sb.append(String.join(", ", missing.subList(0, shown)));
        if (missing.size() > shown) {
            sb.append(", ... and ").append(missing.size() - shown).append(" more");
        }
        return new TablebaseMissingFileException(sb.toString(), cause);
    }
}
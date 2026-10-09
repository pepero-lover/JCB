package com.pepero.jcb.api.uci;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.exception.engine.UCIEngineException;
import com.pepero.jcb.api.parse.ConvertStringMoveUtils;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.FENDialect;
import com.pepero.jcb.core.GameVariant;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * UCI engine wrapper for analyzing, best move finding.
 */
public class UCIEngineWrapper implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(UCIEngineWrapper.class.getName());

    private Process engineProcess;
    private BufferedReader reader;
    private BufferedReader errorReader;
    private BufferedWriter writer;

    private Thread parsingThread;
    private Thread errorDrainThread;
    private Thread broadcastingThread;

    private volatile ChessGame analysisSnapshot;

    private volatile CountDownLatch uciokLatch;
    private volatile CountDownLatch readyokLatch;
    private volatile CountDownLatch stopLatch;

    private final Set<String> availableOptions = ConcurrentHashMap.newKeySet();
    private final HashMap<String, String> engineIdData = new HashMap<>();

    private final AtomicReference<CompletableFuture<AnalysisResult>> currentMoveFuture = new AtomicReference<>();

    private final ConcurrentHashMap<Integer, EngineLine> latestAnalysisMap = new ConcurrentHashMap<>();
    private volatile boolean isAnalyzing = false;

    private Thread shutdownHook;

    private volatile boolean isClosed = false;

    private volatile boolean isWhiteToMove = true;

    private static final long DEFAULT_SYNC_TIMEOUT_SEC = 120;
    private static final long HANDSHAKE_TIMEOUT_SEC = 15;
    private static final long STOP_TIMEOUT_SEC = 10;

    private final EngineAnalysisListener listener;
    private final int tickRateMs;

    private int currentCp = 0;

    /**
     * Build and initialize engine.
     *
     * @param engine engine process builder class
     * @param tickRateMs tick rate (for analyze delay)
     * @param listener listener
     *
     * @throws UCIEngineException when engine initialization failed like uciok timeout, readyok timeout.
     */
    public UCIEngineWrapper(ProcessBuilder engine, int tickRateMs, EngineAnalysisListener listener) {
        this.tickRateMs = tickRateMs;
        this.listener = listener;

        try {
            // keep stderr separate so debug logs from the engine
            // can't get interleaved with UCI protocol lines on stdout
            engine.redirectErrorStream(false);

            this.engineProcess = engine.start();
            this.reader = new BufferedReader(new InputStreamReader(engineProcess.getInputStream()));
            this.errorReader = new BufferedReader(new InputStreamReader(engineProcess.getErrorStream()));
            this.writer = new BufferedWriter(new OutputStreamWriter(engineProcess.getOutputStream()));

            this.uciokLatch = new CountDownLatch(1);
            this.readyokLatch = new CountDownLatch(1);

            // keep the hook reference so it can be removed again in close()
            this.shutdownHook = new Thread(() -> {
                if (engineProcess != null && engineProcess.isAlive()) {
                    engineProcess.destroy();
                }
            });
            Runtime.getRuntime().addShutdownHook(this.shutdownHook);

            // if the engine process dies mid-analysis, fail the
            // pending future instead of blocking startAnalysisSync() forever
            engineProcess.onExit().thenRun(this::handleProcessExit);

            startParsingThread();
            startErrorDrainThread();
            startBroadcastingThread();

            sendCommand("uci");
            if (!uciokLatch.await(HANDSHAKE_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                throw new UCIEngineException("uciok Timeout!");
            }

            sendCommand("isready");
            if (!readyokLatch.await(HANDSHAKE_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                throw new UCIEngineException("readyok Timeout!");
            }
        } catch (UCIEngineException e) {
            // handshake failed: don't leave the process, the threads and the shutdown hook behind
            close();
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            close();
            throw new UCIEngineException("Engine initialization failed.", e);
        }
    }

    /**
     * Tell the engine a new game is starting so it drops hash tables,
     * history heuristics, etc. from the previous game.
     * Must be awaited (isready/readyok) before the next "go".
     */
    public void newGame() {
        readyokLatch = new CountDownLatch(1);
        sendCommand("ucinewgame");
        sendCommand("isready");
        awaitReady();
    }

    /**
     * Set an UCI option and block until the engine confirms it processed
     * it, via isready/readyok Some engines apply options
     * asynchronously, so firing "go" right after "setoption" can race.
     */
    public void setOptionSync(String name, String value) {
        readyokLatch = new CountDownLatch(1);
        sendCommand("setoption name " + name + " value " + value);
        sendCommand("isready");
        awaitReady();
    }

    private void awaitReady() {
        try {
            if (!readyokLatch.await(HANDSHAKE_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                throw new UCIEngineException("readyok Timeout!");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UCIEngineException("Interrupted while waiting for readyok", e);
        }
    }

    private ChessGame takeSnapshot(ChessGame chessGame) {
        return ChessGame.lightWeightCopy(chessGame);
    }

    /**
     * Start engine analysis. (non-blocking)
     * depth <= 0 means "go infinite" - useful for a live,
     * ever-updating eval bar; call stopAnalysis() to end it.
     */
    public void startAnalysis(ChessGame chessGame, int depth, int multiPv) {
        startAnalysis(chessGame, SearchLimit.depth(depth).withMultiPv(multiPv));
    }

    /**
     * Start engine analysis with the given limits. (non-blocking)
     * {@link SearchLimit#infinite()} means "go infinite" - useful for a live,
     * ever-updating eval bar; call stopAnalysis() to end it.
     *
     * @param chessGame game to analyze
     * @param limits search limits (also holds the MultiPV count)
     */
    public void startAnalysis(ChessGame chessGame, SearchLimit limits) {
        Objects.requireNonNull(limits, "Search limits can not be null!");

        prepareSearch(chessGame, limits.multiPv(), null);
        sendCommand(limits.toGoCommand());
    }

    /**
     * Common part of every search: reset the analysis state, apply
     * MultiPV / Chess960 / variant options and send the position.
     *
     * @param future the future a sync search waits on (null for async search)
     */
    private void prepareSearch(ChessGame chessGame, int multiPv, CompletableFuture<AnalysisResult> future) {
        isWhiteToMove = chessGame.getTurn();
        if (future != null) {
            currentMoveFuture.set(future);
        }
        latestAnalysisMap.clear();
        isAnalyzing = true;
        stopLatch = new CountDownLatch(1);

        analysisSnapshot = takeSnapshot(chessGame);

        setOptionSync("MultiPV", String.valueOf(multiPv));

        if(chessGame.isChess960()) {
            if(!hasOption("UCI_Chess960")) {
                throw new UCIEngineException("Chess 960 option not found!");
            }
            setOptionSync("UCI_Chess960", "true");
        }

        supportVariant(chessGame.getGameVariant());

        sendCommand(buildPositionCommand(chessGame));
    }

    /**
     * Throw UCIEngineException if this chess engine doesn't support this variant
     *
     * @param variant variant
     */
    private void supportVariant(GameVariant variant) {
        if(variant != GameVariant.STANDARD) {
            if(!hasOption("UCI_Variant")) {
                throw new UCIEngineException("Variant option not found!");
            }

            switch (variant) {
                case CRAZY_HOUSE :
                    setOptionSync("UCI_Variant", "crazyhouse");
                    break;
                case THREE_CHECK:
                    setOptionSync("UCI_Variant", "3check");
                    break;
                case KING_OF_THE_HILL:
                    setOptionSync("UCI_Variant", "kingofthehill");
                    break;
                case HORDE:
                    setOptionSync("UCI_Variant", "horde");
                    break;
                case GIVEAWAY:
                    setOptionSync("UCI_Variant", "antichess");
                    break;
                case SUICIDE:
                    setOptionSync("UCI_Variant", "suicide");
                    break;
                case ATOMIC:
                    setOptionSync("UCI_Variant", "atomic");
                    break;
                case RACING_KINGS:
                    setOptionSync("UCI_Variant", "racingkings");
                    break;
            }
        }
    }

    /**
     * Stop engine analysis
     */
    public void stopAnalysis() {
        if (isAnalyzing) {
            isAnalyzing = false;
            sendCommand("stop");

            CountDownLatch latch = stopLatch;
            if (latch != null) {
                try {
                    if (!latch.await(STOP_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                        System.err.println("Warning: engine did not confirm stop within timeout");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private String buildPositionCommand(ChessGame chessGame) {
        StringBuilder positionCmd = new StringBuilder();

        String startFen = chessGame.getStartPositionFEN();
        String defaultStartFen = Chessboard.start_position;

        if (startFen.equals(defaultStartFen)) {
            positionCmd.append("position startpos");
        } else {
            if (chessGame.getGameVariant() == GameVariant.THREE_CHECK) {
                ChessGame tempForFen = ChessGame.fromFEN(startFen, chessGame.getGameVariant());
                startFen = tempForFen.getFEN(FENDialect.FAIRY_STOCKFISH);
            }
            positionCmd.append("position fen ").append(startFen);
        }

        List<MoveInfo> history = chessGame.getPathToCurrentNode();
        if (history != null && !history.isEmpty()) {
            positionCmd.append(" moves");
            for (MoveInfo move : history) {
                positionCmd.append(" ").append(move.toLanString());
            }
        }

        return positionCmd.toString();
    }

    /**
     * Start getting engine's info and pvs
     */
    private void startParsingThread() {
        parsingThread = new Thread(() -> {
            try {
                String line;

                while (!Thread.currentThread().isInterrupted() && (line = reader.readLine()) != null) {
                    // one bad line must never kill this thread, otherwise every pending
                    // latch / future would just wait until its timeout
                    try {
                        handleEngineLine(line);
                    } catch (RuntimeException e) {
                        LOGGER.log(Level.WARNING, "Failed to handle engine output line: " + line, e);
                    }
                }
            } catch (IOException e) {
                System.err.println("Stopped parsing stream");
            }
        }, "uci-parsing-thread");
        // daemon: a forgotten close() must not keep the JVM alive
        parsingThread.setDaemon(true);
        parsingThread.start();
    }

    /**
     * Handle a single line of engine's stdout.
     */
    private void handleEngineLine(String line) {
        notifyListener(l -> l.onEngineLog("IN", line));

        if (line.equals("uciok")) {
            uciokLatch.countDown();
        } else if (line.equals("readyok")) {
            readyokLatch.countDown();
        } else if (line.startsWith("option name ")) {
            parseOptionLine(line);
        } else if (line.startsWith("id")) {
            parseIdLine(line);
        } else if (line.startsWith("bestmove")) {
            handleBestMove(line);
        } else if (line.startsWith("info") && line.contains("score") && line.contains(" pv ")) {
            parseInfoLine(line);
        }
    }

    private void handleBestMove(String line) {
        List<EngineLine> finalBundle = latestAnalysisMap.isEmpty()
                ? List.of()
                : latestAnalysisMap.values().stream()
                .sorted(Comparator.comparingInt(EngineLine::pvNumber))
                .toList();
        if (!finalBundle.isEmpty()) {
            notifyListener(l -> l.onAnalysisBundled(finalBundle));
        }
        isAnalyzing = false;

        String[] parts = line.split(" ");
        String bestMove = parts.length > 1 ? parts[1] : "(none)";
        notifyListener(l -> l.onBestMoveFound(bestMove));

        CompletableFuture<AnalysisResult> future = currentMoveFuture.get();
        if (future != null && !future.isDone()) {
            future.complete(new AnalysisResult(bestMove, finalBundle));
        }

        CountDownLatch latch = stopLatch;
        if (latch != null) {
            latch.countDown();
        }
    }

    /**
     * Drain stderr so it never blocks the process and never gets mixed
     * into stdout protocol parsing
     */
    private void startErrorDrainThread() {
        errorDrainThread = new Thread(() -> {
            try {
                String line;
                while (!Thread.currentThread().isInterrupted() && (line = errorReader.readLine()) != null) {
                    final String errLine = line;
                    notifyListener(l -> l.onEngineLog("ERR", errLine));
                }
            } catch (IOException ignored) {
                // stream closed on shutdown
            }
        }, "uci-stderr-thread");
        errorDrainThread.setDaemon(true);
        errorDrainThread.start();
    }

    /**
     * Start engine analysis loop
     */
    private void startBroadcastingThread() {
        broadcastingThread = new Thread(() -> {
            List<EngineLine> lastSentBundle = null;

            try {
                while (!Thread.currentThread().isInterrupted()) {
                    if (isAnalyzing && !latestAnalysisMap.isEmpty() && listener != null) {
                        List<EngineLine> currentBundle = latestAnalysisMap.values().stream()
                                .sorted(Comparator.comparingInt(EngineLine::pvNumber))
                                .toList();

                        if (!currentBundle.equals(lastSentBundle)) {
                            notifyListener(l -> l.onAnalysisBundled(currentBundle));
                            lastSentBundle = currentBundle;
                        }
                    }

                    Thread.sleep(tickRateMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "uci-broadcast-thread");
        // daemon: a forgotten close() must not keep the JVM alive
        broadcastingThread.setDaemon(true);
        broadcastingThread.start();
    }

    /**
     * Get info line and put into the latestAnalysisMap
     */
    private void parseInfoLine(String infoLine) {
        try {
            // skip upperbound lowerbound
            boolean isBound = infoLine.contains(" upperbound") || infoLine.contains(" lowerbound");
            if (isBound) {
                return;
            }

            int pvNumber = 1;
            if (infoLine.contains(" multipv ")) {
                int mpvIndex = infoLine.indexOf(" multipv ") + 9;
                int nextSpace = infoLine.indexOf(" ", mpvIndex);
                if (nextSpace == -1) nextSpace = infoLine.length();
                pvNumber = Integer.parseInt(infoLine.substring(mpvIndex, nextSpace));
            }

            int depth = 0;
            if (infoLine.contains(" depth ")) {
                int depthIndex = infoLine.indexOf(" depth ") + 7;
                int nextSpace = infoLine.indexOf(" ", depthIndex);
                if (nextSpace == -1) nextSpace = infoLine.length();
                depth = Integer.parseInt(infoLine.substring(depthIndex, nextSpace));
            }

            boolean isCurrentCpMate = currentCp > MATE_IDENTIFY || currentCp < -MATE_IDENTIFY;

            EngineCp score = new EngineCp(
                    currentCp,
                    isCurrentCpMate
            );

            if (infoLine.contains("score cp ")) {
                int cpIndex = infoLine.indexOf("score cp ") + 9;
                int nextSpace = infoLine.indexOf(" ", cpIndex);
                if (nextSpace == -1) nextSpace = infoLine.length();
                int rawCp = Integer.parseInt(infoLine.substring(cpIndex, nextSpace));
                currentCp = isWhiteToMove ? rawCp : -rawCp;
                score = new EngineCp(isWhiteToMove ? rawCp : -rawCp, false);
            } else if (infoLine.contains("score mate ")) {
                int mateIndex = infoLine.indexOf("score mate ") + 11;
                int nextSpace = infoLine.indexOf(" ", mateIndex);
                if (nextSpace == -1) nextSpace = infoLine.length();
                int mateIn = Integer.parseInt(infoLine.substring(mateIndex, nextSpace));
                int rawCp = mateIn > 0 ? MATE_SCORE - mateIn : -MATE_SCORE - mateIn;
                currentCp = isWhiteToMove ? rawCp : -rawCp;
                score = new EngineCp(isWhiteToMove ? mateIn : -mateIn, true);
            }

            int pvIndex = infoLine.indexOf(" pv ") + 4;
            String pvStr = infoLine.substring(pvIndex).trim();

            String sanPvStr = pvStr;
            ChessGame snapshot = analysisSnapshot;
            if (snapshot != null) {
                try {
                    sanPvStr = snapshot.toSan(pvStr);
                } catch (Exception e) {
                    sanPvStr = pvStr;
                }
            }

            List<MoveInfo> pvMoveInfo = ConvertStringMoveUtils.parseLanSequenceToMoveData(
                    snapshot.getBoardSnapshot(),
                    pvStr
            );

            latestAnalysisMap.put(pvNumber, new EngineLine(depth, pvNumber, score, pvStr, sanPvStr, pvMoveInfo));

            // lambda needs effectively final values (depth / score get reassigned above)
            final int reportedDepth = depth;
            final EngineCp reportedScore = score;
            notifyListener(l -> l.onEngineInfo(reportedDepth, reportedScore, pvStr));
        } catch (Exception e) {
            // ignore format exception
        }
    }

    private void parseOptionLine(String line) {
        try {
            int nameStart = line.indexOf("name ") + 5;
            int typeIdx = line.indexOf(" type ", nameStart);
            if (typeIdx == -1) return;
            String optionName = line.substring(nameStart, typeIdx).trim();
            availableOptions.add(optionName);
        } catch (Exception e) {
        }
    }

    private void parseIdLine(String line) {
        try {
            if (line.startsWith("id author ")) {
                String authorName = line.substring(10).trim();
                engineIdData.put("author", authorName);
            }
            if (line.startsWith("id name ")) {
                String authorName = line.substring(8).trim();
                engineIdData.put("name", authorName);
            }
        } catch (Exception e) {
        }
    }

    /**
     * Get whether this option name exists
     *
     * @param name option name
     * @return whether this option name exists
     */
    public boolean hasOption(String name) {
        return availableOptions.contains(name);
    }

    /**
     * Get engine author
     *
     * @return engine author
     */
    public String getAuthor() {
        return engineIdData.get("author");
    }

    /**
     * Get engine name
     *
     * @return engine name
     */
    public String getEngineName() {
        return engineIdData.get("name");
    }

    /**
     * Get engine id data
     *
     * @return engine id data
     */
    public Map<String, String> getEngineIdData() {
        return Map.copyOf(engineIdData);
    }

    /**
     * Get available engine options
     *
     * @return available engine options
     */
    public Set<String> getAvailableOptions() {
        return Set.copyOf(availableOptions);
    }

    /**
     * Analyze with the given limits and wait for the engine's bestmove. <br>
     * Could throw exception when the bestmove finding time is more than {@link #DEFAULT_SYNC_TIMEOUT_SEC} (120 sec) <br>
     * To change max bestmove time sec, use {@link #analyzeSync(ChessGame, SearchLimit, long)}.
     *
     * @param chessGame game to analyze
     * @param limits search limits (also holds the MultiPV count), must have a stopping condition
     * @return the engine's bestmove together with all pv lines at that point
     *
     * @throws IllegalArgumentException if limits has no stopping condition (it would never return)
     * @throws UCIEngineException if the engine fails or does not answer in time
     */
    public AnalysisResult analyzeSync(ChessGame chessGame, SearchLimit limits) {
        return analyzeSync(chessGame, limits, DEFAULT_SYNC_TIMEOUT_SEC);
    }

    /**
     * Analyze with the given limits and wait for the engine's bestmove.
     *
     * @param chessGame game to analyze
     * @param limits search limits (also holds the MultiPV count), must have a stopping condition
     * @param timeoutSeconds best move synchronize timeout seconds
     * @return the engine's bestmove together with all pv lines at that point
     *
     * @throws IllegalArgumentException if limits has no stopping condition (it would never return)
     * @throws UCIEngineException if the engine fails or does not answer in time
     */
    public AnalysisResult analyzeSync(ChessGame chessGame, SearchLimit limits, long timeoutSeconds) {
        Objects.requireNonNull(limits, "Search limits can not be null!");
        if (limits.isInfinite()) {
            throw new IllegalArgumentException(
                    "Sync analysis needs a depth, movetime, nodes or clock limit! " +
                            "(use startAnalysis(game, SearchLimits.infinite()) for live analysis)");
        }

        CompletableFuture<AnalysisResult> future = new CompletableFuture<>();
        prepareSearch(chessGame, limits.multiPv(), future);
        sendCommand(limits.toGoCommand());

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new UCIEngineException("Engine did not return bestmove within " + timeoutSeconds + "s", e);
        } catch (Exception e) {
            throw new UCIEngineException("Sync failed while waiting engine's response", e);
        }
    }

    private void handleProcessExit() {
        isAnalyzing = false;

        // close() destroys the process on purpose, so it is not a crash:
        // still fail a waiting sync call quickly, but don't report onEngineCrashed
        boolean closedByUser = isClosed;
        CompletableFuture<AnalysisResult> future = currentMoveFuture.get();
        IllegalStateException cause = new IllegalStateException(closedByUser
                ? "Engine was closed"
                : "Engine process exited unexpectedly");
        if (future != null && !future.isDone()) {
            future.completeExceptionally(cause);
        }
        if (!closedByUser) {
            notifyListener(l -> l.onEngineCrashed(cause));
        }
    }

    /**
     * Send command to engine
     */
    public void sendCommand(String command) {
        notifyListener(l -> l.onEngineLog("OUT", command));

        try {
            writer.write(command + "\n");
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Call the listener safely. A listener is user code, so a {@link RuntimeException}
     * thrown by it is logged instead of killing the engine's reader / broadcast threads.
     */
    private void notifyListener(Consumer<EngineAnalysisListener> call) {
        EngineAnalysisListener target = listener;
        if (target == null) return;

        try {
            call.accept(target);
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "EngineAnalysisListener threw an exception", e);
        }
    }

    /**
     * Safe closing engine
     */
    public void close() {
        if (isClosed) return;
        isClosed = true;

        // writer is still null when the process failed to start
        if (writer != null) sendCommand("quit");

        if (broadcastingThread != null) broadcastingThread.interrupt();
        if (parsingThread != null) parsingThread.interrupt();
        if (errorDrainThread != null) errorDrainThread.interrupt();

        if (engineProcess != null) {
            engineProcess.destroy();
            try {
                if (!engineProcess.waitFor(2, TimeUnit.SECONDS)) {
                    engineProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                engineProcess.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }

        try {
            if (reader != null) reader.close();
            if (errorReader != null) errorReader.close();
            if (writer != null) writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {}
        }
    }

    private final int MATE_SCORE = 100000;
    private final int MATE_IDENTIFY = 95000;

    /**
     * Get current Analyze CP
     * <p>
     * if mate found, return +-100000 -+[mate in N]
     *
     * @return current cp
     */
    public int getCurrentCp() {
        return currentCp;
    }

    /**
     * Get current first pv engine line
     */
    public EngineLine getCurrentFirstEngineLine() {
        return latestAnalysisMap.get(1);
    }

    /**
     * Get current pv engine lines
     */
    public HashMap<Integer, EngineLine> getCurrentEngineLines() {
        return new HashMap<>(latestAnalysisMap);
    }
}
package com.pepero.jcb.api.uci;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.exception.UCIEngineException;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.FENDialect;
import com.pepero.jcb.core.GameVariants;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class UCIEngineWrapper implements AutoCloseable {
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

    private final AtomicReference<CompletableFuture<String>> currentMoveFuture = new AtomicReference<>();

    private final ConcurrentHashMap<Integer, EngineLine> latestAnalysisMap = new ConcurrentHashMap<>();
    private volatile boolean isAnalyzing = false;

    private Thread shutdownHook;

    private volatile boolean isWhiteToMove = true;

    private static final long DEFAULT_SYNC_TIMEOUT_SEC = 120;
    private static final long HANDSHAKE_TIMEOUT_SEC = 15;
    private static final long STOP_TIMEOUT_SEC = 10;

    private final EngineAnalysisListener listener;
    private final int tickRateMs;

    private int currentCp = 0;

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
                throw new RuntimeException("uciok Timeout!");
            }

            sendCommand("isready");
            if (!readyokLatch.await(HANDSHAKE_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                throw new RuntimeException("readyok Timeout!");
            }
        } catch (Exception e) {
            throw new RuntimeException("Engine initialization failed.", e);
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
                throw new RuntimeException("readyok Timeout!");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for readyok", e);
        }
    }

    private ChessGame takeSnapshot(ChessGame chessGame) {
        return ChessGame.lightWeightCopy(chessGame);
    }

    /**
     * Start engine analysis.
     * depth <= 0 means "go infinite" - useful for a live,
     * ever-updating eval bar; call stopAnalysis() to end it.
     */
    public void startAnalysis(ChessGame chessGame, int depth, int multiPv) {
        latestAnalysisMap.clear();
        isWhiteToMove = chessGame.isWhiteTurn();
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

        supportVariant(chessGame.getGameVariants());

        sendCommand(buildPositionCommand(chessGame));

        if (depth > 0) {
            sendCommand("go depth " + depth);
        } else {
            sendCommand("go infinite");
        }
    }

    /**
     * Throw UCIEngineException if this chess engine doesn't support this variant
     *
     * @param variants variant
     */
    private void supportVariant(GameVariants variants) {
        if(variants != GameVariants.STANDARD) {
            if(!hasOption("UCI_Variant")) {
                throw new UCIEngineException("Variant option not found!");
            }

            switch (variants) {
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
                case ANTICHESS:
                    setOptionSync("UCI_Variant", "antichess");
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
            if (chessGame.getGameVariants() == GameVariants.THREE_CHECK) {
                ChessGame tempForFen = ChessGame.fromFEN(startFen, chessGame.getGameVariants());
                startFen = tempForFen.getFEN(FENDialect.FAIRY_STOCKFISH);
            }
            positionCmd.append("position fen ").append(startFen);
        }

        List<MoveInfo> history = chessGame.getMoveHistory();
        if (history != null && !history.isEmpty()) {
            positionCmd.append(" moves");
            for (MoveInfo move : history) {
                positionCmd.append(" ").append(move.toLanString(chessGame.getGameVariants()));
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
                    if (listener != null) {
                        listener.onEngineLog("IN", line);
                    }

                    if (line.equals("uciok")) {
                        uciokLatch.countDown();
                    } else if (line.equals("readyok")) {
                        readyokLatch.countDown();
                    } else if (line.startsWith("option name ")) {
                        parseOptionLine(line);
                    } else if (line.startsWith("bestmove")) {
                        if (!latestAnalysisMap.isEmpty() && listener != null) {
                            List<EngineLine> finalBundle = latestAnalysisMap.values().stream()
                                    .sorted(Comparator.comparingInt(EngineLine::pvNumber))
                                    .toList();
                            listener.onAnalysisBundled(finalBundle);
                        }
                        isAnalyzing = false;
                        String bestMove = line.split(" ")[1];
                        if (listener != null) listener.onBestMoveFound(bestMove);

                        CompletableFuture<String> future = currentMoveFuture.get();
                        if (future != null && !future.isDone()) {
                            future.complete(bestMove);
                        }

                        CountDownLatch latch = stopLatch;
                        if (latch != null) {
                            latch.countDown();
                        }
                    } else if (line.startsWith("info") && line.contains("score") && line.contains(" pv ")) {
                        parseInfoLine(line);
                    }
                }
            } catch (IOException e) {
                System.err.println("Stopped parsing stream");
            }
        }, "uci-parsing-thread");
        parsingThread.start();
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
                    if (listener != null) {
                        listener.onEngineLog("ERR", line);
                    }
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
                            listener.onAnalysisBundled(currentBundle);
                            lastSentBundle = currentBundle;
                        }
                    }

                    Thread.sleep(tickRateMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "uci-broadcast-thread");
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

            latestAnalysisMap.put(pvNumber, new EngineLine(depth, pvNumber, score, pvStr, sanPvStr, false));

            if (listener != null) {
                listener.onEngineInfo(depth, score, pvStr);
            }
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

    public boolean hasOption(String name) {
        return availableOptions.contains(name);
    }

    public Set<String> getAvailableOptions() {
        return Set.copyOf(availableOptions);
    }

    /**
     * Same as before, but with a bounded wait instead of an
     * unconditional get() that can hang forever if the engine dies or
     * never returns a bestmove.
     */
    public String startAnalysisSync(ChessGame chessGame, int depthLimit,
                                    long wtimeMs, long btimeMs,
                                    long wincMs, long bincMs,
                                    int multiPv) {
        return startAnalysisSync(chessGame, depthLimit, wtimeMs, btimeMs, wincMs, bincMs, multiPv, DEFAULT_SYNC_TIMEOUT_SEC);
    }

    public String startAnalysisSync(ChessGame chessGame, int depthLimit,
                                    long wtimeMs, long btimeMs,
                                    long wincMs, long bincMs,
                                    int multiPv, long timeoutSeconds) {
        isWhiteToMove = chessGame.isWhiteTurn();
        CompletableFuture<String> future = new CompletableFuture<>();
        currentMoveFuture.set(future);
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

        supportVariant(chessGame.getGameVariants());

        sendCommand(buildPositionCommand(chessGame));

        StringBuilder goCmd = new StringBuilder("go");

        if (wtimeMs > 0 || btimeMs > 0) {
            goCmd.append(" wtime ").append(wtimeMs);
            goCmd.append(" btime ").append(btimeMs);
            if (wincMs > 0) goCmd.append(" winc ").append(wincMs);
            if (bincMs > 0) goCmd.append(" binc ").append(bincMs);
        } else if (depthLimit > 0) {
            goCmd.append(" depth ").append(depthLimit);
        }

        sendCommand(goCmd.toString());

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException("Engine did not return bestmove within " + timeoutSeconds + "s", e);
        } catch (Exception e) {
            throw new RuntimeException("Sync failed while waiting engine's response", e);
        }
    }

    private void handleProcessExit() {
        isAnalyzing = false;
        CompletableFuture<String> future = currentMoveFuture.get();
        IllegalStateException cause = new IllegalStateException("Engine process exited unexpectedly");
        if (future != null && !future.isDone()) {
            future.completeExceptionally(cause);
        }
        if (listener != null) {
            listener.onEngineCrashed(cause);
        }
    }

    /**
     * Send command to engine
     */
    public void sendCommand(String command) {
        if (listener != null) {
            listener.onEngineLog("OUT", command);
        }

        try {
            writer.write(command + "\n");
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private volatile boolean isClosed = false;

    /**
     * Safe closing engine
     */
    public void close() {
        if (isClosed) return;
        isClosed = true;

        sendCommand("quit");

        if (broadcastingThread != null) broadcastingThread.interrupt();
        if (parsingThread != null) parsingThread.interrupt();
        if (errorDrainThread != null) errorDrainThread.interrupt();

        try {
            if (reader != null) reader.close();
            if (errorReader != null) errorReader.close();
            if (writer != null) writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (engineProcess != null) {
            engineProcess.destroy();
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
     *
     * @return current first pv engine line
     */
    public EngineLine getCurrentFirstEngineLine() {
        return latestAnalysisMap.get(1);
    }
}
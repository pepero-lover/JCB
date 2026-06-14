package com.pepero.jcb.api.uci;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.core.Chessboard;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class UCIEngineWrapper {
    private Process engineProcess;
    private BufferedReader reader;
    private BufferedWriter writer;

    private Thread parsingThread;
    private Thread broadcastingThread;

    private CountDownLatch uciokLatch;
    private CountDownLatch readyokLatch;

    private CompletableFuture<String> currentMoveFuture;

    private final ConcurrentHashMap<Integer, EngineLine> latestAnalysisMap = new ConcurrentHashMap<>();
    private volatile boolean isAnalyzing = false;

    public record EngineLine(int depth, int pvNumber, String score, String pv) {}

    public interface EngineAnalysisListener {
        void onAnalysisBundled(List<EngineLine> bundledLines);
        void onBestMoveFound(String bestMove);
    }

    private final EngineAnalysisListener listener;
    private final int tickRateMs;

    public UCIEngineWrapper(String enginePath, int tickRateMs, EngineAnalysisListener listener) {
        this.tickRateMs = tickRateMs;
        this.listener = listener;

        try {
            ProcessBuilder pb = new ProcessBuilder(enginePath.split(" "));

            pb.redirectErrorStream(true); 
            
            this.engineProcess = pb.start();
            this.reader = new BufferedReader(new InputStreamReader(engineProcess.getInputStream()));
            this.writer = new BufferedWriter(new OutputStreamWriter(engineProcess.getOutputStream()));
            
            this.uciokLatch = new CountDownLatch(1);
            this.readyokLatch = new CountDownLatch(1);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (engineProcess != null && engineProcess.isAlive()) {
                    engineProcess.destroy();
                }
            }));

            startParsingThread();

            startBroadcastingThread();

            sendCommand("uci");
            if (!uciokLatch.await(5, TimeUnit.SECONDS)) throw new RuntimeException("uciok Timeout!");
            
            sendCommand("isready");
            if (!readyokLatch.await(5, TimeUnit.SECONDS)) throw new RuntimeException("readyok Timeout!");

            System.out.println("Engine started / synchronized!");

        } catch (Exception e) {
            throw new RuntimeException("Engine initialization failed: " + enginePath, e);
        }
    }

    /**
     * Start engine analysis
     */
    public void startAnalysis(ChessGame chessGame, int depth, int multiPv) {
        latestAnalysisMap.clear();
        isAnalyzing = true;

        sendCommand("setoption name MultiPV value " + multiPv);

        StringBuilder positionCmd = new StringBuilder();

        String startFen = chessGame.getStartPositionFEN();
        String defaultStartFen = Chessboard.start_position;

        if (startFen.equals(defaultStartFen)) {
            positionCmd.append("position startpos");
        } else {
            positionCmd.append("position fen ").append(startFen);
        }

        List<MoveInfo> history = chessGame.getMoveHistory();
        if (history != null && !history.isEmpty()) {
            positionCmd.append(" moves");
            for (MoveInfo move : history) {
                positionCmd.append(" ").append(move.toLanString(chessGame.getGameVariants()));
            }
        }

        sendCommand(positionCmd.toString());

        sendCommand("go depth " + depth);
    }

    /**
     * Stop engine analysis
     */
    public void stopAnalysis() {
        if (isAnalyzing) {
            sendCommand("stop");
            isAnalyzing = false;
        }
    }

    /**
     * Start getting engine's info and pvs
     */
    private void startParsingThread() {
        parsingThread = new Thread(() -> {
            try {
                String line;

                while (!Thread.currentThread().isInterrupted() && (line = reader.readLine()) != null) {
                    if (line.equals("uciok")) {
                        // when uciok

                        uciokLatch.countDown();
                    } else if (line.equals("readyok")) {
                        // when readyok

                        readyokLatch.countDown();
                    } else if (line.startsWith("bestmove")) {
                        // best move

                        // get latest info
                        if (!latestAnalysisMap.isEmpty() && listener != null) {
                            List<EngineLine> finalBundle = latestAnalysisMap.values().stream()
                                    .sorted(Comparator.comparingInt(EngineLine::pvNumber))
                                    .toList();
                            listener.onAnalysisBundled(finalBundle);
                        }

                        isAnalyzing = false;
                        String bestMove = line.split(" ")[1];
                        if (listener != null) listener.onBestMoveFound(bestMove);

                        if (currentMoveFuture != null && !currentMoveFuture.isDone()) {
                            currentMoveFuture.complete(bestMove);
                        }
                    } else if (line.startsWith("info") && line.contains("score") && line.contains(" pv ")) {
                        parseInfoLine(line);
                    }
                }
            } catch (IOException e) {
                System.err.println("Stopped parsing stream");
            }
        });
        parsingThread.start();
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
        });
        broadcastingThread.start();
    }

    /**
     * Get info line and put into the latestAnalysisMap
     */
    private void parseInfoLine(String infoLine) {
        try {
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

            String scoreStr = "N/A";
            if (infoLine.contains("score cp ")) {
                int cpIndex = infoLine.indexOf("score cp ") + 9;
                int nextSpace = infoLine.indexOf(" ", cpIndex);
                if (nextSpace == -1) nextSpace = infoLine.length();
                double eval = Integer.parseInt(infoLine.substring(cpIndex, nextSpace)) / 100.0;
                scoreStr = (eval > 0 ? "+" : "") + eval;
            } else if (infoLine.contains("score mate ")) {
                int mateIndex = infoLine.indexOf("score mate ") + 11;
                int nextSpace = infoLine.indexOf(" ", mateIndex);
                if (nextSpace == -1) nextSpace = infoLine.length();
                scoreStr = "M" + infoLine.substring(mateIndex, nextSpace);
            }

            int pvIndex = infoLine.indexOf(" pv ") + 4;
            String pvStr = infoLine.substring(pvIndex).trim();

            latestAnalysisMap.put(pvNumber, new EngineLine(depth, pvNumber, scoreStr, pvStr));

        } catch (Exception e) {
            // ignore format exception
        }
    }

    public String startAnalysisSync(ChessGame chessGame, int depthLimit,
                                    long wtimeMs, long btimeMs,
                                    long wincMs, long bincMs,
                                    int multiPv) {
        this.currentMoveFuture = new CompletableFuture<>();
        this.latestAnalysisMap.clear();
        this.isAnalyzing = true;

        sendCommand("setoption name MultiPV value " + multiPv);

        StringBuilder positionCmd = new StringBuilder();
        String startFen = chessGame.getStartPositionFEN();

        if (startFen.equals(Chessboard.start_position)) {
            positionCmd.append("position startpos");
        } else {
            positionCmd.append("position fen ").append(startFen);
        }

        List<MoveInfo> history = chessGame.getMoveHistory();
        if (history != null && !history.isEmpty()) {
            positionCmd.append(" moves");
            for (MoveInfo move : history) {
                positionCmd.append(" ").append(move.toLanString(chessGame.getGameVariants()));
            }
        }

        sendCommand(positionCmd.toString());

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
            return currentMoveFuture.get();
        } catch (Exception e) {
            throw new RuntimeException("Sync failed while waiting engine's response", e);
        }
    }

    /**
     * Send command to engine
     */
    public void sendCommand(String command) {
        try {
            writer.write(command + "\n");
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Safe closing engine
     */
    public void close() {
        sendCommand("quit");

        if (broadcastingThread != null) broadcastingThread.interrupt();
        if (parsingThread != null) parsingThread.interrupt();

        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (engineProcess != null) {
            engineProcess.destroy();
        }

        System.out.println("Engine closed!");
    }
}